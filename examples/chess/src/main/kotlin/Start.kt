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
        databaseConnection = DatabaseConnection(url = "jdbc:sqlite:/home/linus/temp/autoserverChess.db", driver = "org.sqlite.JDBC"),
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
        customQueries = listOf(TopLevelObject(GameQueryService()))
    )
    SimpleBackend.start()
}
