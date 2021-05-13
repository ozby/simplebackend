import com.expediagroup.graphql.generator.TopLevelObject
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.ModelProperties
import com.prettybyte.simplebackend.lib.ktorgraphql.GraphQLHelper
import graphql.GameQueryService
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import ktorgraphql.getGraphQLServer
import ktorgraphql.schema.EventMutationService
import modelviews.GameView
import modelviews.UserView
import statemachines.createGameStateMachine
import statemachines.userStateMachine
import kotlin.system.exitProcess

val simpleBackend = SimpleBackend<Event, Views>()

fun main(args: Array<String>) {
    if (args.size != 2) {
        println("Must be started with two arguments: url driver")
        println("url: the URL to the database (e.g. jdbc:sqlite:/home/linus/temp/chess.db)")
        println("driver: the database driver (e.g. org.sqlite.JDBC)")
        exitProcess(1)
    }

    val gameStateMachine = createGameStateMachine()
    val myViews = Views(GameView(), UserView())

    val serModule = createSerModule()

    simpleBackend.setup {
        databaseConnection(url = args[0], driver = args[1])
        databaseMigrations(Migrations)
        eventParser(::parseEvent)
        managedModels {
            model(UserProperties::class, userStateMachine(), myViews.user)
            model(GameProperties::class, gameStateMachine, myViews.game)
        }
        views(myViews)
        authorizationRules {
            reads {
                positive {
                    rule(::`A user can read games where she is a player`)
                }
                negative {
                    //           rule(::`Black victories cannot be read`)
                }
            }
            events {
                positive {
                    rule(::`A user can be created`)
                    rule(::`A user can create a game`)
                    rule(::`A user can perform actions in a game where she is a player`)
                }
                negative {
                    rule(::`Only admins can delete games`)
                }
            }

        }
        serModule(serModule)
    }

    gameStateMachine.onStateChange { makeComputerMove(it) }

    simpleBackend.start()

    val jsonMapper = Json { serializersModule = serModule }

    val mapper = jacksonObjectMapper()
    val server =
        getGraphQLServer(
            mapper,
            listOf("graphql"),
            listOf(TopLevelObject(GameQueryService(myViews))),
            listOf(TopLevelObject(EventMutationService<Event, Views>(simpleBackend, ::parseEvent, jsonMapper)))
        )
    val handler = GraphQLHelper(server, mapper)

    val ktorServer = embeddedServer(Netty, 8080) {
        routing {
            post("graphql") {
                handler.handle(this.call)
            }

            get("playground") {
                this.call.respondText(buildPlaygroundHtml("graphql", "subscriptions"), ContentType.Text.Html)
            }
        }
    }

    ktorServer.start(wait = true)


}

fun createSerModule(): SerializersModule =
    SerializersModule {
        polymorphic(ModelProperties::class) {
            subclass(GameProperties::class)
            subclass(UserProperties::class)
        }
    }

data class Views(val game: GameView<Views>, val user: UserView<Views>)

private fun buildPlaygroundHtml(graphQLEndpoint: String, subscriptionsEndpoint: String) =
    Application::class.java.classLoader.getResource("graphql-playground.html")?.readText()
        ?.replace("\${graphQLEndpoint}", graphQLEndpoint)
        ?.replace("\${subscriptionsEndpoint}", subscriptionsEndpoint)
        ?: throw IllegalStateException("graphql-playground.html cannot be found in the classpath")
