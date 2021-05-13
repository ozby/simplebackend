package com.prettybyte.simplebackend

import arrow.core.Either
import com.expediagroup.graphql.generator.TopLevelObject
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.prettybyte.simplebackend.lib.*
import com.prettybyte.simplebackend.lib.ktorgraphql.GraphQLHelper
import com.prettybyte.simplebackend.lib.ktorgraphql.getGraphQLServer
import com.prettybyte.simplebackend.lib.ktorgraphql.schema.EventMutationService
import com.prettybyte.simplebackend.lib.statemachine.StateMachine
import io.grpc.Server
import io.grpc.ServerBuilder
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.KClass

object SingletonStuff {

    // TODO: can we make this immutable?

    internal lateinit var views: Any
    internal var readModelPositiveRules: Set<(UserIdentity, Model<out ModelProperties>, Any) -> PositiveAuthorization> = emptySet()
    internal var readModelNegativeRules: Set<(UserIdentity, Model<out ModelProperties>, Any) -> NegativeAuthorization> = emptySet()
    internal var eventPositiveRules: Set<(UserIdentity, IEvent, Any) -> PositiveAuthorization> = emptySet()
    internal var eventNegativeRules: Set<(UserIdentity, IEvent, Any) -> NegativeAuthorization> = emptySet()

    fun <V> getViews() = views as V
    fun <V> getReadModelPositiveRules() = readModelPositiveRules as Set<(UserIdentity, Model<out ModelProperties>, V) -> PositiveAuthorization>
    fun <V> getReadModelNegativeRules() = readModelNegativeRules as Set<(UserIdentity, Model<out ModelProperties>, V) -> NegativeAuthorization>
    fun <V, E> getEventPositiveRules() = eventPositiveRules as Set<(UserIdentity, E, V) -> PositiveAuthorization>
    fun <V, E> getEventNegativeRules() = eventNegativeRules as Set<(UserIdentity, E, V) -> NegativeAuthorization>

}

