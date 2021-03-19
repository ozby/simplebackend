package com.prettybyte.simplebackend.lib.statemachine;

import com.prettybyte.simplebackend.lib.IEvent
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.ModelProperties

class State<T : ModelProperties, E : IEvent, ModelStates : Enum<*>, V>(val name: String) {

    internal var enterBlock: Block<T, E> = Block()
    internal var exitBlock: Block<T, E> = Block()
    internal val transitions = mutableListOf<Transition<T, E, ModelStates, V>>()
    internal val onEventBlocks = mutableListOf<Pair<String, BlockWithGuards<T, E>>>()

    fun onEnter(init: Block<T, E>.() -> Unit) {
        enterBlock = Block<T, E>()
        enterBlock.init()
    }

    fun onExit(init: Block<T, E>.() -> Unit) {
        exitBlock = Block<T, E>()
        exitBlock.init()
    }

    fun onEvent(event: String, init: BlockWithGuards<T, E>.() -> Unit) {
        val onEventBlock = BlockWithGuards<T, E>()
        onEventBlock.init()
        onEventBlocks.add(Pair(event, onEventBlock))
    }

    fun transition(
        triggeredByEvent: String? = null,
        triggeredIf: ((Model<T>) -> Boolean)? = null,
        targetState: ModelStates,
        init: Transition<T, E, ModelStates, V>.() -> Unit
    ) {  // TODO: triggeredByEvent should be E
        val transition = Transition<T, E, ModelStates, V>(triggeredByEvent, triggeredIf, targetState)
        transition.init()
        transitions.add(transition)
    }

    internal fun getTransitionForEvent(event: IEvent): Transition<T, E, ModelStates, V>? {
        return transitions.firstOrNull() { it.canBeTriggeredByEvent(event) }
    }

    internal fun isInitialState(): Boolean {
        return name == initial
    }


}
