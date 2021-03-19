import Board.Companion.squareToIndex
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.UserIdentity
import modelviews.GameView
import modelviews.UserView
import statemachines.GameState
import statemachines.UserStates
import kotlin.test.*

class ChessRulesTest() {

    val userIdentityId1 = "userIdentityId1"
    val userIdentity1 = UserIdentity.fromTest(userIdentityId1)
    val user1Id = "user1"
    val user2Id = "user2"
    val user1 = Model(
        id = user1Id,
        state = UserStates.active.name,
        properties = UserProperties(
            firstName = "Adam",
            lastName = "Adamsson",
            roles = emptySet(),
            userIdentityId = userIdentityId1,
            victories = 0,
            defeats = 0,
            draws = 0
        ),
        graphQlName = "user"
    )
    val gameId = "gameId"

    val freshGameProperties = newGame(CreateGameParams(whitePlayerUserId = user1Id, blackPlayerUserId = user2Id))
    val freshGameModel = Model(id = gameId, state = GameState.`White turn`.name, properties = freshGameProperties, graphQlName = "game")
    val moveA2A3Event = MakeMove(gameId = gameId, params = """{"from": "a2", "to": "a3"}""", userIdentityId1)
    val moveToIllegalSquareEvent = MakeMove(gameId = gameId, params = """{"from": "a2", "to": "j9"}""", userIdentityId1)

    val views = Views(GameView(), UserView())

    @BeforeTest
    fun setupTest() {
        if (views.user.getWithoutAuthorization(user1Id) == null) {
            views.user.create(user1)
        } else {
            views.user.update(user1)
        }
    }

    @Test
    fun setupNewGame() {
        val gameProperties = newGame(CreateGameParams(whitePlayerUserId = "1", blackPlayerUserId = "2"))
        assertTrue { gameProperties.whitePlayerId == "1" }
        assertTrue { gameProperties.blackPlayerId == "2" }
        assertTrue { gameProperties.pieces.size == 64 }
        assertTrue { gameProperties.pieces[0] == "wr" }
    }

    @Test
    fun isCorrectPlayer() {
        assertNull(`Event comes from white player`(freshGameModel, moveA2A3Event, userIdentity1, views))
    }

    @Test
    fun isValidMove() {
        assertNull(`Validate move`(freshGameModel, moveA2A3Event, userIdentity1, views))
        assertNotNull(`Validate move`(freshGameModel, moveToIllegalSquareEvent, userIdentity1, views))
    }

    @Test
    fun makeTurn() {
        val result = makeTurn(freshGameModel, moveA2A3Event.getParams())
        assertTrue { result.pieces[squareToIndex("a2")].isEmpty() }
        assertTrue { result.pieces[squareToIndex("a3")] == "wp" }
    }

}
