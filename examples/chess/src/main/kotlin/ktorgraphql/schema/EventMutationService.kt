package ktorgraphql.schema

import AuthorizedContext
import Views
import arrow.core.Either
import com.expediagroup.graphql.types.operations.Mutation
import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.IEvent
import com.prettybyte.simplebackend.lib.Problem
import graphql.ErrorClassification
import graphql.GraphQLError
import graphql.execution.DataFetcherResult
import graphql.language.SourceLocation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CreateEventResponse(val blockedByGuards: List<String>, val dryRun: Boolean, val modifiedModels: List<String>)

class EventMutationService<E : IEvent, V>(
    private val simpleBackend: SimpleBackend<E, Views>,
    private val eventParser: (name: String, modelId: String, params: String, userIdentityId: String) -> E,
    private val jsonMapper: Json,
) : Mutation {

    suspend fun createEvent(
        eventName: String,
        modelId: String,
        eventParametersJson: String,
        dryRun: Boolean,
        context: AuthorizedContext
    ): DataFetcherResult<CreateEventResponse?> {
        val userIdentity = context.userIdentity
        val event = eventParser(eventName, modelId, eventParametersJson, userIdentity.id)

        return when (val result = simpleBackend.processEvent(
            event,
            eventParametersJson,
            performActions = true,
            userIdentity = userIdentity,
            preventModelUpdates = dryRun,
            storeEvent = true,
        )) {
            is Either.Left -> DataFetcherResult.newResult<CreateEventResponse>().error(MyGraphQlError(result.a)).build()
            is Either.Right -> DataFetcherResult.newResult<CreateEventResponse>().data(
                CreateEventResponse(
                    blockedByGuards = emptyList(),
                    dryRun = dryRun,
                    modifiedModels = result.b.map { jsonMapper.encodeToString(it) }   // TODO: check if user is authorized to read models?
                )
            ).build()

        }
    }
}

class MyGraphQlError(private val a: Problem) : GraphQLError {
    override fun getMessage(): String {
        return a.toString()
    }

    override fun getLocations(): MutableList<SourceLocation> {
        return emptyList<SourceLocation>().toMutableList()
    }

    override fun getErrorType(): ErrorClassification {
        TODO("Not yet implemented")
    }

}

    