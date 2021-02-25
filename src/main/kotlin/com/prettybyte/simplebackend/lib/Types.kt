package com.prettybyte.simplebackend.lib

import com.prettybyte.simplebackend.lib.statemachine.StateMachine
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

data class ManagedModel<T : ModelProperties, E : IEvent, ModelStates : Enum<*>>(
    val kClass: KClass<T>,
    val stateMachine: StateMachine<T, E, ModelStates>,
    val view: IModelView<T>,
)

@Serializable
data class Model<T : ModelProperties>(
    val id: String,
    val state: String,
    val graphQlName: String,
    val properties: T,
)

@Serializable
abstract class ModelProperties

interface IModelView<T : ModelProperties> {
    fun get(id: String): Model<T>?
    fun create(model: Model<T>)
    fun update(new: Model<T>)
}

interface IEvent {
    val schemaVersion: Int
    val modelId: String?
    val name: String
    val params: String
    val userIdentityId: String
    fun getParams(): EventParams
}

@Serializable
abstract class EventParams

class RawEvent(
    override val schemaVersion: Int,
    override val modelId: String?,
    override val name: String,
    override val params: String,
    override val userIdentityId: String,
    val eventId: Int,
) : IEvent {
    override fun getParams(): EventParams = RawEventParams()
}

class RawEventParams : EventParams()

interface IAuthorizer<E : IEvent> {
    /**
     * This will be called when a client wants to acquire an simplebackend.SimpleBackend JWT. For Single-Sign On, this is a good place to
     * create a new user (unless the user has already been created).
     *
     * @return null if everything is ok, otherwise a Problem with an error message (no JWT will be issued to the client)
     */
    fun onExchangeJWT(jws: Jws<Claims>): Problem?

    fun isAllowedToCreateEvent(userIdentity: UserIdentity, event: E): Boolean
    fun isAllowedToSubscribeToEvents(userIdentity: UserIdentity): Boolean
}

typealias JsonString = String

interface IMigrations<E : IEvent> {
    fun migrate(old: RawEvent): Pair<MigrationAction, E?>
}

enum class MigrationAction {        // TODO: try using sealed classes instead so we don't need the ugly Pair
    keepAsItIs,
    migrate,
    delete,
}

interface IWithResponseMapper<T : ModelProperties> {
    fun toJsonResponse(model: Model<T>): JsonString
}

data class BlockedByGuard(val message: String)
