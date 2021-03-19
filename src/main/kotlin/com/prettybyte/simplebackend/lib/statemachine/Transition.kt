package com.prettybyte.simplebackend.lib.statemachine

import com.prettybyte.simplebackend.SingletonStuff
import com.prettybyte.simplebackend.lib.*

class Transition<T : ModelProperties, E : IEvent, ModelStates : Enum<*>, V>(
    val trigger: String?,
    val triggeredIf: ((Model<T>) -> Boolean)?,
    private val targetState: ModelStates
) {

    internal lateinit var currentState: State<T, E, ModelStates, V>

    private lateinit var eventParams: EventParams
    private lateinit var effectCreateModelFunction: (EventParams) -> T
    private var effectUpdateModelFunction: ((Model<T>, EventParams) -> T)? = null
    private var effectCreateEventFunction: ((Model<T>) -> E)? = null
    private var model: T? = null
    private val guardFunctions: MutableList<(Model<T>?, E, UserIdentity, V) -> BlockedByGuard?> = mutableListOf()

    fun effectCreateModel(f: (EventParams) -> T) {
        effectCreateModelFunction = f
    }

    fun effectUpdateModel(f: (Model<T>, EventParams) -> T) {
        effectUpdateModelFunction = f
    }

    fun effectCreateEvent(f: ((Model<T>) -> E)) {
        effectCreateEventFunction = f
    }

    fun guard(guard: (Model<T>?, E, UserIdentity, V) -> BlockedByGuard?) {
        guardFunctions.add(guard)
    }

    internal fun enterTransition(dryRun: Boolean, retrieveState: (ModelStates) -> State<T, E, ModelStates, V>): State<T, E, ModelStates, V> {
        return retrieveState(targetState)
    }

    internal fun canBeTriggeredByEvent(event: IEvent): Boolean {
        if (trigger == null) {
            return false
        }
        return event.name == trigger
    }

    internal fun executeEffect(
        modelBefore: Model<T>?,
        eventParams: EventParams,
        newState: State<T, E, ModelStates, V>,
        event: IEvent,
        view: IModelView<T, V>,
        preventModelUpdates: Boolean,
        performActions: Boolean,
    ): Pair<Model<T>, E?> {
        this.eventParams = eventParams
        if (currentState.isInitialState() && effectCreateModelFunction != null) {
            if (event.modelId == null) {
                throw RuntimeException("modelId is required to create model")
            }
            val modelProperties = effectCreateModelFunction(eventParams)
            val created = Model(
                id = event.modelId!!,       // TODO: !!
                state = newState.name,
                properties = modelProperties,
                graphQlName = getGraphQlName(modelProperties),
            )
            if (!preventModelUpdates) {
                view.create(created)    // TODO: should return error if it already exists
            }
            return Pair(created, null)
        }

        if (modelBefore == null) {
            throw RuntimeException()
        }

        var createdEvent: E? = null
        if (effectCreateEventFunction != null) {
            createdEvent = effectCreateEventFunction!!.invoke(modelBefore)
        }

        if (effectUpdateModelFunction != null) {
            val newModel = modelBefore.copy(state = newState.name, properties = effectUpdateModelFunction!!.invoke(modelBefore, eventParams))
            if (!preventModelUpdates) {
                view.update(newModel)
            }
            return Pair(newModel, createdEvent)
        }


        val modelWithUpdatedState = modelBefore.copy(state = newState.name)
        if (!preventModelUpdates) {
            view.update(modelWithUpdatedState)
        }
        return Pair(modelWithUpdatedState, createdEvent)
    }

    private fun getGraphQlName(modelProperties: T): String {
        val className = modelProperties::class.simpleName ?: throw RuntimeException()
        val indexOfProperties = className.indexOf("Properties")
        return className.substring(0, if (indexOfProperties == -1) className.length else indexOfProperties).toLowerCase()
    }

    /**
     * Returns a list of problems. If the list is empty, all guards were passed
     */
    internal fun verifyGuard(event: E, model: Model<T>?, userIdentity: UserIdentity): List<BlockedByGuard> {
        return guardFunctions.map { it(model, event, userIdentity, SingletonStuff.getViews()) }.filterNotNull()
    }

}
