package views

import UserProperties
import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import com.prettybyte.simplebackend.lib.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

object UserView : IModelView<UserProperties>, IQueryView {

    private val users = HashMap<String, Model<UserProperties>>()

    override fun get(id: String): Model<UserProperties>? {
        return users[id]
    }

    override fun create(model: Model<UserProperties>) {
        users[model.id] = model
    }

    override fun update(new: Model<UserProperties>) {
        users[new.id] = new
    }

    data class QueryParams(val byUserIdentityId: String?)

    override fun getQueryParamsClass(): KClass<*> = QueryParams::class

    override fun query(params: Any): Either<Problem, JsonString> {
        params as QueryParams
        if (params.byUserIdentityId != null) {
            val user = getByUserIdentityId(params.byUserIdentityId) ?: return Left(Problem(Status.NOT_FOUND))
            return Right(Json.encodeToString(user))
        }
        return Left(Problem(Status.INVALID_ARGUMENT, "Invalid query"))
    }

    fun get(userIdentity: UserIdentity): Model<UserProperties>? = getByUserIdentityId(userIdentity.id)

    fun getByUserIdentityId(userIdentityId: String): Model<UserProperties>? {
        return users.values.firstOrNull { it.properties.userIdentityId == userIdentityId }
    }

}
