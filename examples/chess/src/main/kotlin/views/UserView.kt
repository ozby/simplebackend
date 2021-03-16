package views

import UserProperties
import com.prettybyte.simplebackend.lib.IModelView
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.UserIdentity

object UserView : IModelView<UserProperties> {

    private val users = HashMap<String, Model<UserProperties>>()

    override fun getWithoutAuthorization(id: String): Model<UserProperties>? {
        return users[id]
    }

    override fun create(model: Model<UserProperties>) {
        users[model.id] = model
    }

    override fun update(new: Model<UserProperties>) {
        users[new.id] = new
    }

    override fun delete(id: String) {
        users.remove(id)
    }

    fun getWithoutAuthorization(userIdentity: UserIdentity): Model<UserProperties>? {
        return users.values.firstOrNull { it.properties.userIdentityId == userIdentity.id }
    }

    fun getByUserIdentityIdWithoutAuthorization(userIdentityId: String): Model<UserProperties>? {
        return users[userIdentityId]
    }

}
