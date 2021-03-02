import Color.black
import Color.white
import Piece.king
import Piece.pawn
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardTest {

    val setupEndgamePieces: List<String> =
        List(64) {
            when (it) {
                54 -> "wp"
                20 -> "wk"
                58 -> "bk"
                else -> ""
            }
        }

    @Test
    fun isSquareWhite() {
        assertFalse { Board.isSquareWhite(0) }
        assertTrue { Board.isSquareWhite(1) }
        assertTrue { Board.isSquareWhite(7) }
        assertTrue { Board.isSquareWhite(8) }
        assertFalse { Board.isSquareWhite(9) }
        assertFalse { Board.isSquareWhite(63) }
    }

    @Test
    fun squareName() {
        assertTrue { Board.getSquareName(SquareCoordinates(0, 0)) == "a1" }
        assertTrue { Board.getSquareName(SquareCoordinates(7, 7)) == "h8" }
        assertTrue { Board.getSquareName(SquareCoordinates(1, 2)) == "b3" }
    }

    @Test
    fun isSquareOnBoard() {
        assertTrue { Board.isSquareOnBoard("a1") }
        assertTrue { Board.isSquareOnBoard("h7") }
        assertFalse { Board.isSquareOnBoard("i3") }
        assertFalse { Board.isSquareOnBoard("a0") }
        assertFalse { Board.isSquareOnBoard("a9") }
    }

    @Test
    fun squareToIndex() {
        assertTrue { Board.squareToIndex("a1") == 0 }
        assertTrue { Board.squareToIndex("a2") == 8 }
        assertTrue { Board.squareToIndex("h8") == 63 }
    }

    @Test
    fun pieceAtSquare() {
        val board = Board(setupEndgamePieces)
        val piece1 = board.getPieceAt(6, 6)
        val piece2 = board.getPieceAt(2, 7)
        assertTrue { piece1 != null && piece1.first == pawn && piece1.second == white }
        assertTrue { piece2 != null && piece2.first == king && piece2.second == black }
    }
}
