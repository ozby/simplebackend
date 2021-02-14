package com.prettybyte.simplebackend.lib.ktorgraphql

import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.toSchema
import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import com.expediagroup.graphql.server.execution.GraphQLServer
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.GraphQL
import io.ktor.request.*

class KtorGraphQLServer(
    requestParser: KtorGraphQLRequestParser,
    contextFactory: KtorGraphQLContextFactory,
    requestHandler: GraphQLRequestHandler
) : GraphQLServer<ApplicationRequest>(requestParser, contextFactory, requestHandler)

fun getGraphQLServer(mapper: ObjectMapper, customPackages: List<String>, queries: List<TopLevelObject>, mutations: List<TopLevelObject>): KtorGraphQLServer {
    val requestParser = KtorGraphQLRequestParser(mapper)
    val contextFactory = KtorGraphQLContextFactory()
    val config = SchemaGeneratorConfig(supportedPackages = listOf("com.prettybyte.simplebackend.lib.ktorgraphql.schema", *customPackages.toTypedArray()))
    val graphQLSchema = toSchema(config, queries, mutations)
    val graphQL = GraphQL.newGraphQL(graphQLSchema).build()
    val requestHandler = GraphQLRequestHandler(graphQL)
    return KtorGraphQLServer(requestParser, contextFactory, requestHandler)
}
