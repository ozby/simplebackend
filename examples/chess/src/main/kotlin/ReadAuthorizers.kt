import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import com.prettybyte.simplebackend.lib.Auth
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.Problem
import com.prettybyte.simplebackend.lib.UserIdentity
import views.UserView

class AllowIfIsPlayer(val userIdentity: UserIdentity) : Auth<Model<GameProperties>>() {

    override fun get(): Either<Problem, Model<GameProperties>?> {
        val user = UserView.getWithoutAuthorization(userIdentity) ?: return Left(Problem.notFound())
        return if (theValue?.properties?.whitePlayerId == user.id || theValue?.properties?.blackPlayerId == user.id) {
            Right(theValue)
        } else {
            Left(Problem.unauthorized("You are not a player in this game"))
        }
    }

}

class RemoveIfIsNotPlayer(val userIdentity: UserIdentity) : Auth<Model<GameProperties>>() {

    override fun get(): Either<Problem, Model<GameProperties>?> {
        val user = UserView.getWithoutAuthorization(userIdentity) ?: return Left(Problem.notFound())
        return if (theValue?.properties?.whitePlayerId == user.id || theValue?.properties?.blackPlayerId == user.id) {
            Right(theValue)
        } else {
            Right(null)
        }
    }

}
