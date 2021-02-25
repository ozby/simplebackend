import EventAuthorizer.Roles.editor
import arrow.core.Either
import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.AuthorizeAll
import com.prettybyte.simplebackend.lib.IEventAuthorizer
import com.prettybyte.simplebackend.lib.Problem
import com.prettybyte.simplebackend.lib.UserIdentity
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import views.UserView
import java.util.*

object EventAuthorizer : IEventAuthorizer<Event> {

    // Available roles:
    enum class Roles {
        editor,
        viewer,
    }

    override fun onExchangeJWT(jws: Jws<Claims>): Problem? {
        val subject = jws.body.subject
        when (val either = UserView.getByUserIdentityId(subject, AuthorizeAll())) {
            is Either.Left -> return either.a
        }

        // The user doesn't exist. Should we create a user?
        val email = jws.body["email"].toString()
        if (email.endsWith("@prettybyte.com") && jws.body["email_verified"] == true) {
            val p = "{\"userIdentityId\": \"$subject\", \"firstName\": \"${email}\", \"lastName\": \"\"}"
            SimpleBackend.processEvent(
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

    override fun isAllowedToSubscribeToEvents(userIdentity: UserIdentity): Boolean {
        return true
    }

    private fun hasRole(role: Roles, userIdentity: UserIdentity): Boolean {
        return UserView.getWithoutAuthorization(userIdentity)?.properties?.roles?.contains(role) ?: false
    }

}
