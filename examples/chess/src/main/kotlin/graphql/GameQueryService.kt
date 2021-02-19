package graphql

import com.expediagroup.graphql.types.operations.Query
import graphql.models.Game
import views.GameView

class GameQueryService : Query {
    fun game(id: String? = null): List<Game> {
        if (id == null) {
            return GameView.getAll().map { Game.fromModel(it) }
        }
        val game = GameView.get(id) ?: return emptyList()
        return listOf(Game.fromModel(game))
    }
}
