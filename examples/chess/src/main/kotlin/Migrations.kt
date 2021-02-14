import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.prettybyte.simplebackend.lib.*
import com.prettybyte.simplebackend.lib.MigrationAction.*

object Migrations : IMigrations<Event> {

    override fun migrate(old: RawEvent): Pair<MigrationAction, Event?> {
        return when (old.name) {
            createGame -> when (old.schemaVersion) {
                5 -> toCreateGameV6(old)
                6 -> toCreateGameV7(old)
                else -> Pair(keepAsItIs, null)
            }
            else -> Pair(keepAsItIs, null)
        }
    }

    private fun toCreateGameV7(oldEvent: RawEvent): Pair<MigrationAction, Event?> {
        val oldParams = Json.decodeFromString<CreateGameParamsV3>(oldEvent.params)
        val newParams = CreateGameParams(whitePlayerUserId = oldParams.whitePlayerUzerId, blackPlayerUserId = oldParams.blackPlayerUserId)
        return Pair(migrate, CreateGame(gameId = oldEvent.modelId!!, params = Json.encodeToString(newParams), userIdentityId = oldEvent.userIdentityId))
    }

    private fun toCreateGameV6(oldEvent: RawEvent): Pair<MigrationAction, Event?> {
        val oldParams = Json.decodeFromString<CreateGameParamsV1>(oldEvent.params)
        val newParams = CreateGameParams(whitePlayerUserId = oldParams.whitePlayerUserId, blackPlayerUserId = oldParams.blackPlayerUserId)
        return Pair(migrate, CreateGame(gameId = oldEvent.modelId!!, params = Json.encodeToString(newParams), userIdentityId = oldEvent.userIdentityId))
    }
}

sealed class ObsoleteEvents(
    override val schemaVersion: Int,
    override val modelType: String,
    override val modelId: String?,
    override val name: String,
    override val params: String,
    override val userIdentityId: String,
) : IEvent

class CreateGameV1(gameId: String, params: String, userIdentityId: String) : ObsoleteEvents(
    schemaVersion = 1,
    name = createGame,
    modelType = "Game",
    modelId = gameId,
    params = params,
    userIdentityId = userIdentityId
) {
    override fun getParams(): CreateGameParamsV1 = Json.decodeFromString(params)
    override fun toString(): String = "created game"
}

@Serializable
data class CreateGameParamsV1(val whitePlayerUserId: String, val blackPlayerUserId: String) : EventParams()

@Serializable
data class CreateGameParamsV3(val whitePlayerUzerId: String, val blackPlayerUserId: String) : EventParams()
