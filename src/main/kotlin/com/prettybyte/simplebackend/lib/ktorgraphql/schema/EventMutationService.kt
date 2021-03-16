package com.prettybyte.simplebackend.lib.ktorgraphql.schema

import arrow.core.Either.Left
import arrow.core.Either.Right
import com.expediagroup.graphql.types.operations.Mutation
import com.prettybyte.simplebackend.lib.*
import com.prettybyte.simplebackend.lib.AuthorizationRuleResult.allow
import com.prettybyte.simplebackend.lib.AuthorizationRuleResult.deny
import com.prettybyte.simplebackend.lib.ktorgraphql.AuthorizedContext
import com.prettybyte.simplebackend.logAndMakeInternalException
import graphql.execution.DataFetcherResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CreateEventResponse(val blockedByGuards: List<String>, val dryRun: Boolean, val modifiedModels: List<String>)

class EventMutationService<E : IEvent>(
    private val eventService: EventService<E>,
    private val eventParser: (name: String, modelId: String, params: String, userIdentityId: String) -> E,
    private val json: Json,
    private val eventAuthorizer: IEventAuthorizer<E>,
) : Mutation {

    suspend fun createEvent(
        eventName: String,
        modelId: String,
        eventParametersJson: String,
        dryRun: Boolean,
        context: AuthorizedContext
    ): DataFetcherResult<CreateEventResponse?> {
        try {
            if (modelId.length < 36) {  // TODO: we should make it so that the id is generated on the backend
                DataFetcherResult.newResult<CreateEventResponse>().error(Problem(Status.INVALID_ARGUMENT, "modelId is too short")).build()
            }
            val userIdentity = context.userIdentity
            val event = eventParser(eventName, modelId, eventParametersJson, userIdentity.id)
            validateParams(event)

            val authorizationRuleResults = AuthorizerRules.eventRules.map { it(userIdentity, event) }
            if (authorizationRuleResults.any { it == deny } || authorizationRuleResults.none { it == allow }) {
                return DataFetcherResult.newResult<CreateEventResponse>().error(Problem(Status.INVALID_ARGUMENT, "Permission denied")).build()
            }

            return when (val result = eventService.process(
                event,
                eventParametersJson,
                performActions = true,
                userIdentity = userIdentity,
                preventModelUpdates = dryRun,
            )) {
                is Left -> DataFetcherResult.newResult<CreateEventResponse>().error(result.a).build()
                is Right -> DataFetcherResult.newResult<CreateEventResponse>().data(
                    CreateEventResponse(
                        blockedByGuards = emptyList(),
                        dryRun = dryRun,
                        modifiedModels = result.b.map { json.encodeToString(it) }   // TODO: check if user is authorized to read models?
                    )
                ).build()
            }
        } catch (e: Exception) {
            throw logAndMakeInternalException(e)
        }
    }

    private fun validateParams(event: E) {
        try {
            event.getParams()
        } catch (e: Exception) {
            throw RuntimeException("At least one parameter is invalid or missing for event ${event.name}. The request contained these parameters: '${event.params}'  ${e.message}")
        }
    }

}
