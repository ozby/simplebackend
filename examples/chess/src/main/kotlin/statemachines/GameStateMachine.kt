package statemachines

import Event
import GameProperties
import GameRules.`Automatic draw`
import GameRules.`Black can promote pawn`
import GameRules.`Black is checkmate`
import GameRules.`Event comes from black player`
import GameRules.`Event comes from white player`
import GameRules.`Is promotable piece`
import GameRules.`Validate move`
import GameRules.`White can promote pawn`
import GameRules.`White is checkmate`
import GameRules.makeTurn
import GameRules.newGame
import GameRules.switchPawn
import `Can only create game where I am a player`
import acceptDraw
import com.prettybyte.simplebackend.lib.statemachine.StateMachine
import com.prettybyte.simplebackend.lib.statemachine.stateMachine
import createGame
import declineDraw
import makeMove
import proposeDraw
import resign
import selectPiece
import statemachines.GameState.*

enum class GameState {
    `White turn`,
    `Black turn`,
    `White victory`,
    `Black victory`,
    `White promote pawn`,
    `Black promote pawn`,
    Draw,
    `White has proposed draw`,
    `Black has proposed draw`,
}

fun createGameStateMachine(): StateMachine<GameProperties, Event, GameState> =

    stateMachine {
        voidState {
            transition(triggeredByEvent = createGame, targetState = `White turn`) {
                guard(::`Can only create game where I am a player`)
                effectCreateModel(::newGame)
            }
        }

        state(`White turn`) {
            transition(triggeredByEvent = makeMove, targetState = `Black turn`) {
                guard(::`Event comes from white player`)
                guard(::`Validate move`)
                effectUpdateModel(::makeTurn)
            }
            transition(triggeredIf = ::`Black can promote pawn`, targetState = `Black promote pawn`) {}
            transition(triggeredIf = ::`White is checkmate`, targetState = `Black victory`) {}
            transition(triggeredIf = ::`Automatic draw`, targetState = Draw) {}
            transition(triggeredByEvent = proposeDraw, targetState = `White has proposed draw`) {}
            transition(triggeredByEvent = resign, targetState = `Black victory`) {
                guard(::`Event comes from white player`)
            }
        }

        state(`Black turn`) {
            transition(triggeredByEvent = makeMove, targetState = `White turn`) {
                guard(::`Event comes from black player`)
                guard(::`Validate move`)
                effectUpdateModel(::makeTurn)
            }
            transition(triggeredIf = ::`White can promote pawn`, targetState = `White promote pawn`) {}
            transition(triggeredIf = ::`Black is checkmate`, targetState = `White victory`) {}
            transition(triggeredIf = ::`Automatic draw`, targetState = Draw) {}
            transition(triggeredByEvent = proposeDraw, targetState = `Black has proposed draw`) {}
            transition(triggeredByEvent = resign, targetState = `White victory`) {
                guard(::`Event comes from black player`)
            }
        }

        state(`White promote pawn`) {
            transition(triggeredByEvent = selectPiece, targetState = `Black turn`) {
                guard(::`Event comes from white player`)
                guard(::`Is promotable piece`)
                effectUpdateModel(::switchPawn)
            }
        }

        state(`Black promote pawn`) {
            transition(triggeredByEvent = selectPiece, targetState = `White turn`) {
                guard(::`Event comes from black player`)
                guard(::`Is promotable piece`)
                effectUpdateModel(::switchPawn)
            }
        }

        state(`White victory`) {}

        state(`Black victory`) {}

        state(Draw) {}

        state(`White has proposed draw`) {
            transition(triggeredByEvent = acceptDraw, targetState = Draw) {
                guard(::`Event comes from black player`)
            }
            transition(triggeredByEvent = declineDraw, targetState = `White turn`) {
                guard(::`Event comes from black player`)
            }
        }

        state(`Black has proposed draw`) {
            transition(triggeredByEvent = acceptDraw, targetState = Draw) {
                guard(::`Event comes from white player`)
            }
            transition(triggeredByEvent = declineDraw, targetState = `Black turn`) {
                guard(::`Event comes from white player`)
            }
        }

    }
