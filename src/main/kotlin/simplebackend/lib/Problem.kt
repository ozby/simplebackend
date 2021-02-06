package simplebackend.lib

import io.grpc.Status

class Problem(private val grpcStatus: Status, private val message: String = "") {

    fun toGrpcException(): Exception = grpcStatus.withDescription(message).asRuntimeException()

    companion object {

        fun unknownModelType(type: String): Problem =
            Problem(Status.INVALID_ARGUMENT, "Unknown model type '$type'")

        fun noTransitionAvailableForEvent(eventName: String): Problem =
            Problem(
                Status.INVALID_ARGUMENT,
                "The model is not in a state where there is a transition that can be triggered by '$eventName'"
            )

        fun preventedByGuard(failedGuards: List<String>): Problem =
            Problem(Status.FAILED_PRECONDITION, failedGuards.joinToString(". "))
    }

    override fun toString(): String {
        return message
    }
}
