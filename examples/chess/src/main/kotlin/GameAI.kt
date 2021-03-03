import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.UserIdentity
import kotlinx.coroutines.delay
import statemachines.GameState.*
import kotlin.random.Random

val rnd = Random(1)

suspend fun makeComputerMove(model: Model<GameProperties>) {
    delay(1000)
    when (valueOf(model.state)) {
        `Black turn` -> calculateMove(model)
        `Black promote pawn` -> promotePawn(model)
        `White has proposed draw` -> declineDraw(model)
        else -> return
    }
}

private fun declineDraw(model: Model<GameProperties>) {
    val params = """{"test": ""}"""
    val event = DeclineDraw(gameId = model.id, params = params, userIdentityId = computerPlayer)
    SimpleBackend.processEvent(event, eventParametersJson = params, userIdentity = UserIdentity(computerPlayer))
}

private fun promotePawn(model: Model<GameProperties>) {
    val params = """{"piece": "q"}"""
    val event = PromotePawn(gameId = model.id, params = params, userIdentityId = computerPlayer)
    SimpleBackend.processEvent(event, eventParametersJson = params, userIdentity = UserIdentity(computerPlayer))
}

private fun calculateMove(model: Model<GameProperties>) {
    // first see if we should propose draw
    if (whiteIsAhead(model) &&
        !SimpleBackend.getEventsForModelId<Event>(model.id).any { it.name == proposeDraw }
    ) {
        val params = """{"test": ""}"""
        val event = ProposeDraw(gameId = model.id, params = params, userIdentityId = computerPlayer)
        SimpleBackend.processEvent(event, eventParametersJson = params, userIdentity = UserIdentity(computerPlayer))
        return
    }

    val validMoves = calculateAllValidMoves(model.properties.pieces, valueOf(model.state), gameId = model.id)
    val selectedMove = validMoves.random(rnd)
    val params = """{"from": "${selectedMove.first}", "to": "${selectedMove.second}"}"""
    val event = MakeMove(gameId = model.id, params = params, userIdentityId = computerPlayer)
    SimpleBackend.processEvent(event, eventParametersJson = params, userIdentity = UserIdentity(computerPlayer))
}

private fun whiteIsAhead(model: Model<GameProperties>): Boolean {
    val blackPieces = model.properties.pieces.filter { it.startsWith("b") }
    val whitePieces = model.properties.pieces.filter { it.startsWith("w") }
    return whitePieces.size > blackPieces.size
}
