package schema

import com.expediagroup.graphql.types.operations.Query
import schema.models.GraphQLGame
import views.GameView

class GameQueryService : Query {
    fun game(id: String? = null): List<GraphQLGame> {
        if (id == null) {
            return GameView.getAll().map { GraphQLGame.fromModel(it) }
        }
        val game = GameView.get(id) ?: return emptyList()
        return listOf(GraphQLGame.fromModel(game))
    }
}
