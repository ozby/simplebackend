package simplebackend.lib

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.Left
import io.grpc.Status
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import lib.*
import simplebackend.EventGrpcKt
import simplebackend.Simplebackend.*
import simplebackend.lib.MigrationAction.*
import simplebackend.lib.statemachine.StateMachine
import simplebackend.logAndMakeInternalException
import java.time.Instant
import kotlin.reflect.KFunction1
import kotlin.system.exitProcess

// TODO: Be conservative in what you send, be liberal in what you accept (?)

class EventService<E : IEvent>(
    private val eventParser: (name: String, modelId: String, params: String, userIdentityId: String) -> E,
    private val eventStore: EventStore<E>,
    private val authorizer: IAuthorizer<E>,
    private val stateMachineProvider: KFunction1<String, StateMachine<*, E, *>>,
    private val modelViewProvider: (String) -> IModelView<*>,
    private val json: Json,
    private val migrations: IMigrations<E>?,
    private val managedModels: Set<ManagedModel<*, E, *>>,
) : EventGrpcKt.EventCoroutineImplBase() {

    private val stateFlow: MutableStateFlow<EventUpdatedResponse> = MutableStateFlow(EventUpdatedResponse.newBuilder().build())

    internal fun start() {
        migrateObsoleteEvents()

        println("Reading stored events")
        val events = try {
            eventStore.readAllEvents()
        } catch (e: Exception) {
            println("Could not read events from the database, perhaps because you have deleted an event in your code? If so, you may want to add a migration step.")
            e.printStackTrace()
            exitProcess(1)
        }

        println("Processing stored events")
        GlobalScope.launch {        // Should it really be GlobalScope?
            events.forEach {
                processEventAtStartup(it)
            }
            println("Processed ${events.size} stored events")
        }
    }

    private suspend fun processEventAtStartup(event: E) {
        try {
            when (val result = process(
                event,
                storeEvent = false,
                performActions = false,
                withGuards = false,
                notifyListeners = false,
                eventOptions = EventOptions(dryRun = false)
            )) {
                is Left -> throw RuntimeException(result.a.toString())
            }
        } catch (e: Exception) {
            println("Could not process events stored in the database")
            val encounteredText = "Encountered an unknown key '"

            val message = e.message
            if (message != null && message.contains(encounteredText)) {
                val missingKeyTextWithStuffAtTheEnd = message.substring(message.indexOf(encounteredText) + encounteredText.length)
                val missingKey = missingKeyTextWithStuffAtTheEnd.substring(0, missingKeyTextWithStuffAtTheEnd.indexOf("'"))
                println(
                    "It seems that the events in your code has been changed and therefore the events stored in the database doesn't match the "
                            + "new event in the code. Did you change '$missingKey' in the event '${event.name}'? " +
                            "To fix this you most likely want to add a migration step."
                )
            }
            e.printStackTrace()
            exitProcess(1)
        }
    }

    private fun migrateObsoleteEvents() {
        if (migrations == null) {
            println("Migrations skipped")
            return
        }
        println("Checking if any event needs to be migrated")
        eventStore.readAllEventsRaw().forEach {
            val migrated = migrations.migrate(it)
            when (migrated.first) {
                keepAsItIs -> return@forEach
                delete -> eventStore.delete(it.eventId)
                migrate -> {
                    val migratedEvent = migrated.second
                    if (migratedEvent == null) {
                        println("Migration failed: no updated event provided for ${it.name} with old schemaVersion ${it.schemaVersion}")
                        exitProcess(1)
                    }
                    if (it.schemaVersion >= migratedEvent.schemaVersion) {
                        println("Could not migrate event '${it.name}', the new event must have a schemaVersion larger than the old event")
                        exitProcess(1)
                    }
                    try {
                        migratedEvent.getParams()
                    } catch (e: Exception) {
                        println("The migrated event doesn't seem to work, the parameters could not be parsed.")
                        e.printStackTrace()
                        exitProcess(1)
                    }
                    eventStore.update(it.eventId, migratedEvent)
                    println("simplebackend.Event ${it.name} was migrated")
                }
            }
        }
    }

    override suspend fun createEvent(request: EventRequest): Response {
        try {
            val userIdentity = ctx.call {
                return@call userIdentityKey.get()
            }

            if (request.modelId.length < 36) {  // TODO: we should make it so that the id is generated on the backend
                throw Status.INVALID_ARGUMENT.withDescription("modelId is too short").asRuntimeException()
            }

            val event = eventParser(request.eventName, request.modelId, request.eventParametersJson, userIdentity.id)
            validateParams(event)
            val eventOptions = EventOptions(dryRun = request.eventOptions.dryRun)
            if (!authorizer.isAllowedToCreateEvent(userIdentity, event)) {
                throw Status.PERMISSION_DENIED.asRuntimeException()
            }

            when (val result = process(event, eventOptions, request.eventParametersJson, userIdentity = userIdentity)) {
                is Left -> throw result.a.toGrpcException()
                is Right -> return Response.newBuilder().setResponseJson(json.encodeToString(result.b)).build()
            }
        } catch (e: Exception) {
            throw logAndMakeInternalException(e)
        }
    }

    private fun validateParams(event: E) {
        try {
            event.getParams()
        } catch (e: Exception) {
            throw Status.INVALID_ARGUMENT.withDescription("At least one parameter is invalid or missing for event ${event.name}. The request contained these parameters: '${event.params}'  ${e.message}")
                .asRuntimeException()
        }
    }

    override fun subscribe(request: SubscriptionRequest): Flow<EventUpdatedResponse> {
        try {
            ctx.call {
                if (!authorizer.isAllowedToSubscribeToEvents(userIdentityKey.get())) {
                    throw Status.PERMISSION_DENIED.asRuntimeException()
                }
            }
            return stateFlow
        } catch (e: Exception) {
            throw logAndMakeInternalException(e)
        }
    }

    private val mutex = Mutex()

    suspend fun process(
        event: E,
        eventOptions: EventOptions,
        eventParametersJson: String = "",
        storeEvent: Boolean = true,
        performActions: Boolean = true,
        userIdentity: UserIdentity? = null,
        withGuards: Boolean = true,
        notifyListeners: Boolean = true,
    ): Either<Problem, Model<out ModelProperties>?> {
        mutex.withLock {
            val modelView = modelViewProvider(event.modelType)
            val stateMachine = stateMachineProvider(event.modelType)  // one event may trigger many state machines ??

            if (!stateMachine.transitionExists(event, modelView)) {
                return Left(Problem.noTransitionAvailableForEvent(event.name))
            }

            if (withGuards) {
                if (userIdentity == null) {
                    throw RuntimeException()
                }
                val preventMessages = stateMachine.preventedByGuards(event, userIdentity, modelView)
                if (preventMessages.isNotEmpty()) {
                    return Left(Problem.preventedByGuard(preventMessages))
                }
            }

            val dryRun = eventOptions.dryRun
            if (storeEvent && !dryRun) {
                eventStore.store(event, eventParametersJson)    // TODO: ugly!
            }

            val updatedModel = stateMachine.eventOccurred(event, isDryRun = dryRun, performActions = performActions, modelView)
            when (updatedModel) {
                is Left -> return updatedModel
                is Right -> {
                    if (notifyListeners && !dryRun) {
                        GlobalScope.launch {
                            stateMachine.onStateChangeListeners.forEach { (it as suspend (Model<out ModelProperties>) -> Unit)(updatedModel.b!!) }
                        }
                    }
                    if (updatedModel.b != null && !dryRun) {
                        stateFlow.value =
                            EventUpdatedResponse.newBuilder()
                                .setType(event.modelType)
                                .setId(updatedModel.b!!.id)
                                .setTimestamp(Instant.now().toEpochMilli())
                                .build()
                    }
                    return updatedModel
                }
            }
        }
    }

}

data class EventOptions(val dryRun: Boolean)
