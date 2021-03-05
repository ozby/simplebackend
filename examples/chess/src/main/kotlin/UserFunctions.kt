import EventAuthorizer.Roles.player
import EventAuthorizer.Roles.viewer
import arrow.core.Either
import com.prettybyte.simplebackend.lib.*
import views.GameView
import views.UserView

fun hasNoOngoingGames(model: Model<UserProperties>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
    return when (val result = GameView.getAllWhereUserIsPlayer(getActiveUser(userIdentity)?.id ?: "", AllowAll()).get()) {
        is Either.Left -> BlockedByGuard(result.a.message)
        is Either.Right -> if (result.b!!.isEmpty()) null else BlockedByGuard("Cannot delete user since he/she has ongoing games")
    }
}

fun updateRating(model: Model<UserProperties>, eventParams: EventParams): UserProperties {
    eventParams as UpdateUsersRatingParams
    return when (eventParams.result) {
        "victory" -> model.properties.copy(victories = model.properties.victories + 1)
        "defeat" -> model.properties.copy(defeats = model.properties.defeats + 1)
        "draw" -> model.properties.copy(draws = model.properties.draws + 1)
        else -> throw Exception()
    }
}

fun userNotAlreadyCreated(model: Model<UserProperties>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
    if (UserView.getWithoutAuthorization(userIdentity) != null) {
        return BlockedByGuard("User already exist")
    }
    return null
}

fun createdByCorrectIdentity(model: Model<UserProperties>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
    if (userIdentity.isSystem()) {
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
        roles = setOf(viewer, player),
        victories = 0,
        defeats = 0,
        draws = 0,
    )
}

fun sendWelcomeEmail(model: Model<UserProperties>?, event: Event): Unit {
    println("Sending welcome email (not implemented)")
}
