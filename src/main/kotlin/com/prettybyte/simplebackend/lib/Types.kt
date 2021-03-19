package com.prettybyte.simplebackend.lib

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import com.prettybyte.simplebackend.SingletonStuff
import com.prettybyte.simplebackend.lib.NegativeAuthorization.deny
import com.prettybyte.simplebackend.lib.PositiveAuthorization.allow
import com.prettybyte.simplebackend.lib.statemachine.StateMachine
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

data class ManagedModel<T : ModelProperties, E : IEvent, ModelStates : Enum<*>, V>(
    val kClass: KClass<T>,
    val stateMachine: StateMachine<T, E, ModelStates, V>,
    val view: IModelView<T, V>,
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

interface IModelView<T : ModelProperties, V> {
    fun get(id: String): ReadModelAuthorizer<T, V> {
        return ReadModelAuthorizer(getWithoutAuthorization(id))
    }

    fun getWithoutAuthorization(id: String): Model<T>?
    fun create(model: Model<T>)
    fun update(new: Model<T>)
    fun delete(id: String)
}

class ReadModelAuthorizer<T : ModelProperties, V>(private val value: Model<T>?) {

    fun auth(userIdentity: UserIdentity): Either<Problem, Model<T>?> {
        assert(value != null)
        if (value == null) {
            return Either.left(Problem(Status.NOT_FOUND, "Value is null"))
        }

        val views = SingletonStuff.getViews<V>()
        if (SingletonStuff.getReadModelNegativeRules<V>().map { it(userIdentity, value, views) }.any { it == deny }) {
            return Either.left(Problem.unauthorized())  // TODO: tell the user which rule was violated?
        }

        if (SingletonStuff.getReadModelPositiveRules<V>().map { it(userIdentity, value, views) }.none { it == allow }) {
            return Either.left(Problem.unauthorized("No policy explicitly allowed the request"))
        }
        return Either.right(value)
    }

}

class ReadModelListAuthorizer<T : ModelProperties, V>(val valueList: List<Model<T>>) {

    fun auth(userIdentity: UserIdentity): Either<Problem, List<Model<T>>?> {
        val auths = valueList.map { ReadModelAuthorizer<T, V>(it).auth(userIdentity) }
        if (auths.any { it is Either.Left }) {
            return Left(Problem.unauthorized("At least one element was unauthorized"))
        }
        return Right(valueList)
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

/*interface IEventAuthorizer<E : IEvent> {
    /**
     * This will be called when a client wants to acquire an simplebackend.SimpleBackend JWT. For Single-Sign On, this is a good place to
     * create a new user (unless the user has already been created).
     *
     * @return null if everything is ok, otherwise a Problem with an error message (no JWT will be issued to the client)
     */
    fun onExchangeJWT(jws: Jws<Claims>): Problem?

    fun isAllowedToSubscribeToEvents(userIdentity: UserIdentity): Boolean
}
 */

interface IMigrations<E : IEvent> {
    fun migrate(old: RawEvent): Pair<MigrationAction, E?>
}

enum class MigrationAction {        // TODO: try using sealed classes instead so we don't need the ugly Pair
    keepAsItIs,
    migrate,
    delete,
}

data class BlockedByGuard(val message: String)

