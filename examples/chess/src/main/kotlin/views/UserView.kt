package views

import UserProperties
import arrow.core.Either.Left
import arrow.core.Either.Right
import com.prettybyte.simplebackend.lib.*

object UserView : IModelView<UserProperties> {

    private val users = HashMap<String, Model<UserProperties>>()


    override fun get(id: String, auth: Auth<Model<UserProperties>>): Auth<Model<UserProperties>> {
        return auth.withValue(users[id])
    }

    override fun create(model: Model<UserProperties>) {
        users[model.id] = model
    }

    override fun update(new: Model<UserProperties>) {
        users[new.id] = new
    }

    fun getWithoutAuthorization(userIdentity: UserIdentity): Model<UserProperties>? {
        return users.values.firstOrNull { it.properties.userIdentityId == userIdentity.id }
    }

    fun getByUserIdentityId(userIdentityId: String, readAuthenticator: Auth<Model<UserProperties>>): Auth<Model<UserProperties>> {
        return readAuthenticator.withValue(users.values.firstOrNull { it.properties.userIdentityId == userIdentityId })
    }

    fun getByUserIdentityIdWithoutAuthorization(userIdentityId: String): Model<UserProperties>? {
        return when (val u = getByUserIdentityId(userIdentityId, AllowAll()).get()) {
            is Left -> null
            is Right -> u.b
        }
    }
}
