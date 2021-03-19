import Board.Companion.isSquareOnBoard
import Board.Companion.squareToIndex
import Color.black
import Color.white
import Piece.*
import com.prettybyte.simplebackend.lib.*
import statemachines.GameState
import statemachines.GameState.`Black turn`
import statemachines.GameState.`White turn`

fun `Event comes from white player`(game: Model<GameProperties>?, event: IEvent, userIdentity: UserIdentity, views: Views): BlockedByGuard? {
    if (userIdentity.id == computerPlayer) {
        return null
    }
    val user = views.user.getActiveUserWithoutAuthorization(userIdentity) ?: return BlockedByGuard("Can't find user")
    if (game == null) {
        return BlockedByGuard("isCorrectPlayer: game was null")
    }
    return if (game.properties.whitePlayerId != user.id) BlockedByGuard("Not your turn") else null
}

fun `Event comes from black player`(game: Model<GameProperties>?, event: IEvent, userIdentity: UserIdentity, views: Views): BlockedByGuard? {
    if (userIdentity.id == computerPlayer) {
        return null
    }
    val user = views.user.getActiveUserWithoutAuthorization(userIdentity) ?: return BlockedByGuard("Can't find user")
    if (game == null) {
        return BlockedByGuard("isCorrectPlayer: game was null")
    }
    return if (game.properties.blackPlayerId != user.id) BlockedByGuard("Not your turn") else null
}

fun `Validate move`(model: Model<GameProperties>?, event: Event, userIdentity: UserIdentity, views: Views): BlockedByGuard? {
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
    val validMoves = calculateAllValidMoves(model.properties.pieces, GameState.valueOf(model.state), gameId = model.id)
    return if (validMoves.contains(Pair(from, to))) null else BlockedByGuard("Illegal move")
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
    val from = params.from
    val to = params.to
    val piecesAfter = piecesAfterMove(model.properties.pieces, from, to)
    return GameProperties(
        pieces = piecesAfter,
        whitePlayerId = model.properties.whitePlayerId,
        blackPlayerId = model.properties.blackPlayerId
    )
}

fun `White is checkmate`(model: Model<GameProperties>): Boolean {
    return isWhiteCheck(model.properties.pieces, model.id) && calculateAllValidMoves(model.properties.pieces, `White turn`, model.id, true).isEmpty()
}

fun `Black is checkmate`(model: Model<GameProperties>): Boolean {
    return isBlackCheck(model.properties.pieces, model.id) && calculateAllValidMoves(model.properties.pieces, `Black turn`, model.id, true).isEmpty()
}

fun calculateAllValidMoves(
    pieces: List<String>,
    state: GameState,
    gameId: String,
    removeIfKingIsExposed: Boolean = true,
    excludeCastling: Boolean = false
): Set<Pair<String, String>> {
    val validMoves = mutableSetOf<Pair<String, String>>()
    val board = Board(pieces)
    for (x in 0..7) {
        for (y in 0..7) {
            val piece = board.getPieceAt(x, y) ?: continue
            if ((piece.second == white && state == `White turn`) || (piece.second == Color.black && state == `Black turn`)) {
                validMoves.addAll(validMovesForPiece(board, x, y, gameId, excludeCastling))
            }
        }
    }
    return if (removeIfKingIsExposed) {
        validMoves.filter { !willMoveLeaveKingChecked(pieces, state, it, gameId) }.toSet()
    } else {
        validMoves
    }
}

fun `White can promote pawn`(model: Model<GameProperties>): Boolean {
    return model.properties.pieces.subList(56, 64).contains("wp")
}

fun `Black can promote pawn`(model: Model<GameProperties>): Boolean {
    return model.properties.pieces.subList(0, 8).contains("bp")
}

