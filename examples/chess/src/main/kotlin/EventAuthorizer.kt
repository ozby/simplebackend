import com.prettybyte.simplebackend.lib.*
import com.prettybyte.simplebackend.lib.AuthorizationRuleResult.*
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import statemachines.GameState

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

}

fun `A user can read games where she is a player`(userIdentity: UserIdentity, model: Model<out ModelProperties>, views: Views): AuthorizationRuleResult {
    return when (model.properties) {
        is GameProperties -> if (isPlayerInGame(userIdentity, model.id, views)) allow else noOpinion
        else -> noOpinion
    }
}

fun `Black victories cannot be read`(userIdentity: UserIdentity, model: Model<out ModelProperties>, views: Views): AuthorizationRuleResult {
    if (model.properties !is GameProperties) {
        return noOpinion
    }
    return if (GameState.valueOf(model.state) == GameState.`Black victory`) deny else noOpinion
}

fun `A user can perform actions in a game where she is a player`(userIdentity: UserIdentity, event: IEvent, views: Views): AuthorizationRuleResult {
    return when (event) {
        is MakeMove, is ProposeDraw, is AcceptDraw, is DeclineDraw, is Resign -> if (isPlayerInGame(userIdentity, event.modelId!!, views)) allow else noOpinion
        else -> noOpinion
    }
}

fun `A user can be created`(userIdentity: UserIdentity, event: IEvent, views: Views): AuthorizationRuleResult {
    if (event !is CreateUser) {
        return noOpinion
    }
    return allow
}

fun `A user can create a game`(userIdentity: UserIdentity, event: IEvent, views: Views): AuthorizationRuleResult {
    if (event !is CreateGame) {
        return noOpinion
    }
    return allow
}


fun isPlayerInGame(userIdentity: UserIdentity, gameId: String, views: Views): Boolean {
    val user = views.user.getWithoutAuthorization(userIdentity) ?: return false
    val game = views.game.getWithoutAuthorization(gameId) ?: return false
    return (user.id == game.properties.whitePlayerId || user.id == game.properties.blackPlayerId)
}
