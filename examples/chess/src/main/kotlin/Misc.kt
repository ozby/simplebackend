import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.UserIdentity
import statemachines.UserStates
import views.UserView

fun parseEvent(eventName: String, modelId: String, params: String, userIdentityId: String): Event {
    return when (eventName) {
        createGame -> CreateGame(modelId, params, userIdentityId)
        makeMove -> MakeMove(modelId, params, userIdentityId)
        createUser -> CreateUser(modelId, params, userIdentityId)
        else -> throw RuntimeException("Could not parse event with name '$eventName'")
    }
}

/**
 * Returns user if state == active. Else null.
 */
fun getActiveUser(userIdentity: UserIdentity): Model<UserProperties>? {
    val user = UserView.get(userIdentity)
    if (user?.state?.equals(UserStates.active.name) ?: false) {
        return user
    } else {
        return null
    }
}


const val computerPlayer = "Computer player"

val systemUserIdentity = UserIdentity("perhaps a secret system user")