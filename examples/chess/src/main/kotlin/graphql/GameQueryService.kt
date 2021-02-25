package graphql

import AuthorizeIfIsPlayer
import GameProperties
import RemoveIfIsNotPlayer
import arrow.core.Either.Left
import arrow.core.Either.Right
import com.expediagroup.graphql.types.operations.Query
import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.ktorgraphql.AuthorizedContext
import views.GameView

class GameQueryService : Query {
    fun game(id: String? = null, context: AuthorizedContext): List<Game> {
        if (id == null) {
            return when (val either = GameView.getAll(RemoveIfIsNotPlayer(context.userIdentity))) {
                is Left -> throw either.a.asException()
                is Right -> either.b.map { Game(it) }
            }
        }
        return when (val either = GameView.get(id, AuthorizeIfIsPlayer(context.userIdentity))) {
            is Left -> throw either.a.asException()
            is Right -> if (either.b == null) emptyList() else listOf(Game(either.b!!))
        }
    }
}

class Game(private val model: Model<GameProperties>) {
    fun id(): String = model.id
    fun state(): String = model.state
    fun pieces(): List<String> = model.properties.pieces
    fun whitePlayerId(): String = model.properties.whitePlayerId
    fun blackPlayerId(): String = model.properties.blackPlayerId
    fun history(): List<String> = GameView.history(model.id)
    fun transitions(): List<String> {
        return when (val transitions = SimpleBackend.getTransitions(GameProperties::class, model.id)) {
            is Left -> emptyList()
            is Right -> transitions.b
        }
    }
}
