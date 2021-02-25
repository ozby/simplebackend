package views

import UserProperties
import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import com.prettybyte.simplebackend.lib.*

object UserView : IModelView<UserProperties> {

    private val users = HashMap<String, Model<UserProperties>>()


    override fun get(id: String, readAuthenticator: IReadAuthenticator<UserProperties>): Either<Problem, Model<UserProperties>?> {
        return readAuthenticator.authorize(users[id])
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

    fun getByUserIdentityId(userIdentityId: String, readAuthenticator: IReadAuthenticator<UserProperties>): Either<Problem, Model<UserProperties>?> {
        return readAuthenticator.authorize(users.values.firstOrNull { it.properties.userIdentityId == userIdentityId })
    }

    fun getByUserIdentityIdWithoutAuthorization(userIdentityId: String): Model<UserProperties>? {
        return when (val u = getByUserIdentityId(userIdentityId, AuthorizeAll())) {
            is Left -> null
            is Right -> u.b
        }
    }
}
