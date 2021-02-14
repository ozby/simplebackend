package schema.models

import Game
import com.prettybyte.simplebackend.lib.Model

data class GraphQLGame(val id: String, val state: String, val pieces: List<String>,
                val whitePlayerId: String,
                val blackPlayerId: String) {
    companion object {
        fun fromModel(model: Model<Game>): GraphQLGame {
            return GraphQLGame(
                id = model.id,
                state = model.state,
                pieces = model.properties.pieces,
                whitePlayerId = model.properties.whitePlayerId,
                blackPlayerId = model.properties.blackPlayerId,
            )
        }
    }
}
