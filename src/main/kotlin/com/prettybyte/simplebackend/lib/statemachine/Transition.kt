package com.prettybyte.simplebackend.lib.statemachine

import com.prettybyte.simplebackend.lib.*

class Transition<T : ModelProperties, E : IEvent, ModelStates : Enum<*>>(
    val trigger: String?,
    val triggeredIf: ((T) -> Boolean)?,
    val targetState: ModelStates
) {

    internal lateinit var currentState: State<T, E, ModelStates>

    private lateinit var eventParams: EventParams
    private lateinit var effectCreateModelFunction: (EventParams) -> T
    private var effectUpdateModelFunction: ((Model<T>, EventParams) -> T)? = null
    private var model: T? = null
    private val guardFunctions: MutableList<(Model<T>?, E, UserIdentity) -> BlockedByGuard?> = mutableListOf()

    fun effectCreateModel(f: (EventParams) -> T) {
        effectCreateModelFunction = f
    }

    fun effectUpdateModel(f: (Model<T>, EventParams) -> T) {
        effectUpdateModelFunction = f
    }

    fun guard(guard: (Model<T>?, E, UserIdentity) -> BlockedByGuard?) {
        guardFunctions.add(guard)
    }

    internal fun enterTransition(dryRun: Boolean, retrieveState: (ModelStates) -> State<T, E, ModelStates>): State<T, E, ModelStates> {
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
        modelType: String,
        newState: State<T, E, ModelStates>,
        event: IEvent,
        view: IModelView<T>,
        isDryRun: Boolean
    ): Model<T> {
        this.eventParams = eventParams
        if (currentState.isInitialState() && effectCreateModelFunction != null) {
            if (event.modelId == null) {
                throw RuntimeException("modelId is required to create model")
            }
            val created = Model(
                id = event.modelId!!,       // TODO: !!
                state = newState.name,
                properties = effectCreateModelFunction(eventParams)
            )
            if (!isDryRun) {
                view.create(created)
            }
            return created
        }
        if (modelBefore == null) {
            throw RuntimeException()
        }


        if (effectUpdateModelFunction != null && modelBefore != null) {
            if (modelBefore == null) {
                throw RuntimeException("executeEffect")
            }
            val newModel = modelBefore.copy(state = newState.name, properties = effectUpdateModelFunction!!.invoke(modelBefore, eventParams))
            if (!isDryRun) {
                view.update(newModel)
            }
            return newModel
        }

        val modelWithUpdatedState = modelBefore.copy(state = newState.name)
        if (!isDryRun) {
            view.update(modelWithUpdatedState)
        }
        return modelWithUpdatedState
    }


    /**
     * Returns a list of problems. If the list is empty, all guards were passed
     */
    internal fun verifyGuard(event: E, model: Model<T>?, userIdentity: UserIdentity): List<BlockedByGuard> {
        return guardFunctions.map { it(model, event, userIdentity) }.filterNotNull()
    }

}
