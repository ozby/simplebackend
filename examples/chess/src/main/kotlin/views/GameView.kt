package views

import Event
import GameProperties
import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.IModelView
import com.prettybyte.simplebackend.lib.IReadAuthenticator
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.Problem

object GameView : IModelView<GameProperties> {

    private val games = HashMap<String, Model<GameProperties>>()

    override fun get(id: String, readAuthenticator: IReadAuthenticator<GameProperties>): Either<Problem, Model<GameProperties>?> {
        return readAuthenticator.authorize(games[id])
    }

    override fun create(model: Model<GameProperties>) {
        games[model.id] = model
    }

    override fun update(new: Model<GameProperties>) {
        games[new.id] = new
    }

    fun getAll(readAuthenticator: IReadAuthenticator<GameProperties>): Either<Problem, List<Model<GameProperties>>> {
        val list = games.values.mapNotNull {
            when (val either = readAuthenticator.authorize(it)) {
                is Left -> return Left(either.a)
                is Right -> either.b
            }
        }.toList()
        return Right(list)
    }

    fun history(id: String) = SimpleBackend.getEventsForModelId<Event>(id)
        .map {
            val userName = UserView.getByUserIdentityIdWithoutAuthorization(it.userIdentityId)?.properties?.firstName ?: it.userIdentityId
            "$userName $it"
        }

}
