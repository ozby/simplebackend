package com.prettybyte.simplebackend.lib

import graphql.ErrorClassification
import graphql.GraphQLError
import graphql.language.SourceLocation

enum class Status {
    INVALID_ARGUMENT,
    FAILED_PRECONDITION,
    NOT_FOUND
}

class Problem(private val status: Status, private val errorMessage: String = "") : GraphQLError {

    fun asException(): Exception = RuntimeException(errorMessage)

    companion object {

        fun unknownModelType(type: String): Problem =
            Problem(Status.INVALID_ARGUMENT, "Unknown model type '$type'")

        fun noTransitionAvailableForEvent(eventName: String): Problem =
            Problem(
                Status.INVALID_ARGUMENT,
                "The model is not in a state where there is a transition that can be triggered by '$eventName'"
            )

        fun preventedByGuard(failedGuards: List<BlockedByGuard>): Problem =
            Problem(Status.FAILED_PRECONDITION, failedGuards.joinToString(", ") { it.message })

        fun generalProblem(): Problem =
            Problem(Status.INVALID_ARGUMENT, "General problem")

        fun modelNotFound(): Problem = Problem(Status.NOT_FOUND, "Model not found")

    }

    override fun toString(): String {
        return errorMessage
    }

    override fun getMessage(): String {
        return errorMessage
    }

    override fun getLocations(): MutableList<SourceLocation> {
        return mutableListOf()
    }

    override fun getErrorType(): ErrorClassification {
        return MyErrorClassification()
    }
}

private class MyErrorClassification : ErrorClassification