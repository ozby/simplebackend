import Authorizer.Roles.editor
import Authorizer.Roles.viewer
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import com.prettybyte.simplebackend.SimpleBackend

import com.prettybyte.simplebackend.lib.IAuthorizer
import com.prettybyte.simplebackend.lib.IQueryView
import com.prettybyte.simplebackend.lib.Problem
import com.prettybyte.simplebackend.lib.UserIdentity

import views.GameView
import views.UserView
import java.util.*

object Authorizer : IAuthorizer<Event> {

    lateinit var simpleBackend: SimpleBackend<Event>

    // Available roles:
    enum class Roles {
        editor,
        viewer,
    }

    override fun onExchangeJWT(jws: Jws<Claims>): Problem? {
        val subject = jws.body.subject
        if (UserView.getByUserIdentityId(subject) != null) return null
        // The user doesn't exist. Should we create a user?
        val email = jws.body["email"].toString()
        if (email.endsWith("@prettybyte.com") && jws.body["email_verified"] == true) {
            val p = "{\"userIdentityId\": \"$subject\", \"firstName\": \"${email}\", \"lastName\": \"\"}"
            simpleBackend.processEvent(
                CreateUser(userId = UUID.randomUUID().toString(), params = p, userIdentityId = subject),
                eventParametersJson = p,
                systemUserIdentity
            )
        }
        return null
    }

    override fun isAllowedToCreateEvent(userIdentity: UserIdentity, event: Event): Boolean {
        return when (event) {
            is CreateGame -> hasRole(editor, userIdentity)
            is MakeMove -> hasRole(editor, userIdentity)
            is CreateUser -> event.getParams().userIdentityId == userIdentity.id
        }
    }

    override fun isAllowedToQuery(userIdentity: UserIdentity, view: IQueryView, parameters: Map<String, String>): Boolean {
        return when (view) {
            is GameView -> hasRole(viewer, userIdentity)
            is UserView -> hasRole(viewer, userIdentity)
            else -> false
        }
    }

    override fun isAllowedToSubscribeToEvents(userIdentity: UserIdentity): Boolean {
        return true
    }

    private fun hasRole(role: Roles, userIdentity: UserIdentity): Boolean {
        return UserView.get(userIdentity)?.properties?.roles?.contains(role) ?: false
    }

}
