package modelviews

import Event
import GameProperties
import Views
import com.prettybyte.simplebackend.lib.IModelView
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.ReadModelListAuthorizer
import simpleBackend

class GameView<V> : IModelView<GameProperties, V> {

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

    fun getAll(): ReadModelListAuthorizer<GameProperties, V> {
        return ReadModelListAuthorizer(games.values.toList())
    }

    fun history(id: String, user: UserView<Views>): List<String> {
        return simpleBackend.getEventsForModelId<Event, Views>(id)
            .map {
                val userName = user.getByUserIdentityIdWithoutAuthorization(it.userIdentityId)?.properties?.firstName ?: it.userIdentityId
                "$userName $it"
            }
    }

    fun getAllWhereUserIsPlayer(userId: String): ReadModelListAuthorizer<GameProperties, V> {
        return ReadModelListAuthorizer(games.values.filter { it.properties.whitePlayerId == userId || it.properties.blackPlayerId == userId })
    }

}
