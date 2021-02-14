package com.prettybyte.simplebackend.lib.ktorgraphql.schema

import arrow.core.Either.Left
import arrow.core.Either.Right
import com.expediagroup.graphql.types.operations.Mutation
import com.prettybyte.simplebackend.lib.EventOptions
import com.prettybyte.simplebackend.lib.EventService
import com.prettybyte.simplebackend.lib.IAuthorizer
import com.prettybyte.simplebackend.lib.IEvent
import com.prettybyte.simplebackend.lib.ktorgraphql.AuthorizedContext
import com.prettybyte.simplebackend.logAndMakeInternalException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CreateEventResponse(val blockedByGuards: List<String>, val dryRun: Boolean, val model: String?)

class EventMutationService<E : IEvent>(
    private val eventService: EventService<E>,
    private val eventParser: (name: String, modelId: String, params: String, userIdentityId: String) -> E,
    private val json: Json,
    private val authorizer: IAuthorizer<E>,
) : Mutation {

    suspend fun createEvent(
        eventName: String,
        modelId: String,
        eventParametersJson: String,
        dryRun: Boolean,
        context: AuthorizedContext
    ): CreateEventResponse {
        try {
            if (modelId.length < 36) {  // TODO: we should make it so that the id is generated on the backend
                throw RuntimeException("modelId is too short")
            }
            val userIdentity = context.userIdentity
            val event = eventParser(eventName, modelId, eventParametersJson, userIdentity.id)
            validateParams(event)
            val eventOptions = EventOptions(dryRun = dryRun)
            if (!authorizer.isAllowedToCreateEvent(userIdentity, event)) {
                throw RuntimeException("Permission denied")
            }
            when (val result = eventService.process(event, eventOptions, eventParametersJson, userIdentity = userIdentity)) {
                is Left -> throw result.a.asException()
                is Right -> return CreateEventResponse(
                    blockedByGuards = emptyList(),
                    dryRun = dryRun,
                    model = json.encodeToString(result.b)
                )
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
