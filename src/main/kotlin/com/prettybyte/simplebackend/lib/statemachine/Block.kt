package com.prettybyte.simplebackend.lib.statemachine

import com.prettybyte.simplebackend.lib.*

open class Block<T : ModelProperties, E : IEvent> {

    private val effectCreateModelFunctions = mutableListOf<(EventParams) -> T>()       // TODO: These should only be present in a transition from voidState
    internal val effectUpdateModelFunctions = mutableListOf<((Model<T>, EventParams) -> T)>()
    internal var effectCreateEventFunctions = mutableListOf<((Model<T>?) -> E)>()
    internal val actions: MutableList<(Model<T>?, E) -> Unit> = mutableListOf()

    fun effectCreateModel(f: (EventParams) -> T) {
        effectCreateModelFunctions.add(f)
    }

    fun effectUpdateModel(f: (Model<T>, EventParams) -> T) {
        effectUpdateModelFunctions.add(f)
    }

    fun effectCreateEvent(f: ((Model<T>?) -> E)) {
        effectCreateEventFunctions.add(f)
    }

    fun action(f: (Model<T>?, E) -> Unit) {
        /*  if (isInitialState()) {
              throw RuntimeException("Enter actions are not allowed on the initial state") // should we allow them?
          }
              // TODO
         */
        actions.add(f)
    }

}

class BlockWithGuards<T : ModelProperties, E : IEvent> : Block<T, E>() {

    internal val guardFunctions: MutableList<(Model<T>?, E, UserIdentity) -> BlockedByGuard?> = mutableListOf()

    fun guard(guard: (Model<T>?, E, UserIdentity) -> BlockedByGuard?) {
        guardFunctions.add(guard)
    }

}