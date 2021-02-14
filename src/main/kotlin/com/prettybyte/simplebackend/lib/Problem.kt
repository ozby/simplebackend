package com.prettybyte.simplebackend.lib

enum class Status {
    INVALID_ARGUMENT,
    FAILED_PRECONDITION,
    NOT_FOUND
}

class Problem(private val status: Status, private val message: String = "") {

    fun asException(): Exception = RuntimeException(message)

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
    }

    override fun toString(): String {
        return message
    }
}
