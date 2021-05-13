import com.expediagroup.graphql.generator.TopLevelObject
import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.ModelProperties
import graphql.GameQueryService
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
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

    simpleBackend.setup {
        databaseConnection(url = args[0], driver = args[1])
        databaseMigrations(Migrations)
        graphQlPort(8080)
        graphQlPackages("graphql")
        graphQlQueries {
            add(TopLevelObject(GameQueryService(myViews)))
        }
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
        serModule(createSerModule())
    }

    gameStateMachine.onStateChange { makeComputerMove(it) }

    simpleBackend.start()
}

fun createSerModule(): SerializersModule =
    SerializersModule {
        polymorphic(ModelProperties::class) {
            subclass(GameProperties::class)
            subclass(UserProperties::class)
        }
    }

data class Views(val game: GameView<Views>, val user: UserView<Views>)
