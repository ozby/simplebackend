package com.prettybyte.simplebackend

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import com.expediagroup.graphql.generator.TopLevelObject
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.prettybyte.simplebackend.lib.*
import com.prettybyte.simplebackend.lib.ktorgraphql.GraphQLHelper
import com.prettybyte.simplebackend.lib.ktorgraphql.getGraphQLServer
import com.prettybyte.simplebackend.lib.ktorgraphql.schema.EventMutationService
import com.prettybyte.simplebackend.lib.statemachine.State
import com.prettybyte.simplebackend.lib.statemachine.StateMachine
import com.prettybyte.simplebackend.lib.statemachine.Transition
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

const val USER_IDENTITY = "userIdentity"

internal class SimpleBackendWrapped<E : IEvent>(
    eventParser: (name: String, modelId: String, params: String, userIdentityId: String) -> E,      // TODO: reflection?
    eventAuthorizer: IEventAuthorizer<E>,
    private val managedModels: Set<ManagedModel<*, E, *>>,
    private val port: Int,
    serModule: SerializersModule,   // TODO: ugly. Can reflection help?
    databaseConnection: DatabaseConnection,
    migrations: IMigrations<E>?,
    val customGraphqlPackages: List<String>,
    val customQueries: List<TopLevelObject>,
) {
    private var grpcServer: Server
    private var ktorServer: NettyApplicationEngine
    private val json: Json = Json { serializersModule = serModule }

    // private val grpcServer: Server
    private val eventStore: EventStore<E>
    private val eventService: EventService<E>

    // TODO 1.0: https://www.baeldung.com/kotlin/builder-pattern

    init {

        // TODO 1.0: validate that the provided ModelProperties classes are named "xProperties" e.g. GameProperties.

        managedModels.forEach { it.stateMachine.setView(it.view) }

        eventStore = EventStore(databaseConnection, eventParser)
        eventService = EventService(eventParser, eventStore, ::stateMachineProvider, ::modelViewProvider, json, migrations, managedModels)
        grpcServer =
            ServerBuilder.forPort(port + 1)
                // .useTransportSecurity() TODO 1.0
                //  .intercept(AuthenticationInterceptor)
                // .intercept(UserIdentityInjector)
                // .addService(AuthenticationService(authorizer))
                .addService(eventService)
                //  .addService(QueryService(::modelViewProvider, authorizer))
                .build()
        //


        val mapper = jacksonObjectMapper()
        val server =
            getGraphQLServer(
                mapper,
                customGraphqlPackages,
                customQueries,
                listOf(TopLevelObject(EventMutationService(eventService, eventParser, json, eventAuthorizer)))
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

    private fun modelViewProvider(modelType: String): IModelView<*> {
        return managedModels.find { it.kClass.simpleName == modelType }?.view ?: throw RuntimeException("Can't find model view for type '$modelType'")
    }

    private fun stateMachineProvider(event: IEvent): StateMachine<*, E, *> {
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

    fun processEvent(event: E, eventParametersJson: String, userIdentity: UserIdentity) {
        GlobalScope.launch {
            eventService.process(
                event = event,
                eventParametersJson = eventParametersJson,
                withGuards = true,
                eventOptions = EventOptions(dryRun = false),
                userIdentity = userIdentity,
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

    fun getStateMachine(kClass: KClass<out ModelProperties>): Either<Problem, StateMachine<out ModelProperties, E, out Enum<*>>> {
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

object SimpleBackend {

    private lateinit var sb: SimpleBackendWrapped<out IEvent>

    fun <E : IEvent> setup(
        eventParser: (name: String, modelId: String, params: String, userIdentityId: String) -> E,      // TODO: reflection?
        eventAuthorizer: IEventAuthorizer<E>,
        managedModels: Set<ManagedModel<*, E, *>>,
        port: Int,
        serModule: SerializersModule,   // TODO: ugly. Can reflection help?
        databaseConnection: DatabaseConnection,
        migrations: IMigrations<E>?,
        customGraphqlPackages: List<String>,
        customQueries: List<TopLevelObject>
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
            customQueries
        )
    }

    fun <E : IEvent> processEvent(event: E, eventParametersJson: String, userIdentity: UserIdentity) {
        return (sb as SimpleBackendWrapped<E>).processEvent(event, eventParametersJson, userIdentity)
    }

    fun <E : IEvent> getEventsForModelId(id: String): List<E> = (sb as SimpleBackendWrapped<E>).getEventsForModelId(id)

    fun start() = sb.start()

    fun getTransitions(kClass: KClass<out ModelProperties>, id: String): Either<Problem, List<String>> = sb.getTransitions(kClass, id)

    fun <E : IEvent> getStateMachineDescription(kClass: KClass<out ModelProperties>): Either<Problem, StateMachineDescription> =
        when (val either = sb.getStateMachine(kClass)) {
            is Either.Left -> Left(either.a)
            is Either.Right -> Right(StateMachineDescription(either.b))
        }
}

data class StateMachineDescription(private val stateMachine: StateMachine<out ModelProperties, out IEvent, out Enum<out Enum<*>>>) {
    val states = stateMachine.states.map { StateDescription(it) }
}

data class StateDescription(private val state: State<out ModelProperties, out IEvent, out Enum<out Enum<*>>>) {
    val name = state.name
    val transitions = state.transitions.map { TransitionDescription(it as Transition<out ModelProperties, out IEvent, Enum<*>>) }
}

class TransitionDescription(private val transition: Transition<out ModelProperties, out IEvent, Enum<*>>) {
    val targetState = transition.targetState.name
    val guards = transition.guardFunctions.map {
        val funString = it.toString()
        val startIndex = funString.indexOf("`") + 1
        funString.substring(startIndex, funString.indexOf("`", startIndex + 1))
    }
}
