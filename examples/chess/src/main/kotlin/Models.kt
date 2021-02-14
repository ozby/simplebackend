import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.prettybyte.simplebackend.lib.IWithResponseMapper
import com.prettybyte.simplebackend.lib.JsonString
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.ModelProperties
import views.GameResponse

@Serializable
data class Game(
    val pieces: List<String>,
    val whitePlayerId: String,
    val blackPlayerId: String,
) : ModelProperties(), IWithResponseMapper<Game> {
    override fun toJsonResponse(model: Model<Game>): JsonString {
        return Json.encodeToString(GameResponse.from(model))
    }
}

@Serializable
data class User(
    val userIdentityId: String,
    val roles: Set<Authorizer.Roles>,
    val firstName: String,
    val lastName: String,
) : ModelProperties()
