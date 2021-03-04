package com.prettybyte.simplebackend.lib.statemachine

import com.prettybyte.simplebackend.lib.EventParams
import com.prettybyte.simplebackend.lib.IEvent
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.ModelProperties

class Block<T : ModelProperties, E : IEvent> {

    internal val effectCreateModelFunctions = mutableListOf<(EventParams) -> T>()
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
