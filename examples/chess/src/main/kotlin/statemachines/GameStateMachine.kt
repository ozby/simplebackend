package statemachines

import Event
import GameProperties
import GameRules.isBlackCheckMate
import GameRules.isCorrectPlayer
import GameRules.isWhiteCheckMate
import GameRules.makeTurn
import GameRules.newGame
import GameRules.validateMove
import canOnlyCreateGameWhereIAmAPlayer
import com.prettybyte.simplebackend.lib.statemachine.StateMachine
import com.prettybyte.simplebackend.lib.statemachine.stateMachine
import createGame
import makeMove
import statemachines.GameStates.*

enum class GameStates {
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
