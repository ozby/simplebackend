import com.expediagroup.graphql.generator.TopLevelObject
import com.prettybyte.simplebackend.DatabaseConnection
import com.prettybyte.simplebackend.SimpleBackend
import com.prettybyte.simplebackend.lib.ManagedModel
import com.prettybyte.simplebackend.lib.ModelProperties
import graphql.GameQueryService
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import statemachines.createGameStateMachine
import statemachines.userStateMachine
import views.GameView
import views.UserView

fun main() {

    val gameStateMachine = createGameStateMachine()
    gameStateMachine.onStateChange { makeComputerMove(it) }

    SimpleBackend.setup(
        databaseConnection = DatabaseConnection(url = "jdbc:sqlite:/home/linus/temp/simpleserverChess.db", driver = "org.sqlite.JDBC"),
        migrations = Migrations,
        eventParser = ::parseEvent,
        eventAuthorizer = EventAuthorizer,
        managedModels = setOf(
            ManagedModel(UserProperties::class, userStateMachine(), UserView),
            ManagedModel(GameProperties::class, gameStateMachine, GameView)
        ),
        port = 8080,
        serModule = SerializersModule {
            polymorphic(ModelProperties::class) {
                subclass(GameProperties::class)
                subclass(UserProperties::class)
            }
        },
        customGraphqlPackages = listOf("graphql"),
        customQueries = listOf(TopLevelObject(GameQueryService())),
        authorizationReadRules = setOf(::`A user can read games where she is a player`, ::`Black victories cannot be read`),
        authorizationEventRules = setOf(::`A user can create a game`, ::`A user can perform actions in a game where she is a player`),
    )
    SimpleBackend.start()
}


/*

simpleBackend {
  DatabaseConnection(url = "jdbc:sqlite:/home/linus/temp/simpleserverChess.db", driver = "org.sqlite.JDBC"),
  port(8080),
  grqphQlPackages {
    "graphql"
  }
  authorizationRules {
    reads {
        positive {
            rule(::`A user can read games where she is a player`)
            rule(::`A user can read her own userdata`
        }
        negative {
            rule(::`Black victories cannot be read`)
        }
    }
    events {
        positive {
            rule(::`A user can make a move`)
            rule(::`A user can offer draw`)
            rule(::`A user can accept draw`)
            rule(::`A user can decline draw`)
            rule(::`A user can resign`)
        }
        negative {
            rule(::`The user must have payed her subscription to play any game`)
        }
    }

  }


}



 */