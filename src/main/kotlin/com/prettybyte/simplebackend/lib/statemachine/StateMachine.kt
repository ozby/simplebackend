package com.prettybyte.simplebackend.lib.statemachine;

import arrow.core.Either
import arrow.core.Right
import com.prettybyte.simplebackend.lib.*
import kotlin.reflect.KClass

const val initial = "initial"

class StateMachine<T : ModelProperties, E : IEvent, ModelStates : Enum<*>, V>(val thisType: KClass<T>) {

    // TODO: Scope control (see https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)

    private lateinit var modelView: IModelView<T, V>
    val onStateChangeListeners: MutableList<in suspend (Model<T>) -> Unit> = mutableListOf<suspend (Model<T>) -> Unit>()
    private lateinit var currentState: State<T, E, ModelStates, V>
    private val states = mutableListOf<State<T, E, ModelStates, V>>()
    private lateinit var initialState: State<T, E, ModelStates, V>

    internal fun setView(view: IModelView<*, V>) {
        modelView = view as IModelView<T, V>
    }

    fun voidState(init: State<T, E, ModelStates, V>.() -> Unit) {
        val state = State<T, E, ModelStates, V>(initial)
        state.init()
        initialState = state
    }

    fun state(modelState: ModelStates, init: State<T, E, ModelStates, V>.() -> Unit) {
        val state = State<T, E, ModelStates, V>(modelState.name)
        state.init()
        states.add(state)
        // TODO: verify that all states have unique names
    }

    private fun getStateByName(name: String): State<T, E, ModelStates, V> {
        return states.firstOrNull { it.name == name }
            ?: throw NoSuchElementException(name)
    }

    internal fun eventOccurred(event: E, preventModelUpdates: Boolean, performActions: Boolean): Either<Problem, Pair<List<Model<T>>, List<E>>> {
        val updatedModels = mutableListOf<Model<T>>()
        val secondaryEvents = mutableListOf<E>()

        val modelId = event.modelId
        val model: Model<T>? = if (modelId == null) null else modelView.getWithoutAuthorization(modelId)

        currentState = if (model == null) initialState else getStateByName(model.state)
        val transition = currentState.getTransitionForEvent(event)
        if (transition != null) {
            val transitionResult = executeTransition(transition, event, model, performActions = performActions, preventModelUpdates = preventModelUpdates)
            updatedModels.addAll(transitionResult.first)
            secondaryEvents.addAll(transitionResult.second)
        }

        val onEventBlock = currentState.onEventBlocks.firstOrNull { it.first == event.name }
        if (onEventBlock != null) {
            val eventBlockResult = executeBlock(onEventBlock.second, event, model, performActions = performActions, preventModelUpdates = preventModelUpdates)
            updatedModels.addAll(eventBlockResult.first)
            secondaryEvents.addAll(eventBlockResult.second)
        }

        return Right(Pair(updatedModels, secondaryEvents))
    }

    private fun executeTransition(
        transition: Transition<T, E, ModelStates, V>,
        event: E,
        model: Model<T>?,
        performActions: Boolean,
        preventModelUpdates: Boolean
    ): Pair<List<Model<T>>, List<E>> {
        val updatedModels = mutableListOf<Model<T>>()
        val secondaryEvents = mutableListOf<E>()
        transition.currentState = currentState

        val exitBlockResult = executeBlock(currentState.exitBlock, event, model, performActions = performActions, preventModelUpdates = preventModelUpdates)
        updatedModels.addAll(exitBlockResult.first)
        secondaryEvents.addAll(exitBlockResult.second)

        val newState = transition.enterTransition(preventModelUpdates) { getStateByName(it.name) }
        val transitionResult = transition.executeEffect(
            model,
            event.getParams(),
            newState,
            event,
            modelView,
            preventModelUpdates = preventModelUpdates,
            performActions = performActions
        )

        var updatedModel = transitionResult.first
        if (transitionResult.second != null) {
            secondaryEvents.add(transitionResult.second!!)
        }
        updatedModel = updatedModel.copy(state = newState.name)
        if (!preventModelUpdates) {
            modelView.update(updatedModel)
        }
        updatedModels.add(updatedModel)

        val enterBlockResult = executeBlock(newState.enterBlock, event, updatedModel, performActions, preventModelUpdates)
        updatedModels.addAll(enterBlockResult.first)
        secondaryEvents.addAll(enterBlockResult.second)

        // Is there any transition that triggers automatically?
        val autoTransition = newState.transitions.filter { it.triggeredIf != null && it.triggeredIf.invoke(updatedModel) }.firstOrNull()
        if (autoTransition != null) {   // TODO: must allow any number of automatic transitions. Recursion?
            autoTransition.currentState = newState
            val newerState = autoTransition.enterTransition(preventModelUpdates) { getStateByName(it.name) }
            executeBlock(newState.exitBlock, event, model, performActions, preventModelUpdates)
            val result = autoTransition.executeEffect(
                updatedModel,
                event.getParams(),
                newerState,
                event,
                modelView,
                preventModelUpdates = preventModelUpdates,
                performActions = performActions,
            )
            updatedModel = result.first
            if (result.second != null) {
                secondaryEvents.add(result.second!!)
            }

            if (performActions) {
                newerState.enterBlock.actions.forEach { it(model, event) }
            }

        }
        return Pair(updatedModels, secondaryEvents)
    }

