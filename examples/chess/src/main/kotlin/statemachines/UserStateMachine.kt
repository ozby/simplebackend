package statemachines

import CreateUserParams
import Event
import EventAuthorizer.Roles.player
import EventAuthorizer.Roles.viewer
import UserProperties
import com.prettybyte.simplebackend.lib.BlockedByGuard
import com.prettybyte.simplebackend.lib.EventParams
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.UserIdentity
import com.prettybyte.simplebackend.lib.statemachine.StateMachine
import com.prettybyte.simplebackend.lib.statemachine.stateMachine
import statemachines.UserStates.active
import systemUserIdentity
import views.UserView

const val createUser = "CreateUser"
const val deleteUser = "DeleteUser"

enum class UserStates {
    active,
    deleted,
}

fun userStateMachine(): StateMachine<UserProperties, Event, UserStates> =
    stateMachine {
        voidState {
            transition(triggeredByEvent = createUser, targetState = active) {
                guard(::createdByCorrectIdentity)
                guard(::userNotAlreadyCreated)
                effectCreateModel(::newUser)
            }
        }

        state(active) {
            enterAction(::sendWelcomeEmail)
            transition(triggeredByEvent = deleteUser, targetState = UserStates.deleted) {
                // effectDeleteModel()
            }
        }

    }

fun userNotAlreadyCreated(model: Model<UserProperties>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
    if (UserView.getWithoutAuthorization(userIdentity) != null) {
        return BlockedByGuard("User already exist")
    }
    return null
}

fun createdByCorrectIdentity(model: Model<UserProperties>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
    if (userIdentity == systemUserIdentity) {
        return null
    }
    if ((event.getParams() as CreateUserParams).userIdentityId != userIdentity.id) {
        return BlockedByGuard("You must set userIdentityId to the same value as in your JWT")
    }
    return null
}

fun newUser(eventParams: EventParams): UserProperties {
    eventParams as CreateUserParams
    return UserProperties(
        userIdentityId = eventParams.userIdentityId,
        firstName = eventParams.firstName,
        lastName = eventParams.lastName,
        roles = setOf(viewer, player)
    )
}

fun sendWelcomeEmail(model: Model<UserProperties>?, event: Event): Unit {
    println("Sending welcome email (not implemented)")
}
