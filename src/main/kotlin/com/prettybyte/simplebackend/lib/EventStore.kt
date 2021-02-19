package com.prettybyte.simplebackend.lib

import com.prettybyte.simplebackend.DatabaseConnection
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class EventStore<E : IEvent>(
    databaseConnection: DatabaseConnection,
    private val eventParser: (name: String, modelId: String, params: String, userIdentityId: String) -> E
) {

    init {
        Database.connect(databaseConnection.url, databaseConnection.driver)
        transaction {
            SchemaUtils.create(Events)
        }
    }

    internal fun store(event: E, eventParametersJson: String) {
        transaction {
            addLogger()
            Events.insert {
                it[name] = event.name
                it[modelId] = event.modelId ?: ""
                it[params] = eventParametersJson
                it[timestamp] = Instant.now().epochSecond
                it[userIdentityId] = event.userIdentityId
                it[schemaVersion] = event.schemaVersion
            }
        }
    }

    internal fun readAllEvents(): List<E> =      // TODO: need to batch when db gets big
        transaction {
            Events.selectAll().orderBy(Events.id, SortOrder.ASC)
                .map {
                    val event = eventParser(it[Events.name], it[Events.modelId], it[Events.params], it[Events.userIdentityId])
                    if (event.schemaVersion != it[Events.schemaVersion]) {
                        throw RuntimeException("Your EventParser didn't parse '${it[Events.name]}' into correct schema version (the one in the database is ${it[Events.schemaVersion]} but your event has ${event.schemaVersion})")
                    }
                    return@map event
                }
        }

    internal fun readAllEventsRaw(): List<RawEvent> =
        transaction {
            Events.selectAll().orderBy(Events.id, SortOrder.ASC)
                .map {
                    RawEvent(
                        name = it[Events.name],
                        modelId = it[Events.modelId],
                        params = it[Events.params],
                        userIdentityId = it[Events.userIdentityId],
                        schemaVersion = it[Events.schemaVersion],
                        eventId = it[Events.id],
                    )
                }
        }

    internal fun delete(eventId: Int) {
        transaction {
            addLogger()
            Events.deleteWhere { Events.id eq eventId }
        }
    }

    internal fun update(eventId: Int, migratedEvent: E) {
        transaction {
            addLogger()
            Events.update({ Events.id eq eventId }) {
                it[name] = migratedEvent.name
                it[modelId] = migratedEvent.modelId ?: ""
                it[params] = migratedEvent.params
                it[timestamp] = Instant.now().epochSecond
                it[userIdentityId] = migratedEvent.userIdentityId
                it[schemaVersion] = migratedEvent.schemaVersion
            }
        }
    }

}

object Events : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", length = 50)
    val params = varchar("params", length = 5000)
    val modelId = varchar("model_id", 36)
    val timestamp = long("timestamp")
    val userIdentityId = varchar("user_identity_id", 36)
    val schemaVersion = integer("schema_version")

    override val primaryKey = PrimaryKey(id)
}
