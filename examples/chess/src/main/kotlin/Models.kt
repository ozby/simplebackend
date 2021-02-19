import com.prettybyte.simplebackend.lib.IWithResponseMapper
import com.prettybyte.simplebackend.lib.JsonString
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.ModelProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import views.GameResponse

@Serializable
data class GameProperties(
    val pieces: List<String>,
    val whitePlayerId: String,
    val blackPlayerId: String,
) : ModelProperties(), IWithResponseMapper<GameProperties> {
    override fun toJsonResponse(model: Model<GameProperties>): JsonString {
        return Json.encodeToString(GameResponse.from(model))
    }
}

@Serializable
data class UserProperties(
    val userIdentityId: String,
    val roles: Set<Authorizer.Roles>,
    val firstName: String,
    val lastName: String,
) : ModelProperties()
