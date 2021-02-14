package statemachines

import Authorizer.Roles.editor
import Authorizer.Roles.viewer
import CreateUserParams
import Event
import User
import com.prettybyte.simplebackend.lib.BlockedByGuard
import com.prettybyte.simplebackend.lib.EventParams
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.UserIdentity
import com.prettybyte.simplebackend.lib.statemachine.StateMachine
import com.prettybyte.simplebackend.lib.statemachine.stateMachine
import views.UserView
import systemUserIdentity

const val createUser = "CreateUser"
const val deleteUser = "DeleteUser"

enum class UserStates {
    active,
    deleted,
}

fun userStateMachine(): StateMachine<User, Event, UserStates> =
    stateMachine {
        initialState {
            transition(triggeredByEvent = createUser, targetState = UserStates.active) {
                guard(::createdByCorrectIdentity)
                guard(::userNotAlreadyCreated)
                effectCreateModel(::newUser)
            }
        }

        state(UserStates.active) {
            enterAction(::sendWelcomeEmail)
            transition(triggeredByEvent = deleteUser, targetState = UserStates.deleted) {
                // effectDeleteModel()
            }
        }

    }

fun userNotAlreadyCreated(model: Model<User>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
    if (UserView.get(userIdentity) != null) {
        return BlockedByGuard("User already exist")
    }
    return null
}

fun createdByCorrectIdentity(model: Model<User>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
    if (userIdentity == systemUserIdentity) {
        return null
    }
    if ((event.getParams() as CreateUserParams).userIdentityId != userIdentity.id) {
        return BlockedByGuard("You must set userIdentityId to the same value as in your JWT")
    }
    return null
}

fun newUser(eventParams: EventParams): User {
    eventParams as CreateUserParams
    return User(userIdentityId = eventParams.userIdentityId, firstName = eventParams.firstName, lastName = eventParams.lastName, roles = setOf(viewer, editor))
}

fun sendWelcomeEmail(model: Model<User>?, event: Event): Unit {
    println("Sending welcome email (not implemented)")
}
