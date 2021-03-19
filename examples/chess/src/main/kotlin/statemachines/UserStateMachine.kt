package statemachines

import Event
import UserProperties
import Views
import com.prettybyte.simplebackend.lib.statemachine.StateMachine
import com.prettybyte.simplebackend.lib.statemachine.stateMachine
import hasNoOngoingGames
import newUser
import statemachines.UserStates.active
import statemachines.UserStates.deleted
import updateRating
import updateUsersRating
import sendWelcomeEmail as sendWelcomeEmail1

const val createUser = "CreateUser"
const val deleteUser = "DeleteUser"

enum class UserStates {
    active,
    deleted,
}

fun userStateMachine(): StateMachine<UserProperties, Event, UserStates, Views> =

    stateMachine {
        voidState {
            transition(triggeredByEvent = createUser, targetState = active) {
                effectCreateModel(::newUser)
            }
        }

        state(active) {
            onEnter {
                action(::sendWelcomeEmail1)
            }
            transition(triggeredByEvent = deleteUser, targetState = deleted) {
                guard(::hasNoOngoingGames)
            }
            onEvent(updateUsersRating) {
                effectUpdateModel(::updateRating)
            }
        }

        state(deleted) {}

    }
