package views

import Event
import GameProperties
import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.Auth
import com.prettybyte.simplebackend.lib.IModelView
import com.prettybyte.simplebackend.lib.Model

object GameView : IModelView<GameProperties> {

    private val games = HashMap<String, Model<GameProperties>>()

    override fun get(id: String, auth: Auth<Model<GameProperties>>): Auth<Model<GameProperties>> {
        return auth.withValue(games[id])
    }

    override fun create(model: Model<GameProperties>) {
        games[model.id] = model
    }

    override fun update(new: Model<GameProperties>) {
        games[new.id] = new
    }

    fun getAll(authenticator: Auth<List<Model<GameProperties>>>): Auth<List<Model<GameProperties>>> {
        return authenticator.withValue(games.values.toList())
    }

    fun history(id: String) = SimpleBackend.getEventsForModelId<Event>(id)
        .map {
            val userName = UserView.getByUserIdentityIdWithoutAuthorization(it.userIdentityId)?.properties?.firstName ?: it.userIdentityId
            "$userName $it"
        }

}