    private fun executeBlock(
        block: Block<T, E>,
        event: E,
        model: Model<T>?,
        performActions: Boolean,
        preventModelUpdates: Boolean
    ): Pair<List<Model<T>>, List<E>> {
        if (performActions) {
            block.actions.forEach { it(model, event) }
        }

        var updatedModels: List<Model<T>> = emptyList()
        if (model != null) {
            updatedModels =
                block.effectUpdateModelFunctions.map { model.copy(properties = it(model, event.getParams())) }
            if (!preventModelUpdates) {
                updatedModels.forEach { modelView.update(it) }
            }
        }
        val newEvents = block.effectCreateEventFunctions.map { it(model) }
        return Pair(updatedModels, newEvents)
    }

    internal fun canHandle(event: IEvent): Boolean {
        return getAllStates().any { it.getTransitionForEvent(event) != null || it.onEventBlocks.any { it.first == event.name } }
    }

    internal fun transitionExists(event: IEvent, view: IModelView<out ModelProperties, V>): Boolean {
        view as IModelView<T, V>
        val modelId = event.modelId
        val model: Model<T>? = if (modelId == null) null else view.getWithoutAuthorization(modelId)

        currentState = if (model == null) initialState else getStateByName(model.state)
        val transition = currentState.getTransitionForEvent(event)
        return if (transition == null) false else true
    }

    internal fun preventedByGuards(event: E, userIdentity: UserIdentity): List<BlockedByGuard> {
        val modelId = event.modelId
        val model: Model<T>? = if (modelId == null) null else modelView.getWithoutAuthorization(modelId)
        currentState = if (model == null) initialState else getStateByName(model.state)
        val transition = currentState.getTransitionForEvent(event)
        if (transition != null) {
            return transition.verifyGuard(event, model, userIdentity)
        }
        val onEventBlock = currentState.onEventBlocks.firstOrNull { it.first == event.name }
        if (onEventBlock != null) {
            return onEventBlock.second.guardFunctions.mapNotNull { it(model, event, userIdentity) }
        }
        return emptyList()
    }

    private fun getAllStates(): Set<State<T, E, ModelStates, V>> {
        val allStates = HashSet<State<T, E, ModelStates, V>>()
        allStates.addAll(states)
        allStates.add(initialState)
        return allStates
    }

    internal fun handlesType(type: KClass<Model<T>>): Boolean {
        return type == thisType
    }

    fun onStateChange(f: suspend (Model<T>) -> Unit) {
        onStateChangeListeners.add(f)
    }

    fun getEventTransitions(id: String): Either<Problem, List<String>> {
        val model = modelView.getWithoutAuthorization(id)
        val state = getStateByName(model?.state ?: return Either.left(Problem.notFound()))
        return Either.right(state.transitions.mapNotNull { it.trigger })
    }

}

inline fun <reified T : ModelProperties, E : IEvent, ModelStates : Enum<*>, V> stateMachine(init: StateMachine<T, E, ModelStates, V>.() -> Unit): StateMachine<T, E, ModelStates, V> {
    // TODO: validate that all states are reachable?
    val stateMachine = StateMachine<T, E, ModelStates, V>(T::class)
    stateMachine.init()
    // TODO: make sure all states are declared (stateMachine.states == ModelStates)
    return stateMachine
}
