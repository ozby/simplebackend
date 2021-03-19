package statemachines

import Event
import GameProperties
import Views
import `Automatic draw`
import `Black can promote pawn`
import `Black is checkmate`
import `Can only create game where I am a player`
import `Event comes from black player`
import `Event comes from white player`
import `Is promotable piece`
import `Update Users Ratings`
import `Validate move`
import `White can promote pawn`
import `White is checkmate`
import acceptDraw
import com.prettybyte.simplebackend.lib.statemachine.StateMachine
import com.prettybyte.simplebackend.lib.statemachine.stateMachine
import createGame
import declineDraw
import makeMove
import makeTurn
import newGame
import promotePawn
import proposeDraw
import resign
import statemachines.GameState.*
import switchPawn

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

fun createGameStateMachine(): StateMachine<GameProperties, Event, GameState, Views> =

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
            transition(triggeredByEvent = promotePawn, targetState = `Black turn`) {
                guard(::`Event comes from white player`)
                guard(::`Is promotable piece`)
                effectUpdateModel(::switchPawn)
            }
        }

        state(`Black promote pawn`) {
            transition(triggeredByEvent = promotePawn, targetState = `White turn`) {
                guard(::`Event comes from black player`)
                guard(::`Is promotable piece`)
                effectUpdateModel(::switchPawn)
            }
        }

        state(`White victory`) {
            onEnter {
                effectCreateEvent(::`Update Users Ratings`)
            }
        }

        state(`Black victory`) {
            onEnter {
                effectCreateEvent(::`Update Users Ratings`)
            }
        }

        state(Draw) {
            onEnter {
                effectCreateEvent(::`Update Users Ratings`)
            }
        }

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
