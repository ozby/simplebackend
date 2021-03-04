package com.prettybyte.simplebackend.lib.statemachine;

import com.prettybyte.simplebackend.lib.IEvent
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.ModelProperties

class State<T : ModelProperties, E : IEvent, ModelStates : Enum<*>>(val name: String) {

    internal var enterBlock: Block<T, E> = Block()
    internal var exitBlock: Block<T, E> = Block()
    internal val transitions = mutableListOf<Transition<T, E, ModelStates>>()

    fun onEnter(init: Block<T, E>.() -> Unit) {
        enterBlock = Block<T, E>()
        enterBlock.init()
    }

    fun onExit(init: Block<T, E>.() -> Unit) {
        exitBlock = Block<T, E>()
        exitBlock.init()
    }

    fun transition(
        triggeredByEvent: String? = null,
        triggeredIf: ((Model<T>) -> Boolean)? = null,
        targetState: ModelStates,
        init: Transition<T, E, ModelStates>.() -> Unit
    ) {  // TODO: triggeredByEvent should be E
        val transition = Transition<T, E, ModelStates>(triggeredByEvent, triggeredIf, targetState)
        transition.init()
        transitions.add(transition)
    }

    internal fun getTransitionForEvent(event: IEvent): Transition<T, E, ModelStates>? {
        return transitions.firstOrNull() { it.canBeTriggeredByEvent(event) }
    }

    internal fun isInitialState(): Boolean {
        return name == initial
    }


}
