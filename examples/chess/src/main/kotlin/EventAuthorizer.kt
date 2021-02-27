import EventAuthorizer.Roles.player
import com.prettybyte.simplebackend.lib.IEventAuthorizer
import com.prettybyte.simplebackend.lib.Problem
import com.prettybyte.simplebackend.lib.UserIdentity
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import views.UserView

object EventAuthorizer : IEventAuthorizer<Event> {

    // Available roles:
    enum class Roles {
        player,
        viewer,
    }

    override fun onExchangeJWT(jws: Jws<Claims>): Problem? {
        return null
    }

    override fun isAllowedToCreateEvent(userIdentity: UserIdentity, event: Event): Boolean {
        return when (event) {
            is CreateGame -> hasRole(player, userIdentity)
            is MakeMove -> hasRole(player, userIdentity)
            is CreateUser -> event.getParams().userIdentityId == userIdentity.id
            is SelectPiece -> hasRole(player, userIdentity)
            is Resign -> hasRole(player, userIdentity)
            is ProposeDraw -> hasRole(player, userIdentity)
            is AcceptDraw -> hasRole(player, userIdentity)
            is DeclineDraw -> hasRole(player, userIdentity)
        }
    }

    override fun isAllowedToSubscribeToEvents(userIdentity: UserIdentity): Boolean {
        return true
    }

    private fun hasRole(role: Roles, userIdentity: UserIdentity): Boolean {
        return UserView.getWithoutAuthorization(userIdentity)?.properties?.roles?.contains(role) ?: false
    }

}
