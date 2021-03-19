package graphql

import GameProperties
import Views
import arrow.core.Either.Left
import arrow.core.Either.Right
import com.expediagroup.graphql.types.operations.Query
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.ktorgraphql.AuthorizedContext
import simpleBackend

class GameQueryService(private val views: Views) : Query {
    fun game(id: String? = null, context: AuthorizedContext): List<Game> {
        if (id == null) {
            return when (val either = views.game.getAll().auth(context.userIdentity)) {
                is Left -> throw either.a.asException()
                is Right -> either.b!!.map { Game(it, views) }
            }
        }
        return when (val either = views.game.get(id).auth(context.userIdentity)) {
            is Left -> throw either.a.asException()
            is Right -> if (either.b == null) emptyList() else listOf(Game(either.b!!, views))
        }
    }

/*    fun statemachine(): StateMachineDescriptionGraphQL? {
        return when (val sm = SimpleBackend.getStateMachineDescription<Event>(GameProperties::class)) {
            is Left -> null
            is Right -> StateMachineDescriptionGraphQL(sm.b)
        }
    }
 */

    class Game(private val model: Model<GameProperties>, private val views: Views) {
        fun id(): String = model.id
        fun state(): String = model.state
        fun pieces(): List<String> = model.properties.pieces
        fun whitePlayerId(): String = model.properties.whitePlayerId
        fun blackPlayerId(): String = model.properties.blackPlayerId
        fun history(): List<String> = views.game.history(model.id, views.user)
        fun transitions(): List<String> {
            return when (val transitions = simpleBackend.getTransitions(GameProperties::class, model.id)) {
                is Left -> emptyList()
                is Right -> transitions.b
            }
        }
    }

}

/*
class StateMachineDescriptionGraphQL(private val smd: StateMachineDescription) {
    fun states() = smd.states.map { StateDescriptionGraphQL(it) }
}

class StateDescriptionGraphQL(private val state: StateDescription) {
    fun name() = state.name
    fun transitions() = state.transitions.map { "${it.targetState} (guards: ${it.guards.joinToString(", ")})" }
}
 */