import Piece.*
import arrow.core.Either.Left
import arrow.core.Either.Right
import com.prettybyte.simplebackend.lib.AllowAll
import com.prettybyte.simplebackend.lib.BlockedByGuard
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.UserIdentity
import statemachines.UserStates
import views.UserView

fun parseEvent(eventName: String, modelId: String, params: String, userIdentityId: String): Event {
    return when (eventName) {
        createGame -> CreateGame(modelId, params, userIdentityId)
        makeMove -> MakeMove(modelId, params, userIdentityId)
        createUser -> CreateUser(modelId, params, userIdentityId)
        selectPiece -> PromotePawn(modelId, params, userIdentityId)
        resign -> Resign(modelId, params, userIdentityId)
        proposeDraw -> ProposeDraw(modelId, params, userIdentityId)
        acceptDraw -> AcceptDraw(modelId, params, userIdentityId)
        declineDraw -> DeclineDraw(modelId, params, userIdentityId)
        else -> throw RuntimeException("Could not parse event with name '$eventName'")
    }
}

/**
 * Returns user if state == active. Else null.
 */
fun getActiveUser(userIdentity: UserIdentity): Model<UserProperties>? {
    return when (val user = UserView.getByUserIdentityId(userIdentity.id, AllowAll()).get()) {
        is Left -> null
        is Right -> if (user.b?.state?.equals(UserStates.active.name) == true) {
            return user.b
        } else {
            return null
        }
    }
}

fun `Can only create game where I am a player`(model: Model<GameProperties>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
    val user = UserView.getWithoutAuthorization(userIdentity) ?: return BlockedByGuard("User not found")
    if ((event.getParams() as CreateGameParams).whitePlayerUserId != user.id) {
        return BlockedByGuard("You can only create new games where you are the white player")
    }
    return null
}

fun `Update Users Ratings`(model: Model<GameProperties>): Event {
    val params = """{"draw": true}"""
    return UpdateUsersRating(userId = model.properties.whitePlayerId, params = params, UserIdentity.system().id)
}


const val computerPlayer = "Computer player"

val systemUserIdentity = UserIdentity("perhaps a secret system user")

class Board(val pieces: List<String>) {

    fun getPieceAt(x: Int, y: Int): Pair<Piece, Color>? {
        if (x !in 0..7 || y !in 0..7) {
            return null
        }
        val piece = pieces[y * 8 + x]
        if (piece.isEmpty()) {
            return null
        }
        val pieceType = piece.substring(1, 2)
        val pieceColor = piece.substring(0, 1)
        return Pair(getPieceFromAbbreviation(pieceType), if (pieceColor == "w") Color.white else Color.black)
    }

    companion object {
        fun getSquareName(x: Int, y: Int): String {
            return "abcdefgh"[x] + (y + 1).toString()
        }

        fun getSquareName(sq: SquareCoordinates): String = getSquareName(sq.first, sq.second)

        fun isSquareWhite(squareIndex: Int): Boolean {
            val row = squareIndex / 8
            val column = squareIndex % 8
            return if (row % 2 == 0) column % 2 != 0 else column % 2 == 0
        }

        fun isSquareOnBoard(from: String): Boolean {
            val column = from[0]
            val row = from.substring(1).toInt()
            return "abcdefgh".contains(column) && row in 1..8
        }

        fun squareToIndex(from: String): Int {
            val column = from.first()
            val row = from.substring(1).toInt()
            return (row - 1) * 8 + "abcdefgh".indexOfFirst { it == column }
        }

        private fun getPieceFromAbbreviation(pieceType: String): Piece {
            return when (pieceType) {
                "p" -> pawn
                "r" -> rook
                "b" -> bishop
                "q" -> queen
                "k" -> king
                "n" -> knight
                else -> throw RuntimeException()
            }


        }
    }
}

enum class Piece(p: String) {
    pawn("p"), rook("r"), knight("k"), bishop("b"), queen("q"), king("k")
}

enum class Color(c: String) {
    white("w"), black("b")
}

typealias SquareCoordinates = Pair<Int, Int>
