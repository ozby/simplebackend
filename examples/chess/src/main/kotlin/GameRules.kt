import Piece.*
import com.prettybyte.simplebackend.lib.*
import statemachines.GameStates
import statemachines.GameStates.*
import views.UserView

const val white = true
const val black = false

object GameRules {

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
        if (model.state == waitingForBlack.name) {
            return null
        }
        val from = params.from
        val to = params.to
        if (!isSquareOnBoard(from) || !isSquareOnBoard(to)) {
            return BlockedByGuard("Not a valid square")
        }
        val validMoves = calculateAllValidMoves(model);
        return if (validMoves.contains(Pair(from, to))) null else BlockedByGuard("Illegal move")
    }

    private fun calculateAllValidMoves(model: Model<GameProperties>): Set<Pair<String, String>> {
        val state = GameStates.valueOf(model.state)
        if (state == blackVictory || state == whiteVictory) {
            return emptySet()
        }
        val pieces = model.properties.pieces
        val isWhite = state == waitingForWhite
        val validMoves = mutableSetOf<Pair<String, String>>()
        val board = Board(pieces)
        for (x in 0..7) {
            for (y in 0..7) {
                val piece = board.getPieceAt(x, y) ?: continue
                if ((piece.second == Color.white && state == waitingForWhite) || (piece.second == Color.black && state == waitingForBlack)) {
                    validMoves.addAll(getValidMovesForPiece(board, x, y))
                }
            }
        }
        return validMoves   // TODO: should remove any move that leaves player with a threatened king
    }

    private fun getValidMovesForPiece(board: Board, x: Int, y: Int): Set<Pair<String, String>> {
        val piece = board.getPieceAt(x, y) ?: return emptySet()
        return when (piece.first) {
            pawn -> getValidMovesForPawn(board, x, y, piece.second)
            king -> getValidMovesForKing(board, x, y, piece.second)
            rook -> getValidMovesForRook(board, x, y, piece.second)
            queen -> emptySet()
            knight -> emptySet()
            bishop -> emptySet()
        }
    }

    private fun getValidMovesForPawn(board: Board, x: Int, y: Int, color: Color): Set<Pair<String, String>> {
        val validMoves = mutableSetOf<Pair<String, String>>()
        val from = Board.getSquareName(x, y)
        if (y == 7) {
            return emptySet()
        }
        if (board.getPieceAt(x, y + 1) == null) {
            validMoves.add(Pair(from, Board.getSquareName(x, y + 1)))
        }
        val pieceForwardRight = board.getPieceAt(x + 1, y + 1)
        if (x < 7 && pieceForwardRight != null && pieceForwardRight.second != color) {
            validMoves.add(Pair(from, Board.getSquareName(x + 1, y + 1)))
        }
        val pieceForwardLeft = board.getPieceAt(x - 1, y + 1)
        if (x > 1 && pieceForwardLeft != null && pieceForwardLeft.second != color) {
            validMoves.add(Pair(from, Board.getSquareName(x - 1, y + 1)))
        }
        // TODO: en passant
        return validMoves
    }

    private fun getValidMovesForKing(board: Board, x: Int, y: Int, color: Color): Set<Pair<String, String>> {
        val validMoves = mutableSetOf<Pair<String, String>>()
        val from = Board.getSquareName(x, y)
        val toSquares = setOf(
            Pair(x, y + 1), Pair(x + 1, y + 1), Pair(x + 1, y), Pair(x + 1, y - 1), Pair(x, y - 1), Pair(x - 1, y - 1),
            Pair(x - 1, y), Pair(x - 1, y + 1)
        )
        val squaresOnBoard = toSquares.filter { it.first in 0..7 && it.second in 0..7 }
        squaresOnBoard.forEach {
            val pieceAtSquare = board.getPieceAt(it.first, it.second)
            if (pieceAtSquare == null || pieceAtSquare.second != color) {
                validMoves.add(Pair(from, Board.getSquareName(it.first, it.second)))
            }
        }
        // TODO: castling
        return validMoves
    }

    private fun getValidMovesForRook(board: Board, x: Int, y: Int, color: Color): Set<Pair<String, String>> {
        val from = Board.getSquareName(x, y)
        val squares = mutableSetOf<SquareCoordinates>()
        squares.addAll(getSquaresForward(board, x, y, color))


        return squares.map { Pair(from, Board.getSquareName(it)) }.toSet()
    }

    private fun getSquaresForward(board: Board, x: Int, y: Int, color: Color): Set<SquareCoordinates> {
        val squares = mutableSetOf<SquareCoordinates>()
        var squareWasAdded: Boolean
        var yToCheck = y + 1
        do {
            val piece = board.getPieceAt(x, yToCheck)
            if (yToCheck < 8 && (piece == null || piece.second != color)) {
                squares.add(Pair(x, yToCheck))
                squareWasAdded = true
                yToCheck++
            } else {
                squareWasAdded = false
            }
        } while (squareWasAdded)
        return squares
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

}

private class Board(val pieces: List<String>) {

    fun getPieceAt(x: Int, y: Int): Pair<Piece, Color>? {
        if (!(x in 0..7) || !(y in 0..7)) {
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

    companion object {
        fun getSquareName(x: Int, y: Int): String {
            return "abcdefgh"[x] + (y + 1).toString()
        }

        fun getSquareName(sq: SquareCoordinates): String = getSquareName(sq.first, sq.second)
    }
}

enum class Piece(p: String) {
    pawn("p"), rook("r"), knight("k"), bishop("b"), queen("q"), king("k")
}

enum class Color(c: String) {
    white("w"), black("b")
}

typealias SquareCoordinates = Pair<Int, Int>
