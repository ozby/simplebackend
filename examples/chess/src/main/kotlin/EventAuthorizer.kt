import com.prettybyte.simplebackend.lib.*
import com.prettybyte.simplebackend.lib.AuthorizationRuleResult.*
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import statemachines.GameState
import views.GameView
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

    override fun isAllowedToSubscribeToEvents(userIdentity: UserIdentity): Boolean {
        return true
    }

    private fun hasRole(role: Roles, userIdentity: UserIdentity): Boolean {
        return UserView.getWithoutAuthorization(userIdentity)?.properties?.roles?.contains(role) ?: false
    }

}

fun `A user can read games where she is a player`(userIdentity: UserIdentity, model: Model<out ModelProperties>): AuthorizationRuleResult {
    return when (model.properties) {
        is GameProperties -> if (isPlayerInGame(userIdentity, model.id)) allow else noOpinion
        else -> noOpinion
    }
}

fun `Black victories cannot be read`(userIdentity: UserIdentity, model: Model<out ModelProperties>): AuthorizationRuleResult {
    if (model.properties !is GameProperties) {
        return noOpinion
    }
    return if (GameState.valueOf(model.state) == GameState.`Black victory`) deny else noOpinion
}

fun `A user can perform actions in a game where she is a player`(userIdentity: UserIdentity, event: IEvent): AuthorizationRuleResult {
    return when (event) {
        is MakeMove, is ProposeDraw, is AcceptDraw, is DeclineDraw, is Resign -> if (isPlayerInGame(userIdentity, event.modelId!!)) allow else noOpinion
        else -> noOpinion
    }
}

fun `A user can create a game`(userIdentity: UserIdentity, event: IEvent): AuthorizationRuleResult {
    if (event !is CreateGame) {
        return noOpinion
    }
    return allow
}


fun isPlayerInGame(userIdentity: UserIdentity, gameId: String): Boolean {
    val user = UserView.getWithoutAuthorization(userIdentity) ?: return false
    val game = GameView.getWithoutAuthorization(gameId) ?: return false
    return (user.id == game.properties.whitePlayerId || user.id == game.properties.blackPlayerId)
}
