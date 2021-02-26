import EventAuthorizer.Roles.editor
import com.prettybyte.simplebackend.lib.IEventAuthorizer
import com.prettybyte.simplebackend.lib.Problem
import com.prettybyte.simplebackend.lib.UserIdentity
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import views.UserView

object EventAuthorizer : IEventAuthorizer<Event> {

    // Available roles:
    enum class Roles {
        editor,
        viewer,
    }

    override fun onExchangeJWT(jws: Jws<Claims>): Problem? {
        return null
    }

    override fun isAllowedToCreateEvent(userIdentity: UserIdentity, event: Event): Boolean {
        return when (event) {
            is CreateGame -> hasRole(editor, userIdentity)
            is MakeMove -> hasRole(editor, userIdentity)
            is CreateUser -> event.getParams().userIdentityId == userIdentity.id
        }
    }

    override fun isAllowedToSubscribeToEvents(userIdentity: UserIdentity): Boolean {
        return true
    }

    private fun hasRole(role: Roles, userIdentity: UserIdentity): Boolean {
        return UserView.getWithoutAuthorization(userIdentity)?.properties?.roles?.contains(role) ?: false
    }

}
