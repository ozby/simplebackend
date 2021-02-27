import com.prettybyte.simplebackend.lib.EventParams
import com.prettybyte.simplebackend.lib.IEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

const val createGame = "CreateGame"
const val makeMove = "MakeMove"
const val createUser = "CreateUser"
const val selectPiece = "SelectPiece"
const val resign = "Resign"
const val proposeDraw = "ProposeDraw"
const val acceptDraw = "AcceptDraw"
const val declineDraw = "DeclineDraw"

sealed class Event(
    override val schemaVersion: Int,
    override val modelId: String?,
    override val name: String,
    override val params: String,
    override val userIdentityId: String,
) : IEvent

class CreateGame(gameId: String, params: String, userIdentityId: String) : Event(
    schemaVersion = 7,
    name = createGame,
    modelId = gameId,
    params = params,
    userIdentityId = userIdentityId
) {
    override fun getParams(): CreateGameParams = Json.decodeFromString(params)
    override fun toString(): String = "created game"
}

class MakeMove(gameId: String, params: String, userIdentityId: String) : Event(
    schemaVersion = 1,
    name = makeMove,
    modelId = gameId,
    params = params,
    userIdentityId = userIdentityId
) {
    override fun getParams(): MakeMoveParams = Json.decodeFromString(params)
    override fun toString(): String {
        return "moved ${getParams().from} -> ${getParams().to}"
    }
}

class CreateUser(userId: String, params: String, userIdentityId: String) : Event(
    schemaVersion = 1,
    name = createUser,
    modelId = userId,
    params = params,
    userIdentityId = userIdentityId
) {
    override fun getParams(): CreateUserParams = Json.decodeFromString(params)
}

class SelectPiece(gameId: String, params: String, userIdentityId: String) : Event(
    schemaVersion = 1,
    name = selectPiece,
    modelId = gameId,
    params = params,
    userIdentityId = userIdentityId
) {
    override fun getParams(): SelectPieceParams = Json.decodeFromString(params)
}

class Resign(gameId: String, params: String, userIdentityId: String) : Event(
    schemaVersion = 1,
    name = resign,
    modelId = gameId,
    params = params,
    userIdentityId = userIdentityId
) {
    override fun getParams(): ResignParams = Json.decodeFromString(params)
}

class ProposeDraw(gameId: String, params: String, userIdentityId: String) : Event(
    schemaVersion = 1,
    name = proposeDraw,
    modelId = gameId,
    params = params,
    userIdentityId = userIdentityId
) {
    override fun getParams(): ProposeDrawParams = Json.decodeFromString(params)
}

class AcceptDraw(gameId: String, params: String, userIdentityId: String) : Event(
    schemaVersion = 1,
    name = acceptDraw,
    modelId = gameId,
    params = params,
    userIdentityId = userIdentityId
) {
    override fun getParams(): AcceptDrawParams = Json.decodeFromString(params)
}

class DeclineDraw(gameId: String, params: String, userIdentityId: String) : Event(
    schemaVersion = 1,
    name = proposeDraw,
    modelId = gameId,
    params = params,
    userIdentityId = userIdentityId
) {
    override fun getParams(): DeclineDrawParams = Json.decodeFromString(params)
}

@Serializable
data class CreateGameParams(val whitePlayerUserId: String, val blackPlayerUserId: String) : EventParams()

@Serializable
data class MakeMoveParams(val from: String, val to: String) : EventParams()

@Serializable
data class CreateUserParams(val userIdentityId: String, val firstName: String, val lastName: String) : EventParams()

@Serializable
data class SelectPieceParams(val piece: String) : EventParams()

@Serializable
data class ResignParams(val test: String) : EventParams()

@Serializable
data class ProposeDrawParams(val test: String) : EventParams()

@Serializable
data class AcceptDrawParams(val test: String) : EventParams()

@Serializable
data class DeclineDrawParams(val test: String) : EventParams()