internal class SimpleBackendWrapped<E : IEvent, V>(
    eventParser: (name: String, modelId: String, params: String, userIdentityId: String) -> E,      // TODO: reflection?
    private val managedModels: Set<ManagedModel<*, E, *, V>>,
    port: Int,
    serModule: SerializersModule,   // TODO: ugly. Can reflection help?
    databaseConnection: DatabaseConnection,
    migrations: IMigrations<E>?,
    customGraphqlPackages: List<String>,
    customQueries: List<TopLevelObject>,
    authorizationReadPositiveRules: Set<(UserIdentity, Model<out ModelProperties>, V) -> PositiveAuthorization>,
    authorizationReadNegativeRules: Set<(UserIdentity, Model<out ModelProperties>, V) -> NegativeAuthorization>,
    authorizationEventPositiveRules: Set<(UserIdentity, E, V) -> PositiveAuthorization>,
    authorizationEventNegativeRules: Set<(UserIdentity, E, V) -> NegativeAuthorization>,
    views: V,
) {
    private var grpcServer: Server
    private var ktorServer: NettyApplicationEngine
    private val json: Json = Json { serializersModule = serModule }

    // private val grpcServer: Server
    private val eventStore: EventStore<E>
    private val eventService: EventService<E, V>

    init {
        SingletonStuff.views = views as Any
        SingletonStuff.readModelPositiveRules = authorizationReadPositiveRules as Set<(UserIdentity, Model<out ModelProperties>, Any) -> PositiveAuthorization>
        SingletonStuff.readModelNegativeRules = authorizationReadNegativeRules as Set<(UserIdentity, Model<out ModelProperties>, Any) -> NegativeAuthorization>
        SingletonStuff.eventPositiveRules = authorizationEventPositiveRules as Set<(UserIdentity, IEvent, Any) -> PositiveAuthorization>
        SingletonStuff.eventNegativeRules = authorizationEventNegativeRules as Set<(UserIdentity, IEvent, Any) -> NegativeAuthorization>

        // TODO 1.0: validate that the provided ModelProperties classes are named "xProperties" e.g. GameProperties.

        managedModels.forEach { it.stateMachine.setView(it.view) }

        eventStore = EventStore(databaseConnection, eventParser)
        eventService = EventService(eventStore, ::stateMachineProvider, migrations)
        grpcServer =
            ServerBuilder.forPort(port + 1)
                // .useTransportSecurity() TODO 1.0
                //  .intercept(AuthenticationInterceptor)
                // .intercept(UserIdentityInjector)
                // .addService(AuthenticationService(authorizer))
                .addService(eventService)
                .build()
        //


        val mapper = jacksonObjectMapper()
        val server =
            getGraphQLServer(   // TODO: add interceptor that blocks if an event is currently processing (?)
                mapper,
                customGraphqlPackages,
                customQueries,
                listOf(TopLevelObject(EventMutationService(eventService, eventParser, json)))
            )
        val handler = GraphQLHelper(server, mapper)

        ktorServer = embeddedServer(Netty, port) {
            routing {
                post("graphql") {
                    handler.handle(this.call)
                }

                get("playground") {
                    this.call.respondText(buildPlaygroundHtml("graphql", "subscriptions"), ContentType.Text.Html)
                }
            }
        }
    }

    private fun modelViewProvider(modelType: String): IModelView<*, V> {
        return managedModels.find { it.kClass.simpleName == modelType }?.view ?: throw RuntimeException("Can't find model view for type '$modelType'")
    }

    private fun stateMachineProvider(event: IEvent): StateMachine<*, E, *, V> {
        return managedModels.find { it.stateMachine.canHandle(event) }?.stateMachine
            ?: throw RuntimeException("Can't find state machine for event '${event.name}'")
    }

    fun start() {
        eventService.start()
        grpcServer.start()
        ktorServer.start(wait = true)
        // Runtime.getRuntime().addShutdownHook(Thread { this@simplebackend.SimpleBackend.stop() })
        //grpcServer.start()
        // println("Server started, listening on $port")
        //grpcServer.awaitTermination()
        // TODO: make snapshot of Views?
    }

    fun stop() {
        // TODO: notify subscribing clients, cancel streams
        //grpcServer.shutdown()
    }

    fun processEvent(
        event: E,
        eventParametersJson: String,
        userIdentity: UserIdentity,
        performActions: Boolean,
        preventModelUpdates: Boolean,
        storeEvent: Boolean
    ) {
        GlobalScope.launch {
            eventService.process(
                event = event,
                eventParametersJson = eventParametersJson,
                withGuards = true,
                userIdentity = userIdentity,
                performActions = performActions,
                preventModelUpdates = preventModelUpdates,
                storeEvent = storeEvent,
            )
        }
    }

    fun getEventsForModelId(id: String): List<E> {
        return eventStore.readAllEvents().filter { it.modelId == id }   // TODO 1.0: should be a separate SQL query
    }

    fun getTransitions(kClass: KClass<out ModelProperties>, id: String): Either<Problem, List<String>> {
        val stateMachine = managedModels.find { it.kClass == kClass }?.stateMachine ?: return Either.left(Problem.generalProblem())
        return stateMachine.getEventTransitions(id)
    }

    fun getStateMachine(kClass: KClass<out ModelProperties>): Either<Problem, StateMachine<out ModelProperties, E, out Enum<*>, V>> {
        val sm = managedModels.find { it.kClass == kClass }?.stateMachine ?: return Either.left(Problem.generalProblem())
        return Either.Right(sm)
    }
}

fun logAndMakeInternalException(e: Exception): Throwable {
    e.printStackTrace()
    return RuntimeException(e.message)  // TODO 1.0: don't pass e.message
}

data class DatabaseConnection(val url: String, val driver: String)

private fun buildPlaygroundHtml(graphQLEndpoint: String, subscriptionsEndpoint: String) =
    Application::class.java.classLoader.getResource("graphql-playground.html")?.readText()
        ?.replace("\${graphQLEndpoint}", graphQLEndpoint)
        ?.replace("\${subscriptionsEndpoint}", subscriptionsEndpoint)
        ?: throw IllegalStateException("graphql-playground.html cannot be found in the classpath")

class SimpleBackend<E : IEvent, V> {

/*    fun setup(
        eventParser: (name: String, modelId: String, params: String, userIdentityId: String) -> E,      // TODO: reflection?
        eventAuthorizer: IEventAuthorizer<E>,
        managedModels: Set<ManagedModel<*, E, *, V>>,
        port: Int,
        serModule: SerializersModule,   // TODO: ugly. Can reflection help?
        databaseConnection: DatabaseConnection,
        migrations: IMigrations<E>?,
        customGraphqlPackages: List<String>,
        customQueries: List<TopLevelObject>,
        authorizationReadPositiveRules: Set<(UserIdentity, Model<out ModelProperties>, V) -> PositiveAuthorization>,
        authorizationReadNegativeRules: Set<(UserIdentity, Model<out ModelProperties>, V) -> NegativeAuthorization>,
        authorizationEventPositiveRules: Set<(UserIdentity, IEvent, V) -> PositiveAuthorization>,
        authorizationEventNegativeRules: Set<(UserIdentity, IEvent, V) -> NegativeAuthorization>,
        views: V
    ) {
        sb = SimpleBackendWrapped(
            eventParser,
            eventAuthorizer,
            managedModels,
            port,
            serModule,
            databaseConnection,
            migrations,
            customGraphqlPackages,
            customQueries,
            authorizationReadPositiveRules,
            authorizationReadNegativeRules,
            authorizationEventPositiveRules,
            authorizationEventNegativeRules,
            views,
        )
    }

 */

