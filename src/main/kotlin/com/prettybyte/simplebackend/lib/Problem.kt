package com.prettybyte.simplebackend.lib

enum class Status {
    INVALID_ARGUMENT,
    FAILED_PRECONDITION,
    NOT_FOUND,
    UNAUTHORIZED,
}

class Problem(private val status: Status, private val errorMessage: String = "") {

    fun asException(): Exception = RuntimeException(errorMessage)

    companion object {

        fun unknownModelType(type: String): Problem =
            Problem(Status.INVALID_ARGUMENT, "Unknown model type '$type'")

        fun noCannotHandleEvent(eventName: String): Problem =
            Problem(
                Status.INVALID_ARGUMENT,
                "The model is not in a state where it can handle event '$eventName'"
            )

        fun preventedByGuard(failedGuards: List<BlockedByGuard>): Problem =
            Problem(Status.FAILED_PRECONDITION, failedGuards.joinToString(", ") { it.message })

        fun generalProblem(): Problem =
            Problem(Status.INVALID_ARGUMENT, "General problem")

        fun notFound(): Problem = Problem(Status.NOT_FOUND, "Not found")

        fun unauthorized(message: String = ""): Problem = Problem(Status.UNAUTHORIZED, "Unauthorized ($message)")
    }

    override fun toString(): String {
        return errorMessage
    }

}
