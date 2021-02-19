package statemachines

import CreateGameParams
import Event
import GameProperties
import GameRules.isBlackCheckMate
import GameRules.isCorrectPlayer
import GameRules.isWhiteCheckMate
import GameRules.makeTurn
import GameRules.newGame
import GameRules.validateMove
import com.prettybyte.simplebackend.lib.BlockedByGuard
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.UserIdentity
import com.prettybyte.simplebackend.lib.statemachine.StateMachine
import com.prettybyte.simplebackend.lib.statemachine.stateMachine
import createGame
import makeMove
import statemachines.GameStates.*
import views.UserView

enum class GameStates {     // om jag får problem med denna så kan jag undersöka om den verkligen måste in som typ-parameter i statemachine
    waitingForWhite,
    waitingForBlack,
    whiteVictory,
    blackVictory,
}

fun createGameStateMachine(): StateMachine<GameProperties, Event, GameStates> =
    stateMachine {
        initialState {
            transition(triggeredByEvent = createGame, targetState = waitingForWhite) {
                guard(::canOnlyCreateGameWhereIAmAPlayer)
                effectCreateModel(::newGame)
            }
        }

        state(waitingForWhite) {
            transition(triggeredByEvent = makeMove, targetState = waitingForBlack) {
                guard(::isCorrectPlayer)
                guard(::validateMove)
                effectUpdateModel(::makeTurn)
            }

            transition(triggeredIf = ::isWhiteCheckMate, targetState = blackVictory) {}
        }

        state(waitingForBlack) {
            transition(triggeredByEvent = makeMove, targetState = waitingForWhite) {
                guard(::isCorrectPlayer)
                guard(::validateMove)
                effectUpdateModel(::makeTurn)
            }

            transition(triggeredIf = ::isBlackCheckMate, targetState = whiteVictory) {}
        }

        state(whiteVictory) {}

        state(blackVictory) {}
    }

fun canOnlyCreateGameWhereIAmAPlayer(model: Model<GameProperties>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
    val user = UserView.get(userIdentity) ?: return BlockedByGuard("User not found")
    if ((event.getParams() as CreateGameParams).whitePlayerUserId != user.id) {
        return BlockedByGuard("You can only create new games where you are the white player")
    }
    return null
}
