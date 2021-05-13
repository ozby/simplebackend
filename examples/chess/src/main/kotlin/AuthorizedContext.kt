import com.expediagroup.graphql.generator.execution.GraphQLContext
import com.prettybyte.simplebackend.lib.UserIdentity

data class AuthorizedContext(
    val userIdentity: UserIdentity
) : GraphQLContext
