import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import com.prettybyte.simplebackend.lib.IReadAuthenticator
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.Problem
import com.prettybyte.simplebackend.lib.UserIdentity
import views.UserView

class AuthorizeIfIsPlayer(val userIdentity: UserIdentity) : IReadAuthenticator<GameProperties> {

    override fun authorize(model: Model<GameProperties>?): Either<Problem, Model<GameProperties>?> {
        val user = UserView.getWithoutAuthorization(userIdentity) ?: return Left(Problem.notFound())
        return if (model?.properties?.whitePlayerId == user.id || model?.properties?.blackPlayerId == user.id) {
            Right(model)
        } else {
            Left(Problem.unauthorized("You are not a player in this game"))
        }
    }

}

class RemoveIfIsNotPlayer(val userIdentity: UserIdentity) : IReadAuthenticator<GameProperties> {

    override fun authorize(model: Model<GameProperties>?): Either<Problem, Model<GameProperties>?> {
        val user = UserView.getWithoutAuthorization(userIdentity) ?: return Left(Problem.notFound())
        return if (model?.properties?.whitePlayerId == user.id || model?.properties?.blackPlayerId == user.id) {
            Right(model)
        } else {
            Right(null)
        }
    }

}
