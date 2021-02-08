package simplebackend

import io.grpc.Status
import io.grpc.StatusRuntimeException
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
import simplebackend.lib.*
import simplebackend.lib.statemachine.StateMachine

const val USER_IDENTITY = "userIdentity"

class SimpleBackend<E : IEvent>(
    eventParser: (name: String, modelId: String, params: String, userIdentityId: String) -> E,      // TODO: reflection?
    authorizer: IAuthorizer<E>,
    private val managedModels: Set<ManagedModel<*, E, *>>,
    private val port: Int,
    serModule: SerializersModule,   // TODO: ugly. Can reflection help?
    databaseConnection: DatabaseConnection,
    migrations: IMigrations<E>?,
) {
    private var ktorServer: NettyApplicationEngine
    private val json: Json = Json { serializersModule = serModule }

    // private val grpcServer: Server
    private val eventStore: EventStore<E>
    private val eventService: EventService<E>

    // TODO 1.0: https://www.baeldung.com/kotlin/builder-pattern

    init {
        eventStore = EventStore(databaseConnection, eventParser)
        eventService = EventService(eventParser, eventStore, authorizer, ::stateMachineProvider, ::modelViewProvider, json, migrations, managedModels)
/*        grpcServer =
            ServerBuilder.forPort(port)
                // .useTransportSecurity() TODO 1.0
                .intercept(AuthenticationInterceptor)
                .intercept(UserIdentityInjector)
                .addService(AuthenticationService(authorizer))
                .addService(eventService)
                .addService(QueryService(::modelViewProvider, authorizer))
                .build()
                //
 */

        ktorServer = embeddedServer(Netty, port) {
            routing {
                get("/") {
                    call.respondText("My Example Blog", ContentType.Text.Html)
                }
            }
        }
    }

    private fun modelViewProvider(modelType: String): IModelView<*> {
        return managedModels.find { it.kClass.simpleName == modelType }?.view ?: throw RuntimeException("Can't find model view for type '$modelType'")
    }

    private fun stateMachineProvider(modelType: String): StateMachine<*, E, *> {
        return managedModels.find { it.kClass.simpleName == modelType }?.stateMachine ?: throw RuntimeException("Can't find model view for type '$modelType'")
    }

    fun start() {
        eventService.start()
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
}

fun logAndMakeInternalException(e: Exception): Throwable {
    if (e is StatusRuntimeException) {
        throw e
    }
    e.printStackTrace()
    return Status.INTERNAL.withDescription(e.message).asRuntimeException()  // TODO 1.0: don't pass e.message
}

data class DatabaseConnection(val url: String, val driver: String)