    fun setup(init: SimpleBackend<E, V>.() -> Unit) {
        this.init()
        sb = SimpleBackendWrapped(
            eventParserValue,
            managedModelsValue,
            portValue!!,
            serModuleValue,
            dbConnectionValue,
            dbMigrationsValue,
            graphQlPackagesValue,
            graphQlQueriesValue,
            authorizationRulesBlock.readPositiveRules,
            authorizationRulesBlock.readNegativeRules,
            authorizationRulesBlock.eventPositiveRules,
            authorizationRulesBlock.eventNegativeRules,
            viewsValue as V
        )
    }

    private lateinit var serModuleValue: SerializersModule
    private lateinit var authorizationRulesBlock: AuthorizationRulesBlock<E, V>
    private lateinit var graphQlQueriesValue: List<TopLevelObject>
    private lateinit var graphQlPackagesValue: List<String>
    private lateinit var managedModelsValue: Set<ManagedModel<*, E, *, V>>
    private lateinit var eventParserValue: (name: String, modelId: String, params: String, userIdentityId: String) -> E
    private lateinit var viewsValue: Any
    private var portValue: Int? = null
    private lateinit var dbMigrationsValue: IMigrations<E>
    private lateinit var dbConnectionValue: DatabaseConnection
    private lateinit var sb: SimpleBackendWrapped<E, V>

    fun processEvent(
        event: E,
        eventParametersJson: String,
        userIdentity: UserIdentity,
        performActions: Boolean,
        preventModelUpdates: Boolean,
        storeEvent: Boolean
    ) {
        return sb.processEvent(
            event,
            eventParametersJson,
            userIdentity,
            performActions = performActions,
            preventModelUpdates = preventModelUpdates,
            storeEvent = storeEvent
        )
    }


    fun getEventsForModelId(id: String): List<E> = (sb as SimpleBackendWrapped<E, V>).getEventsForModelId(id)

    fun start() = sb.start()

    fun getTransitions(kClass: KClass<out ModelProperties>, id: String): Either<Problem, List<String>> = sb.getTransitions(kClass, id)

    /*
    fun <E : IEvent> getStateMachineDescription(kClass: KClass<out ModelProperties>): Either<Problem, StateMachineDescription> =
        when (val either = sb.getStateMachine(kClass)) {
            is Either.Left -> Left(either.a)
            is Either.Right -> Right(StateMachineDescription(either.b))
        }
     */


    fun databaseConnection(url: String, driver: String) {
        dbConnectionValue = DatabaseConnection(url, driver)
    }

    fun databaseMigrations(dbMigrations: IMigrations<E>) {
        dbMigrationsValue = dbMigrations
    }

    fun graphQlPort(port: Int) {
        portValue = port
    }

    fun views(views: Any) {
        viewsValue = views
    }

    fun eventParser(parser: (name: String, modelId: String, params: String, userIdentityId: String) -> E) {
        eventParserValue = parser
    }

    fun managedModels(init: ManagedModelsBlock<E, V>.() -> Unit) {
        val block = ManagedModelsBlock<E, V>()
        block.init()
        managedModelsValue = block.value
    }

    fun graphQlPackages(vararg packages: String) {
        graphQlPackagesValue = packages.asList()
    }

    fun graphQlQueries(init: GraphQlQueriesBlock.() -> Unit) {
        val block = GraphQlQueriesBlock()
        block.init()
        graphQlQueriesValue = block.value
    }

    fun authorizationRules(init: AuthorizationRulesBlock<E, V>.() -> Unit) {
        authorizationRulesBlock = AuthorizationRulesBlock<E, V>()
        authorizationRulesBlock.init()
    }

    fun serModule(ser: SerializersModule) {
        serModuleValue = ser
    }

    class ManagedModelsBlock<E : IEvent, V> {

        internal val value = mutableSetOf<ManagedModel<*, E, *, V>>()

        fun <T : ModelProperties, ModelStates : Enum<*>> model(
            clazz: KClass<T>,
            stateMachine: StateMachine<T, E, ModelStates, V>,
            view: IModelView<T, V>
        ) {
            value.add(ManagedModel(clazz, stateMachine, view))
        }

    }

    class GraphQlQueriesBlock {
        val value = mutableListOf<TopLevelObject>()

        fun add(tlo: TopLevelObject) {
            value.add(tlo)
        }
    }

    class AuthorizationRulesBlock<E, V> {

