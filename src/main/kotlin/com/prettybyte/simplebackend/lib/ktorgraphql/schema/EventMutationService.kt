package com.prettybyte.simplebackend.lib.ktorgraphql.schema

import arrow.core.Either.Left
import arrow.core.Either.Right
import com.expediagroup.graphql.types.operations.Mutation
import com.prettybyte.simplebackend.SingletonStuff
import com.prettybyte.simplebackend.lib.EventService
import com.prettybyte.simplebackend.lib.IEvent
import com.prettybyte.simplebackend.lib.NegativeAuthorization.deny
import com.prettybyte.simplebackend.lib.PositiveAuthorization.allow
import com.prettybyte.simplebackend.lib.Problem
import com.prettybyte.simplebackend.lib.Status
import com.prettybyte.simplebackend.lib.ktorgraphql.AuthorizedContext
import com.prettybyte.simplebackend.logAndMakeInternalException
import graphql.execution.DataFetcherResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CreateEventResponse(val blockedByGuards: List<String>, val dryRun: Boolean, val modifiedModels: List<String>)

class EventMutationService<E : IEvent, V>(
    private val eventService: EventService<E, V>,
    private val eventParser: (name: String, modelId: String, params: String, userIdentityId: String) -> E,
    private val json: Json,
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

            val negativeAuthProblem = SingletonStuff.getEventNegativeRules<V, E>().firstOrNull { it(userIdentity, event, SingletonStuff.getViews()) == deny }
            if (negativeAuthProblem != null) {
                return DataFetcherResult.newResult<CreateEventResponse>()
                    .error(Problem(Status.UNAUTHORIZED, extractNameFromFunction(negativeAuthProblem))).build()
            }
            if (SingletonStuff.getEventPositiveRules<V, E>().none { it(userIdentity, event, SingletonStuff.getViews()) == allow }) {
                return DataFetcherResult.newResult<CreateEventResponse>()
                    .error(Problem(Status.UNAUTHORIZED, "Event '$eventName' was not created since no policy allowed the operation")).build()
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

    private fun extractNameFromFunction(f: Function<Any>): String {
        val funString = f.toString()
        val startIndex = funString.indexOf("`") + 1
        return funString.substring(startIndex, funString.indexOf("`", startIndex + 1))
    }

    private fun validateParams(event: E) {
        try {
            event.getParams()
        } catch (e: Exception) {
            throw RuntimeException("At least one parameter is invalid or missing for event ${event.name}. The request contained these parameters: '${event.params}'  ${e.message}")
        }
    }

}