fun `Is promotable piece`(model: Model<GameProperties>?, event: Event, userIdentity: UserIdentity, views: Views): BlockedByGuard? {
    val piece = (event as PromotePawn).getParams().piece
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

fun `Automatic draw`(model: Model<GameProperties>): Boolean = isStalemate(model) || isDeadPosition(model) || isFivefoldRepetition()

fun canClaimDraw(model: Model<GameProperties>): Boolean = isThreefoldRepetition() || isFiftyMoves()

fun piecesAfterMove(piecesBefore: List<String>, from: String, to: String): List<String> {
    val piecesAfter = piecesBefore.toMutableList()
    piecesAfter[squareToIndex(to)] = piecesBefore[squareToIndex(from)]
    piecesAfter[squareToIndex(from)] = ""
    if (from == "e1" && to == "g1") {   // castling
        piecesAfter[squareToIndex("f1")] = piecesBefore[squareToIndex("h1")]
        piecesAfter[squareToIndex("h1")] = ""
    }
    if (from == "e1" && to == "c1") {   // castling
        piecesAfter[squareToIndex("d1")] = piecesBefore[squareToIndex("a1")]
        piecesAfter[squareToIndex("a1")] = ""
    }
    return piecesAfter
}

private fun isWhiteCheck(pieces: List<String>, gameId: String): Boolean {
    val kingIndex = pieces.indexOf("wk")
    return calculateAllValidMoves(
        pieces,
        `Black turn`,
        gameId,
        removeIfKingIsExposed = false,
        excludeCastling = true
    ).any { squareToIndex(it.second) == kingIndex }
}

private fun isBlackCheck(pieces: List<String>, gameId: String): Boolean {
    val kingIndex = pieces.indexOf("bk")
    return calculateAllValidMoves(
        pieces,
        `White turn`,
        gameId,
        removeIfKingIsExposed = false,
        excludeCastling = true
    ).any { squareToIndex(it.second) == kingIndex }
}

private fun willMoveLeaveKingChecked(piecesBeforeMove: List<String>, state: GameState, move: Pair<String, String>, gameId: String): Boolean {
    // figure out if the opponent can take the king next move
    val piecesAfterMove = piecesAfterMove(piecesBeforeMove, move.first, move.second)
    return if (state == `White turn`) isWhiteCheck(piecesAfterMove, gameId) else isBlackCheck(piecesAfterMove, gameId)
}

private fun isStalemate(model: Model<GameProperties>): Boolean {
    val pieces = model.properties.pieces
    return when (GameState.valueOf(model.state)) {
        `White turn` -> !isWhiteCheck(pieces, model.id) && calculateAllValidMoves(pieces, `White turn`, model.id, true).isEmpty()
        `Black turn` -> !isBlackCheck(pieces, model.id) && calculateAllValidMoves(pieces, `Black turn`, model.id, true).isEmpty()
        else -> throw Exception()
    }
}

private fun isDeadPosition(model: Model<GameProperties>): Boolean = isInsufficientMaterial(model)   // We are not trying to figure out other dead positions

private fun isInsufficientMaterial(model: Model<GameProperties>): Boolean {
    // will only check for insufficient material
    val remainingPieces = model.properties.pieces.filter { it.isNotEmpty() }
    if (remainingPieces.size == 2) {   // king vs king
        return true
    }
    if (remainingPieces.size == 3) {
        return (remainingPieces.contains("wb") || remainingPieces.contains("bb")) ||    // king and bishop vs king
                (remainingPieces.contains("wn") || remainingPieces.contains("bn"))      // king and knight vs king
    }
    if (remainingPieces.filter { it.isNotEmpty() }.size == 4) {
        return remainingPieces.contains("wb") && remainingPieces.contains("bb") &&  // King and bishop vs king and bishop of the same color as the opponent's bishop
                Board.isSquareWhite(remainingPieces.indexOf("wb")) == Board.isSquareWhite(remainingPieces.indexOf("bb"))
    }
    return false
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

private fun validMovesForPiece(board: Board, x: Int, y: Int, gameId: String, excludeCastling: Boolean): Set<Pair<String, String>> {
    val piece = board.getPieceAt(x, y) ?: throw Exception()
    return when (piece.first) {
        pawn -> validMovesForPawn(board, x, y, piece.second)
        king -> validMovesForKing(board, x, y, piece.second, gameId, excludeCastling)
        rook -> validMovesForRook(board, x, y, piece.second)
        bishop -> validMovesForBishop(board, x, y, piece.second)
        queen -> validMovesForQueen(board, x, y, piece.second)
        knight -> validMovesForKnight(board, x, y, piece.second)
    }
}

private fun validMovesForPawn(board: Board, x: Int, y: Int, color: Color): Set<Pair<String, String>> {
    val deltaY = if (color == white) 1 else -1
    val validMoves = mutableSetOf<Pair<String, String>>()
    val from = Board.getSquareName(x, y)
    if (y == 7) {
        return emptySet()
    }
    if (board.getPieceAt(x, y + deltaY) == null) {
        validMoves.add(Pair(from, Board.getSquareName(x, y + deltaY)))  // one step forward
    }
    if ((color == white && y == 1) || (color == black && y == 6) &&
        board.getPieceAt(x, y + deltaY) == null &&
        board.getPieceAt(x, y + deltaY + deltaY) == null
    ) {
        validMoves.add(Pair(from, Board.getSquareName(x, y + deltaY + deltaY))) // two steps forward
    }
    val pieceForwardRight = board.getPieceAt(x + 1, y + deltaY)
    if (x < 7 && pieceForwardRight != null && pieceForwardRight.second != color) {
        validMoves.add(Pair(from, Board.getSquareName(x + 1, y + deltaY)))
    }
    val pieceForwardLeft = board.getPieceAt(x - 1, y + deltaY)
    if (x > 0 && pieceForwardLeft != null && pieceForwardLeft.second != color) {
        validMoves.add(Pair(from, Board.getSquareName(x - 1, y + deltaY)))
    }
    // TODO: en passant
    return validMoves
}

private fun validMovesForKing(board: Board, x: Int, y: Int, color: Color, gameId: String, excludeCastling: Boolean): Set<Pair<String, String>> {
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
    if (!excludeCastling) {
        validMoves.addAll(validCastlingMoves(board, x, y, color, gameId))
    }
    return validMoves
}

private fun validCastlingMoves(board: Board, x: Int, y: Int, color: Color, gameId: String): Set<Pair<String, String>> {
    val validMoves = mutableSetOf<Pair<String, String>>()
    val from = Board.getSquareName(x, y)
    when (color) {
        white -> {
            if (isWhiteCheck(board.pieces, gameId)) {
                return emptySet()
            }
            if (board.getPieceAt(5, 0) == null &&
                board.getPieceAt(6, 0) == null &&
                !blackCanMoveTo(5, 0, board, gameId, excludeCastling = true) &&
                !blackCanMoveTo(6, 0, board, gameId, true) &&
                !pieceHasMovedFrom(4, 0, gameId) &&
                !pieceHasMovedFrom(7, 0, gameId)
            ) {
                validMoves.add(Pair(from, Board.getSquareName(6, 0)))
            }
            if (board.getPieceAt(1, 0) == null &&
                board.getPieceAt(2, 0) == null &&
                board.getPieceAt(3, 0) == null &&
                !blackCanMoveTo(1, 0, board, gameId, true) &&
                !blackCanMoveTo(2, 0, board, gameId, true) &&
                !blackCanMoveTo(3, 0, board, gameId, true) &&
                !pieceHasMovedFrom(4, 0, gameId) &&
                !pieceHasMovedFrom(0, 0, gameId)
            ) {
                validMoves.add(Pair(from, Board.getSquareName(2, 0)))
            }
        }
        black -> {
            // TODO
        }
    }
    return validMoves
}

private fun blackCanMoveTo(x: Int, y: Int, board: Board, gameId: String, excludeCastling: Boolean): Boolean {
    return calculateAllValidMoves(board.pieces, `Black turn`, gameId, excludeCastling = excludeCastling).any { it.second == Board.getSquareName(x, y) }
}

private fun pieceHasMovedFrom(x: Int, y: Int, gameId: String): Boolean {
    val squareName = Board.getSquareName(x, y)
    return simpleBackend.getEventsForModelId<Event, Views>(gameId).any { it is MakeMove && it.getParams().from == squareName }
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