        internal val readPositiveRules = mutableSetOf<(UserIdentity, Model<out ModelProperties>, V) -> PositiveAuthorization>()
        internal val readNegativeRules = mutableSetOf<(UserIdentity, Model<out ModelProperties>, V) -> NegativeAuthorization>()
        internal val eventPositiveRules = mutableSetOf<(UserIdentity, E, V) -> PositiveAuthorization>()
        internal val eventNegativeRules = mutableSetOf<(UserIdentity, E, V) -> NegativeAuthorization>()

        fun reads(init: AuthorizationReadRulesBlock<V>.() -> Unit) {
            val block = AuthorizationReadRulesBlock<V>()
            block.init()
            readPositiveRules.addAll(block.positiveBlock.rules)
            readNegativeRules.addAll(block.negativeBlock.rules)
        }

        fun events(init: AuthorizationEventsRulesBlock<E, V>.() -> Unit) {
            val block = AuthorizationEventsRulesBlock<E, V>()
            block.init()
            eventPositiveRules.addAll(block.positiveBlock.rules)
            eventNegativeRules.addAll(block.negativeBlock.rules)
        }
    }

    class AuthorizationReadRulesBlock<V> {

        internal lateinit var positiveBlock: AuthorizationReadPositiveRulesBlock<V>
        internal lateinit var negativeBlock: AuthorizationReadNegativeRulesBlock<V>

        fun positive(init: AuthorizationReadPositiveRulesBlock<V>.() -> Unit) {
            positiveBlock = AuthorizationReadPositiveRulesBlock<V>()
            positiveBlock.init()
        }

        fun negative(init: AuthorizationReadNegativeRulesBlock<V>.() -> Unit) {
            negativeBlock = AuthorizationReadNegativeRulesBlock<V>()
            negativeBlock.init()
        }

    }

    class AuthorizationReadPositiveRulesBlock<V> {
        internal val rules = mutableSetOf<(UserIdentity, Model<out ModelProperties>, V) -> PositiveAuthorization>()

        fun rule(f: (UserIdentity, Model<out ModelProperties>, V) -> PositiveAuthorization) {
            rules.add(f)
        }
    }

    class AuthorizationReadNegativeRulesBlock<V> {
        internal val rules = mutableSetOf<(UserIdentity, Model<out ModelProperties>, V) -> NegativeAuthorization>()

        fun rule(f: (UserIdentity, Model<out ModelProperties>, V) -> NegativeAuthorization) {
            rules.add(f)
        }
    }

    class AuthorizationEventsRulesBlock<E, V> {
        internal lateinit var positiveBlock: AuthorizationEventsPositiveRulesBlock<E, V>
        internal lateinit var negativeBlock: AuthorizationEventsNegativeRulesBlock<E, V>

        fun positive(init: AuthorizationEventsPositiveRulesBlock<E, V>.() -> Unit) {
            positiveBlock = AuthorizationEventsPositiveRulesBlock()
            positiveBlock.init()
        }

        fun negative(init: AuthorizationEventsNegativeRulesBlock<E, V>.() -> Unit) {
            negativeBlock = AuthorizationEventsNegativeRulesBlock()
            negativeBlock.init()
        }
    }

    class AuthorizationEventsPositiveRulesBlock<E, V> {
        internal val rules = mutableSetOf<(UserIdentity, E, V) -> PositiveAuthorization>()

        fun rule(f: (UserIdentity, E, V) -> PositiveAuthorization) {
            rules.add(f)
        }
    }

    class AuthorizationEventsNegativeRulesBlock<E, V> {
        internal val rules = mutableSetOf<(UserIdentity, E, V) -> NegativeAuthorization>()

        fun rule(f: (UserIdentity, E, V) -> NegativeAuthorization) {
            rules.add(f)
        }
    }


}


/*
data class StateMachineDescription(private val stateMachine: StateMachine<out ModelProperties, out IEvent, out Enum<out Enum<*>>, *>) {
    val states = stateMachine.states.map { StateDescription(it) }
}

data class StateDescription(private val state: State<out ModelProperties, out IEvent, out Enum<out Enum<*>>>) {
    val name = state.name
    val transitions = state.transitions.map { TransitionDescription(it as Transition<out ModelProperties, out IEvent, Enum<*>, V>) }
}

class TransitionDescription(private val transition: Transition<out ModelProperties, out IEvent, Enum<*>>) {
    val targetState = transition.targetState.name
    val guards = transition.guardFunctions.map {
        val funString = it.toString()
        val startIndex = funString.indexOf("`") + 1
        funString.substring(startIndex, funString.indexOf("`", startIndex + 1))
    }
}

 */


// TODO: use DSL for SimpleBackend like we do with the state machines
