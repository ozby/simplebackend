import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.*
import kotlinx.coroutines.delay
import statemachines.GameStates
import views.UserView

const val white = true
const val black = false

object GameRules {

    lateinit var simpleBackend: SimpleBackend<Event>

    fun isCorrectPlayer(game: Model<GameProperties>?, event: IEvent, userIdentity: UserIdentity): BlockedByGuard? {
        if (userIdentity.id == computerPlayer) {
            return null
        }
        val user = getActiveUser(userIdentity) ?: return BlockedByGuard("Can't find user")
        if (game == null) {
            return BlockedByGuard("isCorrectPlayer: game was null")
        }
        return when (game.state) {
            GameStates.waitingForBlack.name -> if (game.properties.blackPlayerId != user.id) BlockedByGuard("Not your turn") else null
            GameStates.waitingForWhite.name -> if (game.properties.whitePlayerId != user.id) BlockedByGuard("Not your turn") else null
            else -> null
        }
    }

    fun validateMove(model: Model<GameProperties>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
        val params = (event as MakeMove).getParams()
        if (model == null) {
            return BlockedByGuard("Game missing")
        }

        val game = model.properties
        val from = params.from
        val to = params.to
        if (!isSquareOnBoard(from) || !isSquareOnBoard(to)) {
            return BlockedByGuard("Not a valid square")
        }

        val pieceToMove = game.pieces[squareToIndex(from)]

        if (isPlayerMovingOtherPlayersPiece(userIdentity, pieceToMove, game)) {
            return BlockedByGuard("You can only move your own pieces")
        }

        val legal = when (pieceToMove) {
            "wp" -> isLegalPawnMove(white, from, to, game.pieces)
            else -> true
        }
        return if (legal) null else BlockedByGuard("Illegal move")
    }

    private fun isLegalPawnMove(isWhite: Boolean, from: String, to: String, pieces: List<String>): Boolean {
        return squareToIndex(to) == squareToIndex(from) + 8 && pieces[squareToIndex(to)].isEmpty()
    }

    private fun isSquareOnBoard(from: String): Boolean {
        val column = from[0]
        val row = from.substring(1).toInt()
        return "abcdefgh".contains(column) && row > 0 && row < 8
    }

    private fun isPlayerMovingOtherPlayersPiece(userIdentity: UserIdentity, pieceToMove: String, game: GameProperties): Boolean {
        if (userIdentity.id == computerPlayer) {
            return false
        }
        val user = UserView.get(userIdentity) ?: return true
        return pieceToMove.startsWith("w") && game.whitePlayerId != user.id ||
                pieceToMove.startsWith("b") && game.blackPlayerId != user.id
    }

    fun squareToIndex(from: String): Int {
        val column = from.first()
        val row = from.substring(1).toInt()
        return (row - 1) * 8 + "abcdefgh".indexOfFirst { it == column }
    }

    fun newGame(params: EventParams): GameProperties {
        params as CreateGameParams
        return GameProperties(
            pieces = setupPieces(),
            whitePlayerId = params.whitePlayerUserId,
            blackPlayerId = params.blackPlayerUserId
        )
    }

    fun makeTurn(model: Model<GameProperties>, params: EventParams): GameProperties {
        params as MakeMoveParams
        model.properties

        val from = params.from
        val to = params.to
        val oldBoard = model.properties.pieces
        val newBoard = oldBoard.toMutableList()
        newBoard[squareToIndex(to)] = oldBoard[squareToIndex(from)]
        newBoard[squareToIndex(from)] = ""
        if (params.from == "e1" && params.to == "g1") {
            newBoard[squareToIndex("f1")] = oldBoard[squareToIndex("h1")]
            newBoard[squareToIndex("h1")] = ""
        }
        return GameProperties(
            pieces = newBoard,
            whitePlayerId = model.properties.whitePlayerId,
            blackPlayerId = model.properties.blackPlayerId
        )
    }

    fun isWhiteCheckMate(game: GameProperties): Boolean {
        return false
    }

    fun isBlackCheckMate(game: GameProperties): Boolean {
        return game.pieces[16].isNotEmpty()
    }

    private fun setupPieces(): List<String> =
        listOf(
            "wr",
            "wn",
            "wb",
            "wq",
            "wk",
            "wb",
            "wn",
            "wr",
            "wp",
            "wp",
            "wp",
            "wp",
            "wp",
            "wp",
            "wp",
            "wp",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "bp",
            "bp",
            "bp",
            "bp",
            "bp",
            "bp",
            "bp",
            "bp",
            "br",
            "bn",
            "bb",
            "bq",
            "bk",
            "bb",
            "bn",
            "br"
        )

    suspend fun makeComputerMove(model: Model<GameProperties>) {
        val game = model.properties
        if (model.state != GameStates.waitingForBlack.name) {
            return
        }
        delay(2000)
        if (game.pieces[squareToIndex("b7")].isNotEmpty()) {
            val p = "{\"from\": \"b7\", \"to\": \"b6\"}"
            val e = MakeMove(gameId = model.id, params = p, userIdentityId = computerPlayer)
            simpleBackend.processEvent(e, eventParametersJson = p, userIdentity = UserIdentity(computerPlayer))    // TODO
        } else if (game.pieces[squareToIndex("e7")].isNotEmpty()) {
            val p = "{\"from\": \"e7\", \"to\": \"e6\"}"
            val e = MakeMove(gameId = model.id, params = p, userIdentityId = computerPlayer)
            simpleBackend.processEvent(e, eventParametersJson = p, userIdentity = UserIdentity(computerPlayer))    // TODO
        } else if (game.pieces[squareToIndex("h7")].isNotEmpty()) {
            val p = "{\"from\": \"h7\", \"to\": \"h6\"}"
            val e = MakeMove(gameId = model.id, params = p, userIdentityId = computerPlayer)
            simpleBackend.processEvent(e, eventParametersJson = p, userIdentity = UserIdentity(computerPlayer))    // TODO
        } else if (game.pieces[squareToIndex("a7")].isNotEmpty()) {
            val p = "{\"from\": \"a7\", \"to\": \"a6\"}"
            val e = MakeMove(gameId = model.id, params = p, userIdentityId = computerPlayer)
            simpleBackend.processEvent(e, eventParametersJson = p, userIdentity = UserIdentity(computerPlayer))    // TODO
        }
    }
}
