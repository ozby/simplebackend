package com.prettybyte.simplebackend.lib

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.Left
import com.prettybyte.simplebackend.SingletonStuff
import com.prettybyte.simplebackend.lib.MigrationAction.*
import com.prettybyte.simplebackend.lib.NegativeAuthorization.deny
import com.prettybyte.simplebackend.lib.PositiveAuthorization.allow
import com.prettybyte.simplebackend.lib.statemachine.StateMachine
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KFunction1
import kotlin.system.exitProcess

// TODO: Be conservative in what you send, be liberal in what you accept (?)

class EventService<E : IEvent, V>(
    private val eventStore: IEventStore<E>,
    private val stateMachineProvider: KFunction1<IEvent, StateMachine<*, E, *, V>>,
    private val migrations: IMigrations<E>?,
) {

    //  private val stateFlow: MutableStateFlow<Simplebackend.EventUpdatedResponse> = MutableStateFlow(Simplebackend.EventUpdatedResponse.newBuilder().build())

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
                userIdentity = UserIdentity.system(),
                withGuards = false,
                notifyListeners = false,
                preventModelUpdates = false
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

/*    override fun subscribe(request: Simplebackend.SubscriptionRequest): Flow<Simplebackend.EventUpdatedResponse> {
        println("Client subscribed")
        return stateFlow
    }

 */

    private val mutex = Mutex()

    suspend fun process(
        event: E,
        eventParametersJson: String = "",
        storeEvent: Boolean = true,
        performActions: Boolean,
        userIdentity: UserIdentity,
        withGuards: Boolean = true,
        notifyListeners: Boolean = true,
        preventModelUpdates: Boolean,
    ): Either<Problem, List<Model<out ModelProperties>>> {

//        if (event.modelId.length < 36) {  // TODO: we should make it so that the id is generated on the backend
        //          DataFetcherResult.newResult<CreateEventResponse>().error(Problem(Status.INVALID_ARGUMENT, "modelId is too short")).build()
        //    }

        validateParams(event)

        val negativeAuthProblem = SingletonStuff.getEventNegativeRules<V, E>().firstOrNull { it(userIdentity, event, SingletonStuff.getViews()) == deny }
        if (negativeAuthProblem != null) {
            return Left(Problem(Status.UNAUTHORIZED, extractNameFromFunction(negativeAuthProblem)))
        }
        if (SingletonStuff.getEventPositiveRules<V, E>().none { it(userIdentity, event, SingletonStuff.getViews()) == allow }) {
            return Left(Problem(Status.UNAUTHORIZED, "Event '${event.name}' was not created since no policy allowed the operation"))
        }

        val secondaryEvents = mutableListOf<E>()
        val updatedModels = mutableListOf<Model<out ModelProperties>>()
        mutex.withLock {
            val stateMachine = stateMachineProvider(event)  // one event may trigger many state machines ??
            if (!stateMachine.canHandle(event)) {
                return Left(Problem.noCannotHandleEvent(event.name))
            }

            if (withGuards) {
                val failedGuards = stateMachine.preventedByGuards(event, userIdentity)
                if (failedGuards.isNotEmpty()) {
                    return Left(Problem.preventedByGuard(failedGuards))
                }
            }

            if (storeEvent && !preventModelUpdates) {
                eventStore.store(event, eventParametersJson)    // TODO: ugly!
            }

            val eventResult = stateMachine.eventOccurred(
                event,
                preventModelUpdates = preventModelUpdates,
                performActions = performActions
            )
            when (eventResult) {
                is Left -> return eventResult
                is Right -> {
                    secondaryEvents.addAll(eventResult.b.second)
                    updatedModels.addAll(eventResult.b.first)
                    if (notifyListeners && !preventModelUpdates) {
                        GlobalScope.launch {
                            updatedModels.forEach {
                                val updatedModel = it
                                stateMachine.onStateChangeListeners.forEach { (it as suspend (Model<out ModelProperties>) -> Unit)(updatedModel) }
                            }
                        }
                    }
                    if (!preventModelUpdates) {
                        updatedModels.forEach {
                            // TODO: notify listeners
/*                            stateFlow.value =
                                Simplebackend.EventUpdatedResponse.newBuilder()
                                    .setType(it.graphQlName)
                                    .setId(it.id)
                                    .setTimestamp(Instant.now().toEpochMilli())
                                    .build()
 */
                        }
                    }
                }
            }
        }
        secondaryEvents.forEach {       // TODO: we have just released the mutex lock. Theoretically, another event can squeeze itself in before all secondary events has been processed.
            process(
                it,
                it.params,
                userIdentity = UserIdentity.system(),
                performActions = performActions,
                preventModelUpdates = preventModelUpdates,
                storeEvent = false
            )

        }
        return Either.right(updatedModels)
    }

    private fun validateParams(event: E) {
        try {
            event.getParams()
        } catch (e: Exception) {
            throw RuntimeException("At least one parameter is invalid or missing for event ${event.name}. The request contained these parameters: '${event.params}'  ${e.message}")
        }
    }

}

private fun extractNameFromFunction(f: Function<Any>): String {
    val funString = f.toString()
    val startIndex = funString.indexOf("`") + 1
    return funString.substring(startIndex, funString.indexOf("`", startIndex + 1))
}

