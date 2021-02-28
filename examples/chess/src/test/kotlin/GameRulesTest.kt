import GameRules.squareToIndex
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.UserIdentity
import statemachines.GameState
import statemachines.UserStates
import views.UserView
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class GameRulesTest {

    val userIdentityId1 = "userIdentityId1"
    val userIdentity1 = UserIdentity.fromTest(userIdentityId1)
    val user1Id = "user1"
    val user2Id = "user2"
    val user1 = Model(
        id = user1Id,
        state = UserStates.active.name,
        properties = UserProperties(firstName = "Adam", lastName = "Adamsson", roles = emptySet(), userIdentityId = userIdentityId1),
        graphQlName = "user"
    )
    val gameId = "gameId"

    val freshGameProperties = GameRules.newGame(CreateGameParams(whitePlayerUserId = user1Id, blackPlayerUserId = user2Id))
    val freshGameModel = Model(id = gameId, state = GameState.`White turn`.name, properties = freshGameProperties, graphQlName = "game")
    val moveA2A3Event = MakeMove(gameId = gameId, params = """{"from": "a2", "to": "a3"}""", userIdentityId1)
    val moveToIllegalSquareEvent = MakeMove(gameId = gameId, params = """{"from": "a2", "to": "j9"}""", userIdentityId1)

    @BeforeTest
    fun setupTest() {
        if (UserView.getWithoutAuthorization(user1Id) == null) {
            UserView.create(user1)
        } else {
            UserView.update(user1)
        }
    }

    @Test
    fun setupNewGame() {
        val gameProperties = GameRules.newGame(CreateGameParams(whitePlayerUserId = "1", blackPlayerUserId = "2"))
        assertTrue { gameProperties.whitePlayerId == "1" }
        assertTrue { gameProperties.blackPlayerId == "2" }
        assertTrue { gameProperties.pieces.size == 64 }
        assertTrue { gameProperties.pieces[0] == "wr" }
    }

    @Test
    fun isCorrectPlayer() {
        val problem = GameRules.isCorrectPlayer(freshGameModel, moveA2A3Event, userIdentity1)
        assertTrue { problem == null }
    }

    @Test
    fun isValidMove() {
        assertTrue { GameRules.validateMove(freshGameModel, moveA2A3Event, userIdentity1) == null }
        assertTrue { GameRules.validateMove(freshGameModel, moveToIllegalSquareEvent, userIdentity1) != null }
    }

    @Test
    fun makeTurn() {
        val result = GameRules.makeTurn(freshGameModel, moveA2A3Event.getParams())
        assertTrue { result.pieces[squareToIndex("a2")].isEmpty() }
        assertTrue { result.pieces[squareToIndex("a3")] == "wp" }
    }
}
