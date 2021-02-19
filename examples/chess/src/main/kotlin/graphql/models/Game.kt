package graphql.models

import GameProperties
import com.prettybyte.simplebackend.lib.Model

data class Game(
    val id: String, val state: String, val pieces: List<String>,
    val whitePlayerId: String,
    val blackPlayerId: String
) {
    companion object {
        fun fromModel(model: Model<GameProperties>): Game {
            return Game(
                id = model.id,
                state = model.state,
                pieces = model.properties.pieces,
                whitePlayerId = model.properties.whitePlayerId,
                blackPlayerId = model.properties.blackPlayerId,
            )
        }
    }
}
