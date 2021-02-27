package statemachines

import Event
import GameProperties
import GameRules.blackHasPawnOnEdge
import GameRules.drawWasRequiredDuringBlackTurn
import GameRules.drawWasRequiredDuringWhiteTurn
import GameRules.isAutomaticDraw
import GameRules.isBlackCheckMate
import GameRules.isCorrectPlayer
import GameRules.isSwitchablePiece
import GameRules.isWhiteCheckMate
import GameRules.makeTurn
import GameRules.newGame
import GameRules.switchPawn
import GameRules.validateMove
import GameRules.whiteHasPawnOnEdge
import acceptDraw
import canOnlyCreateGameWhereIAmAPlayer
import com.prettybyte.simplebackend.lib.statemachine.StateMachine
import com.prettybyte.simplebackend.lib.statemachine.stateMachine
import createGame
import declineDraw
import makeMove
import proposeDraw
import resign
import selectPiece
import statemachines.GameStates.*

enum class GameStates {
    `White turn`,
    `Black turn`,
    `White victory`,
    `Black victory`,
    `White promote pawn`,
    `Black promote pawn`,
    Draw,
    `Draw has been proposed`,
}

fun createGameStateMachine(): StateMachine<GameProperties, Event, GameStates> =
    stateMachine {
        voidState {
            transition(triggeredByEvent = createGame, targetState = `White turn`) {
                guard(::canOnlyCreateGameWhereIAmAPlayer)
                effectCreateModel(::newGame)
            }
        }

        state(`White turn`) {
            transition(triggeredByEvent = makeMove, targetState = `Black turn`) {
                guard(::isCorrectPlayer)
                guard(::validateMove)
                effectUpdateModel(::makeTurn)
            }
            transition(triggeredIf = ::blackHasPawnOnEdge, targetState = `Black promote pawn`) {}
            transition(triggeredIf = ::isWhiteCheckMate, targetState = `Black victory`) {}
            transition(triggeredIf = ::isAutomaticDraw, targetState = Draw) {}
            transition(triggeredByEvent = proposeDraw, targetState = `Draw has been proposed`) {}
            transition(triggeredByEvent = resign, targetState = `Black victory`) {
                guard(::isCorrectPlayer)
            }
        }

        state(`Black turn`) {
            transition(triggeredByEvent = makeMove, targetState = `White turn`) {
                guard(::isCorrectPlayer)
                guard(::validateMove)
                effectUpdateModel(::makeTurn)
            }
            transition(triggeredIf = ::whiteHasPawnOnEdge, targetState = `White promote pawn`) {}
            transition(triggeredIf = ::isBlackCheckMate, targetState = `White victory`) {}
            transition(triggeredIf = ::isAutomaticDraw, targetState = Draw) {}
            transition(triggeredByEvent = proposeDraw, targetState = `Draw has been proposed`) {}
            transition(triggeredByEvent = resign, targetState = `White victory`) {
                guard(::isCorrectPlayer)
            }
        }

        state(`White promote pawn`) {
            transition(triggeredByEvent = selectPiece, targetState = `Black turn`) {
                guard(::isCorrectPlayer)
                guard(::isSwitchablePiece)
                effectUpdateModel(::switchPawn)
            }
        }

        state(`Black promote pawn`) {
            transition(triggeredByEvent = selectPiece, targetState = `White turn`) {
                guard(::isCorrectPlayer)
                guard(::isSwitchablePiece)
                effectUpdateModel(::switchPawn)
            }
        }

        state(`White victory`) {}

        state(`Black victory`) {}

        state(Draw) {}

        state(`Draw has been proposed`) {
            transition(triggeredByEvent = acceptDraw, targetState = Draw) {
                guard(::isCorrectPlayer)
            }
            transition(triggeredByEvent = declineDraw, targetState = `White turn`) {
                guard(::isCorrectPlayer)
                guard(::drawWasRequiredDuringWhiteTurn)
            }
            transition(triggeredByEvent = declineDraw, targetState = `Black turn`) {
                guard(::isCorrectPlayer)
                guard(::drawWasRequiredDuringBlackTurn)
            }

        }
    }
