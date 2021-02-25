package views

import Event
import GameProperties
import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.IModelView
import com.prettybyte.simplebackend.lib.Model

object GameView : IModelView<GameProperties> {

    private val games = HashMap<String, Model<GameProperties>>()

    override fun get(id: String): Model<GameProperties>? {
        return games[id]
    }

    override fun create(model: Model<GameProperties>) {
        games[model.id] = model
    }

    override fun update(new: Model<GameProperties>) {
        games[new.id] = new
    }

    fun getAll(): List<Model<GameProperties>> {
        return games.values.toList()
    }

    fun history(id: String) = SimpleBackend.getEventsForModelId<Event>(id)
        .map { "${UserView.getByUserIdentityId(it.userIdentityId)?.properties?.firstName ?: it.userIdentityId} $it" }

}
