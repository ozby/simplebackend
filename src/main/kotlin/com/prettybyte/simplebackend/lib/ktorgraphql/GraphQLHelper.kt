package com.prettybyte.simplebackend.lib.ktorgraphql

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*

class GraphQLHelper(private val ktorGraphQLServer: KtorGraphQLServer, private val mapper: ObjectMapper) {

    suspend fun handle(applicationCall: ApplicationCall) {
        // Execute the query against the schema
        val result = ktorGraphQLServer.execute(applicationCall.request)

        if (result != null) {
            // write response as json
            val json = mapper.writeValueAsString(result.response)
            applicationCall.response.call.respond(json)
        } else {
            applicationCall.response.call.respond(HttpStatusCode.BadRequest, "Invalid request")
        }
    }

    fun test(m: String) {
        println(m)
    }


}

