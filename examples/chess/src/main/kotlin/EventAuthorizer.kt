import com.prettybyte.simplebackend.lib.*
import com.prettybyte.simplebackend.lib.NegativeAuthorization.deny
import com.prettybyte.simplebackend.lib.NegativeAuthorization.pass
import com.prettybyte.simplebackend.lib.PositiveAuthorization.allow
import com.prettybyte.simplebackend.lib.PositiveAuthorization.noOpinion
import statemachines.GameState

fun `A user can read games where she is a player`(userIdentity: UserIdentity, model: Model<out ModelProperties>, views: Views): PositiveAuthorization {
    return when (model.properties) {
        is GameProperties -> if (isPlayerInGame(userIdentity, model.id, views)) allow else noOpinion
        else -> noOpinion
    }
}

fun `Black victories cannot be read`(userIdentity: UserIdentity, model: Model<out ModelProperties>, views: Views): NegativeAuthorization {
    if (model.properties !is GameProperties) {
        return pass
    }
    return if (GameState.valueOf(model.state) == GameState.`Black victory`) deny else pass
}

fun `A user can perform actions in a game where she is a player`(userIdentity: UserIdentity, event: Event, views: Views): PositiveAuthorization {
    return when (event) {
        is MakeMove, is ProposeDraw, is AcceptDraw, is DeclineDraw, is Resign -> if (isPlayerInGame(userIdentity, event.modelId!!, views)) allow else noOpinion
        else -> noOpinion
    }
}

fun `Only admins can delete games`(userIdentity: UserIdentity, event: Event, views: Views): NegativeAuthorization {
    // not implemented
    return pass
}

fun `A user can be created`(userIdentity: UserIdentity, event: Event, views: Views): PositiveAuthorization {
    if (event !is CreateUser) {
        return noOpinion
    }
    return allow
}

fun `A user can create a game`(userIdentity: UserIdentity, event: Event, views: Views): PositiveAuthorization {
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
