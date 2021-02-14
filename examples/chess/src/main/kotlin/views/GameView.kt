package views

import Event
import Game
import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.*
import kotlin.reflect.KClass

object GameView : IModelView<Game>, IQueryView {

    lateinit var simpleBackend: SimpleBackend<Event>
    private val games = HashMap<String, Model<Game>>()

    override fun get(id: String): Model<Game>? {
        return games[id]
    }

    override fun create(model: Model<Game>) {
        games[model.id] = model
    }

    override fun update(new: Model<Game>) {
        games[new.id] = new
    }

    data class QueryParams(val byId: String?, val allWithNames: Boolean?, val howMany: Int?, val historyForId: String?)

    override fun getQueryParamsClass(): KClass<*> = QueryParams::class

    override fun query(params: Any): Either<Problem, JsonString> {
        params as QueryParams
        if (params.byId != null) {
            val game = games[params.byId] ?: return Left(Problem(Status.NOT_FOUND))
            return Right(Json.encodeToString(game))
        }
        if (params.allWithNames == true) {
            return Right(Json.encodeToString(games.values.map {
                GameResponse.from(it)
            }.toList()))
        }
        if (params.historyForId != null) {
            val history = simpleBackend.getEventsForModelId(params.historyForId)
                .map { "${UserView.getByUserIdentityId(it.userIdentityId)?.properties?.firstName ?: it.userIdentityId} $it" }
            return Right(Json.encodeToString(history))
        }
        return Left(Problem(Status.INVALID_ARGUMENT, "Invalid query"))
    }

    fun getAll(): List<Model<Game>> {
        return games.values.toList()
    }


}

@Serializable
data class GameResponse(
    val id: String,
    val state: String,
    val pieces: List<String>,
    val whitePlayerName: String,
    val blackPlayerName: String,
) {
    companion object {
        fun from(game: Model<Game>): GameResponse =
            GameResponse(
                id = game.id,
                state = game.state,
                pieces = game.properties.pieces,
                whitePlayerName = UserView.get(game.properties.whitePlayerId)?.properties?.firstName ?: game.properties.whitePlayerId,
                blackPlayerName = UserView.get(game.properties.blackPlayerId)?.properties?.firstName ?: game.properties.blackPlayerId
            )
    }
}