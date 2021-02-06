package simplebackend.lib.statemachine;

import simplebackend.lib.IEvent
import simplebackend.lib.Model
import simplebackend.lib.ModelProperties

class State<T : ModelProperties, E : IEvent, ModelStates : Enum<*>>(val name: String) {

    private val transitionList = mutableListOf<Transition<T, E, ModelStates>>()
    private val enterActions: MutableList<(Model<T>?, E) -> Unit> = mutableListOf()
    private val exitActions: MutableList<(Model<T>?, E) -> Unit> = mutableListOf()

    fun transition(triggeredByEvent: String, targetState: ModelStates, init: Transition<T, E, ModelStates>.() -> Unit) {  // TODO: triggeredByEvent should be E
        val transition = Transition<T, E, ModelStates>(triggeredByEvent, targetState)
        transition.init()
        transitionList.add(transition)
    }

    fun enterAction(f: (Model<T>?, E) -> Unit) {
        if (isInitialState()) {
            throw RuntimeException("Enter actions are not allowed on the initial state") // should we allow them?
        }
        enterActions.add(f)
    }

    fun exitAction(f: (Model<T>?, E) -> Unit) {
        if (isInitialState()) {
            throw RuntimeException("Exit actions are not allowed on the initial state") // should we allow them?
        }
        enterActions.add(f)
    }

    internal fun enterState(performActions: Boolean, model: Model<T>?, event: E) {
        if (performActions) {
            enterActions.forEach { it(model, event) }
        }
    }

    internal fun exitState(performActions: Boolean, model: Model<T>?, event: E) {
        if (performActions) {
            exitActions.forEach { it(model, event) }
        }
    }

    internal fun getTransitionForEvent(event: IEvent): Transition<T, E, ModelStates>? {
        return transitionList.firstOrNull() { it.canBeTriggeredByEvent(event) }
    }

    internal fun getAllTransitions(): Set<String> {
        return transitionList.map { it.trigger }.toSet()
    }

    internal fun isInitialState(): Boolean {
        return name == initial
    }

}
