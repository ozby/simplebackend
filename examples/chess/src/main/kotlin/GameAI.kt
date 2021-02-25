import GameRules.squareToIndex
import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.UserIdentity
import kotlinx.coroutines.delay
import statemachines.GameStates

suspend fun makeComputerMove(model: Model<GameProperties>) {
    val game = model.properties
    if (model.state != GameStates.waitingForBlack.name) {
        return
    }
    delay(2000)
    if (game.pieces[squareToIndex("b7")].isNotEmpty()) {
        val p = "{\"from\": \"b7\", \"to\": \"b6\"}"
        val e = MakeMove(gameId = model.id, params = p, userIdentityId = computerPlayer)
        SimpleBackend.processEvent(e, eventParametersJson = p, userIdentity = UserIdentity(computerPlayer))    // TODO
    } else if (game.pieces[squareToIndex("e7")].isNotEmpty()) {
        val p = "{\"from\": \"e7\", \"to\": \"e6\"}"
        val e = MakeMove(gameId = model.id, params = p, userIdentityId = computerPlayer)
        SimpleBackend.processEvent(e, eventParametersJson = p, userIdentity = UserIdentity(computerPlayer))    // TODO
    } else if (game.pieces[squareToIndex("h7")].isNotEmpty()) {
        val p = "{\"from\": \"h7\", \"to\": \"h6\"}"
        val e = MakeMove(gameId = model.id, params = p, userIdentityId = computerPlayer)
        SimpleBackend.processEvent(e, eventParametersJson = p, userIdentity = UserIdentity(computerPlayer))    // TODO
    } else if (game.pieces[squareToIndex("a7")].isNotEmpty()) {
        val p = "{\"from\": \"a7\", \"to\": \"a6\"}"
        val e = MakeMove(gameId = model.id, params = p, userIdentityId = computerPlayer)
        SimpleBackend.processEvent(e, eventParametersJson = p, userIdentity = UserIdentity(computerPlayer))    // TODO
    }
}
