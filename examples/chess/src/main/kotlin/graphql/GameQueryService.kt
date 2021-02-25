package graphql

import GameProperties
import arrow.core.Either
import com.expediagroup.graphql.types.operations.Query
import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.Model
import views.GameView

class GameQueryService : Query {
    fun game(id: String? = null): List<Game> {
        if (id == null) {
            return GameView.getAll().map { Game(it) }
        }
        val model = GameView.get(id) ?: return emptyList()
        return listOf(Game(model))
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
            is Either.Left -> emptyList()
            is Either.Right -> transitions.b
        }
    }
}
