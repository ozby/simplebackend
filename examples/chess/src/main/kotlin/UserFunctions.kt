import arrow.core.Either
import com.prettybyte.simplebackend.lib.BlockedByGuard
import com.prettybyte.simplebackend.lib.EventParams
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.UserIdentity

fun hasNoOngoingGames(model: Model<UserProperties>?, event: Event, userIdentity: UserIdentity, views: Views): BlockedByGuard? {
    return when (val result = views.game.getAllWhereUserIsPlayer(views.user.getActiveUserWithoutAuthorization(userIdentity)?.id ?: "").auth(userIdentity)) {
        is Either.Left -> BlockedByGuard(result.a.toString())
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

/*fun userNotAlreadyCreated(model: Model<UserProperties>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
    if (UserView.getWithoutAuthorization(userIdentity) != null) {
        return BlockedByGuard("User already exist")
    }
    return null
}
 */

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
        victories = 0,
        defeats = 0,
        draws = 0,
    )
}

fun sendWelcomeEmail(model: Model<UserProperties>?, event: Event): Unit {
    println("Sending welcome email (not implemented)")
}
