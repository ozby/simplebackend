import Piece.*
import com.prettybyte.simplebackend.lib.*
import statemachines.GameStates
import statemachines.GameStates.*

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
            `Black turn`.name -> if (game.properties.blackPlayerId != user.id) BlockedByGuard("Not your turn") else null
            `White turn`.name -> if (game.properties.whitePlayerId != user.id) BlockedByGuard("Not your turn") else null
            else -> null
        }
    }

    fun validateMove(model: Model<GameProperties>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
        val params = (event as MakeMove).getParams()
        if (model == null) {
            return BlockedByGuard("Game missing")
        }
        if (model.state == `Black turn`.name) {
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

    fun squareToIndex(from: String): Int {
        val column = from.first()
        val row = from.substring(1).toInt()
        return (row - 1) * 8 + "abcdefgh".indexOfFirst { it == column }
    }

    fun newGame(params: EventParams): GameProperties {
        params as CreateGameParams
        return GameProperties(
            pieces = setupEndgamePieces(),
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
        return !game.pieces.contains("wk")
    }

    fun isBlackCheckMate(game: GameProperties): Boolean {
        return !game.pieces.contains("bk")
    }

    fun calculateAllValidMoves(model: Model<GameProperties>): Set<Pair<String, String>> {
        val state = GameStates.valueOf(model.state)
        if (state == `Black victory` || state == `White victory`) {
            return emptySet()
        }
        val pieces = model.properties.pieces
        val validMoves = mutableSetOf<Pair<String, String>>()
        val board = Board(pieces)
        for (x in 0..7) {
            for (y in 0..7) {
                val piece = board.getPieceAt(x, y) ?: continue
                if ((piece.second == Color.white && state == `White turn`) || (piece.second == Color.black && state == `Black turn`)) {
                    validMoves.addAll(validMovesForPiece(board, x, y))
                }
            }
        }
        return validMoves   // TODO: should remove any move that leaves player with a threatened king
    }

    fun whiteHasPawnOnEdge(gameProperties: GameProperties): Boolean {
        return gameProperties.pieces.subList(56, 64).contains("wp")
    }

    fun blackHasPawnOnEdge(gameProperties: GameProperties): Boolean {
        return gameProperties.pieces.subList(0, 8).contains("bp")
    }

    fun isSwitchablePiece(model: Model<GameProperties>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
        val piece = (event as SelectPiece).getParams().piece
        if (piece.length != 1) {
            return BlockedByGuard("Illegal piece")
        }
        return if ("nqrb".contains(piece)) null else BlockedByGuard("Illegal piece")
    }

    fun switchPawn(model: Model<GameProperties>, eventParams: EventParams): GameProperties {
        eventParams as SelectPieceParams
        val piecesBefore = model.properties.pieces
        val piecesAfter = mutableListOf(*piecesBefore.toTypedArray())
        var index = piecesBefore.subList(0, 8).indexOf("bp") // black pawn
        if (index >= 0) {
            piecesAfter[index] = "b" + eventParams.piece
        } else {
            index = model.properties.pieces.subList(56, 64).indexOf("wp")   // white pawn
            piecesAfter[index + 56] = "w" + eventParams.piece
        }
        return model.properties.copy(pieces = piecesAfter)
    }

    fun isAutomaticDraw(gameProperties: GameProperties): Boolean = isStalemate() || isDeadPosition() || isFivefoldRepetition()

    fun canClaimDraw(model: Model<GameProperties>): Boolean = isThreefoldRepetition() || isFiftyMoves()

    fun drawWasRequiredDuringWhiteTurn(model: Model<GameProperties>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
        return null // TODO
    }

    fun drawWasRequiredDuringBlackTurn(model: Model<GameProperties>?, event: Event, userIdentity: UserIdentity): BlockedByGuard? {
        return null // TODO
    }

    private fun isStalemate(): Boolean {
        return false // TODO
    }

    private fun isDeadPosition(): Boolean {
        return false // TODO
    }

    private fun isThreefoldRepetition(): Boolean {
        return false // TODO
    }

    private fun isFivefoldRepetition(): Boolean {
        return false // TODO
    }

    private fun isFiftyMoves(): Boolean {
        return false // TODO
    }

    private fun validMovesForPiece(board: Board, x: Int, y: Int): Set<Pair<String, String>> {
        val piece = board.getPieceAt(x, y) ?: throw Exception()
        return when (piece.first) {
            pawn -> validMovesForPawn(board, x, y, piece.second)
            king -> validMovesForKing(board, x, y, piece.second)
            rook -> validMovesForRook(board, x, y, piece.second)
            bishop -> validMovesForBishop(board, x, y, piece.second)
            queen -> validMovesForQueen(board, x, y, piece.second)
            knight -> validMovesForKnight(board, x, y, piece.second)
        }
    }

    private fun validMovesForPawn(board: Board, x: Int, y: Int, color: Color): Set<Pair<String, String>> {
        val deltaY = if (color == Color.white) 1 else -1
        val validMoves = mutableSetOf<Pair<String, String>>()
        val from = Board.getSquareName(x, y)
        if (y == 7) {
            return emptySet()
        }
        if (board.getPieceAt(x, y + deltaY) == null) {
            validMoves.add(Pair(from, Board.getSquareName(x, y + deltaY)))
        }
        val pieceForwardRight = board.getPieceAt(x + 1, y + deltaY)
        if (x < 7 && pieceForwardRight != null && pieceForwardRight.second != color) {
            validMoves.add(Pair(from, Board.getSquareName(x + 1, y + deltaY)))
        }
        val pieceForwardLeft = board.getPieceAt(x - 1, y + deltaY)
        if (x > 1 && pieceForwardLeft != null && pieceForwardLeft.second != color) {
            validMoves.add(Pair(from, Board.getSquareName(x - 1, y + deltaY)))
        }
        // TODO: en passant
        return validMoves
    }

    private fun validMovesForKing(board: Board, x: Int, y: Int, color: Color): Set<Pair<String, String>> {
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

    private fun validMovesForRook(board: Board, x: Int, y: Int, color: Color): Set<Pair<String, String>> {
        val from = Board.getSquareName(x, y)
        val validSquares = mutableSetOf<SquareCoordinates>()
        validSquares.addAll(squaresInLine(board, x, y, color, 0, 1))
        validSquares.addAll(squaresInLine(board, x, y, color, 0, -1))
        validSquares.addAll(squaresInLine(board, x, y, color, 1, 0))
        validSquares.addAll(squaresInLine(board, x, y, color, -1, 0))
        return validSquares.map { Pair(from, Board.getSquareName(it)) }.toSet()
    }

    private fun validMovesForBishop(board: Board, x: Int, y: Int, color: Color): Set<Pair<String, String>> {
        val from = Board.getSquareName(x, y)
        val validSquares = mutableSetOf<SquareCoordinates>()
        validSquares.addAll(squaresInLine(board, x, y, color, 1, 1))
        validSquares.addAll(squaresInLine(board, x, y, color, 1, -1))
        validSquares.addAll(squaresInLine(board, x, y, color, -1, 1))
        validSquares.addAll(squaresInLine(board, x, y, color, -1, -1))
        return validSquares.map { Pair(from, Board.getSquareName(it)) }.toSet()
    }

    private fun validMovesForQueen(board: Board, x: Int, y: Int, color: Color): Set<Pair<String, String>> {
        return setOf(*validMovesForBishop(board, x, y, color).toTypedArray(), *validMovesForRook(board, x, y, color).toTypedArray())
    }

    private fun validMovesForKnight(board: Board, x: Int, y: Int, color: Color): Set<Pair<String, String>> {
        val from = Board.getSquareName(x, y)
        val validSquares = mutableSetOf<SquareCoordinates>()
        val destinations = arrayOf(
            SquareCoordinates(x + 1, y + 2),
            SquareCoordinates(x + 2, y + 1),
            SquareCoordinates(x + 2, y - 1),
            SquareCoordinates(x + 1, y - 2),
            SquareCoordinates(x - 1, y - 2),
            SquareCoordinates(x - 2, y - 1),
            SquareCoordinates(x - 2, y + 1),
            SquareCoordinates(x - 1, y + 2),
        )
        destinations.forEach {
            if (it.first in 0..7 && it.second in 0..7 && board.getPieceAt(it.first, it.second)?.second != color) {
                validSquares.add(it)
            }
        }
        return validSquares.map { Pair(from, Board.getSquareName(it)) }.toSet()
    }

    private fun squaresInLine(board: Board, x: Int, y: Int, color: Color, deltaX: Int, deltaY: Int): Set<SquareCoordinates> {
        val squares = mutableSetOf<SquareCoordinates>()
        var squareWasAdded: Boolean
        var yToCheck = y + deltaY
        var xToCheck = x + deltaX
        var capturedPiece = false
        do {
            val piece = board.getPieceAt(xToCheck, yToCheck)
            if (yToCheck in 0..7 && xToCheck in 0..7 && (piece == null || piece.second != color)) {
                squares.add(Pair(xToCheck, yToCheck))
                squareWasAdded = true
                yToCheck = yToCheck + deltaY
                xToCheck = xToCheck + deltaX
                if (piece != null && piece.second != color) {
                    capturedPiece = true
                }
            } else {
                squareWasAdded = false
            }
        } while (squareWasAdded && !capturedPiece)
        return squares
    }

    private fun isSquareOnBoard(from: String): Boolean {
        val column = from[0]
        val row = from.substring(1).toInt()
        return "abcdefgh".contains(column) && row in 1..8
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

    private fun setupEndgamePieces(): List<String> =
        List(64) {
            when (it) {
                54 -> "wp"
                20 -> "wk"
                58 -> "bk"
                else -> ""
            }
        }

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
