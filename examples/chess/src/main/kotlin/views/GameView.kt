package views

import Event
import GameProperties
import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.IModelView
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.ReadModelListAuthorizer

object GameView : IModelView<GameProperties> {

    private val games = HashMap<String, Model<GameProperties>>()

    override fun getWithoutAuthorization(id: String): Model<GameProperties>? = games[id]

    override fun create(model: Model<GameProperties>) {
        games[model.id] = model
    }

    override fun update(new: Model<GameProperties>) {
        games[new.id] = new
    }

    override fun delete(id: String) {
        games.remove(id)
    }

    fun getAll(): ReadModelListAuthorizer<GameProperties> {
        return ReadModelListAuthorizer(games.values.toList())
    }

    fun history(id: String): List<String> {
        return SimpleBackend.getEventsForModelId<Event>(id)
            .map {
                val userName = UserView.getByUserIdentityIdWithoutAuthorization(it.userIdentityId)?.properties?.firstName ?: it.userIdentityId
                "$userName $it"
            }
    }

    fun getAllWhereUserIsPlayer(userId: String): ReadModelListAuthorizer<GameProperties> {
        return ReadModelListAuthorizer(games.values.filter { it.properties.whitePlayerId == userId || it.properties.blackPlayerId == userId })
    }

}
