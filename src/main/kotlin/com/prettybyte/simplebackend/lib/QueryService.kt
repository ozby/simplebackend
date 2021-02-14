package com.prettybyte.simplebackend.lib

/*
import arrow.core.Either
import io.grpc.Status
import simplebackend.QueryGrpcKt
import simplebackend.Simplebackend.QueryRequest
import simplebackend.Simplebackend.Response
import simplebackend.logAndMakeInternalException

class QueryService<E : IEvent>(
    private val modelViewProvider: (String) -> IModelView<out ModelProperties>,
    private val authorizer: IAuthorizer<E>,
    //
) : QueryGrpcKt.QueryCoroutineImplBase() {

    override suspend fun query(request: QueryRequest): Response {
        try {
            // val view = queryViews[request.view] ?: throw Status.INVALID_ARGUMENT.withDescription("Could not find view '${request.view}").asRuntimeException()

            val view = modelViewProvider(request.view)
            if (view !is IQueryView) {
                throw Status.INVALID_ARGUMENT.withDescription("View '${request.view} is not queriable").asRuntimeException()
            }

            val queryParams = parseQueryParams(request, view)

            ctx.call {
                if (!authorizer.isAllowedToQuery(userIdentityKey.get(), view, request.parametersMap)) {
                    throw Status.PERMISSION_DENIED.asRuntimeException()
                }
            }

            // val queryFunction = view.view::class.memberFunctions.single { it.name == "query" }
            //val result = queryFunction.call(view.view, queryParams) as Either<String, JsonString>
            val result = view.query(queryParams)
            when (result) {
                is Either.Left -> throw result.a.toGrpcException()
                is Either.Right -> return Response.newBuilder().setResponseJson(result.b).build()
            }
        } catch (e: Exception) {
            throw logAndMakeInternalException(e)
        }
    }

    private fun parseQueryParams(request: QueryRequest, view: IQueryView): Any {
        // Verify that the request doesn't contain any parameters that are unknown to the View
        val paramsTemplate = view.getQueryParamsClass().constructors.first().parameters
        val paramsNames = paramsTemplate.map { it.name }
        val unknownParams = request.parametersMap.keys.filter { !paramsNames.contains(it) }
        if (unknownParams.isNotEmpty()) {
            throw Status.INVALID_ARGUMENT.withDescription("Unknown params: ${unknownParams.joinToString(", ")}").asRuntimeException()
        }

        // Parse the parameters
        val params = mutableListOf<Any?>()
        paramsTemplate.forEach {
            val value = request.parametersMap[it.name]
            try {
                val param = when (it.type.toString()) {
                    "kotlin.Int" -> value!!.toInt()
                    "kotlin.Int?" -> value?.toIntOrNull()
                    "kotlin.String" -> value!!
                    "kotlin.String?" -> value
                    "kotlin.Boolean" -> value.toBoolean()
                    "kotlin.Boolean?" -> if (value.isNullOrBlank()) null else value.toBoolean()
                    else -> throw RuntimeException("Bug?: can't handle param type ${it.type}")
                }
                params.add(param)
            } catch (e: Exception) {
                throw Status.INVALID_ARGUMENT.withDescription("Could not parse parameter '${it.name}': '${e.message}'").asRuntimeException()
            }
        }
        // Create an instance of the QueryParams
        return view.getQueryParamsClass().constructors.first().call(*params.toTypedArray())
    }

}


 */