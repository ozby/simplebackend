package com.prettybyte.simplebackend.lib

import arrow.core.Either
import com.prettybyte.simplebackend.lib.AuthorizationRuleResult.allow
import com.prettybyte.simplebackend.lib.AuthorizationRuleResult.deny
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
    fun get(id: String): ReadModelAuthorizer<T> {
        return ReadModelAuthorizer(getWithoutAuthorization(id))
    }

    fun getWithoutAuthorization(id: String): Model<T>?
    fun create(model: Model<T>)
    fun update(new: Model<T>)
    fun delete(id: String)
}

class ReadModelAuthorizer<T : ModelProperties>(private val value: Model<T>?) {

    fun auth(userIdentity: UserIdentity): Either<Problem, Model<T>?> {
        if (value == null) {
            return Either.right(null)
        }
        val authorizationRuleResults = AuthorizerRules.readModelRules.map { it(userIdentity, value) }
        if (authorizationRuleResults.any { it == deny }) {
            return Either.left(Problem.unauthorized())  // TODO: tell the user which rule was violated?
        }
        if (authorizationRuleResults.any { it == allow }) {
            return Either.right(value)
        }
        return Either.left(Problem.unauthorized("No policy explicitly allowed the request"))
    }

}

class ReadModelListAuthorizer<T : ModelProperties>(val theValueList: List<Model<T>>) {

    fun auth(userIdentity: UserIdentity): Either<Problem, List<Model<T>>?> {
        return Either.right(theValueList.filter { value ->
            val authorizationRuleResults = AuthorizerRules.readModelRules.map { it(userIdentity, value) }
            authorizationRuleResults.none { it == deny } && authorizationRuleResults.any { it == allow }
        })
    }

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

interface IEventAuthorizer<E : IEvent> {
    /**
     * This will be called when a client wants to acquire an simplebackend.SimpleBackend JWT. For Single-Sign On, this is a good place to
     * create a new user (unless the user has already been created).
     *
     * @return null if everything is ok, otherwise a Problem with an error message (no JWT will be issued to the client)
     */
    fun onExchangeJWT(jws: Jws<Claims>): Problem?

    fun isAllowedToSubscribeToEvents(userIdentity: UserIdentity): Boolean
}

interface IMigrations<E : IEvent> {
    fun migrate(old: RawEvent): Pair<MigrationAction, E?>
}

enum class MigrationAction {        // TODO: try using sealed classes instead so we don't need the ugly Pair
    keepAsItIs,
    migrate,
    delete,
}

data class BlockedByGuard(val message: String)

