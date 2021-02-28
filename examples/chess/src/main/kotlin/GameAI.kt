import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.UserIdentity
import kotlinx.coroutines.delay
import statemachines.GameState
import kotlin.random.Random

val rnd = Random(1)

suspend fun makeComputerMove(model: Model<GameProperties>) {
    if (model.state != GameState.`Black turn`.name) {
        return
    }
    delay(1000)
    val validMoves = GameRules.calculateAllValidMoves(model.properties.pieces, GameState.valueOf(model.state), verifyIsChecked = true)
    val selectedMove = validMoves.random(rnd)
    val params = """{"from": "${selectedMove.first}", "to": "${selectedMove.second}"}"""
    val event = MakeMove(gameId = model.id, params = params, userIdentityId = computerPlayer)
    SimpleBackend.processEvent(event, eventParametersJson = params, userIdentity = UserIdentity(computerPlayer))
}
