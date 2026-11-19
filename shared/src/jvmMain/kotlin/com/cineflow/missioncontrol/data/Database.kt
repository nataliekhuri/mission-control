package com.cineflow.missioncontrol.data

import org.sqlite.SQLiteConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/**
 * Owns the SQLite connection and creates the schema on first launch.
 *
 * The database file lives in the user's home folder (~/.missioncontrol/missioncontrol.db) so that
 * it persists between executions and is never shipped inside the project folder.
 * No demo data is ever inserted here: on first launch every table is empty.
 */
class Database(val path: Path = defaultPath()) : AutoCloseable {

    val connection: Connection

    init {
        Files.createDirectories(path.toAbsolutePath().parent)
        val config = SQLiteConfig().apply { enforceForeignKeys(true) }
        connection = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}", config.toProperties())
        connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        // norm(text): accent/case-insensitive comparison for searches (SQLite LIKE only folds ASCII).
        org.sqlite.Function.create(connection, "norm", object : org.sqlite.Function() {
            override fun xFunc() = result(TextSearch.normalize(value_text(0) ?: ""))
        })
        createSchema()
    }

    private fun createSchema() {
        val statements = listOf(
            """
            CREATE TABLE IF NOT EXISTS MISIONES (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre      TEXT    NOT NULL,
                destino     TEXT    NOT NULL,
                fecha       TEXT    NOT NULL,
                duracion    INTEGER NOT NULL CHECK (duracion > 0),
                estado      TEXT    NOT NULL CHECK (estado IN (${sqlList<MissionStatus>()})),
                prioridad   TEXT    NOT NULL CHECK (prioridad IN (${sqlList<Priority>()})),
                descripcion TEXT    NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS ASTRONAUTAS (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre       TEXT    NOT NULL,
                especialidad TEXT    NOT NULL CHECK (especialidad IN (${sqlList<Specialty>()})),
                experiencia  INTEGER NOT NULL CHECK (experiencia >= 0),
                estado       TEXT    NOT NULL CHECK (estado IN (${sqlList<AstronautStatus>()}))
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS RECURSOS (
                id        INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre    TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                categoria TEXT    NOT NULL CHECK (categoria IN (${sqlList<ResourceCategory>()})),
                cantidad  INTEGER NOT NULL CHECK (cantidad >= 0),
                unidad    TEXT    NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS INCIDENCIAS (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                mision_id   INTEGER NOT NULL REFERENCES MISIONES(id) ON DELETE CASCADE,
                fecha       TEXT    NOT NULL,
                tipo        TEXT    NOT NULL CHECK (tipo IN (${sqlList<IncidentType>()})),
                gravedad    TEXT    NOT NULL CHECK (gravedad IN (${sqlList<Severity>()})),
                descripcion TEXT    NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS MISION_ASTRONAUTA (
                mision_id     INTEGER NOT NULL REFERENCES MISIONES(id) ON DELETE CASCADE,
                astronauta_id INTEGER NOT NULL REFERENCES ASTRONAUTAS(id) ON DELETE CASCADE,
                rol           TEXT    NOT NULL,
                PRIMARY KEY (mision_id, astronauta_id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS MISION_RECURSO (
                mision_id  INTEGER NOT NULL REFERENCES MISIONES(id) ON DELETE CASCADE,
                recurso_id INTEGER NOT NULL REFERENCES RECURSOS(id) ON DELETE RESTRICT,
                cantidad   INTEGER NOT NULL CHECK (cantidad > 0),
                PRIMARY KEY (mision_id, recurso_id)
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_incidencias_mision ON INCIDENCIAS(mision_id)",
            "CREATE INDEX IF NOT EXISTS idx_mision_astronauta_astronauta ON MISION_ASTRONAUTA(astronauta_id)",
            "CREATE INDEX IF NOT EXISTS idx_mision_recurso_recurso ON MISION_RECURSO(recurso_id)",
        )
        connection.createStatement().use { st -> statements.forEach { st.execute(it.trimIndent()) } }
    }

    /** Runs [block] inside a transaction: commit on success, rollback on any exception. */
    fun <T> transaction(block: () -> T): T {
        val previous = connection.autoCommit
        connection.autoCommit = false
        try {
            val result = block()
            connection.commit()
            return result
        } catch (e: Throwable) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = previous
        }
    }

    fun <T> query(sql: String, vararg params: Any?, mapper: (ResultSet) -> T): List<T> =
        connection.prepareStatement(sql).use { st ->
            bind(st, params)
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(mapper(rs)) }
            }
        }

    fun execute(sql: String, vararg params: Any?): Int =
        connection.prepareStatement(sql).use { st ->
            bind(st, params)
            st.executeUpdate()
        }

    /** Executes an INSERT and returns the generated row id. */
    fun insert(sql: String, vararg params: Any?): Long {
        execute(sql, *params)
        return query("SELECT last_insert_rowid()") { it.getLong(1) }.first()
    }

    fun count(sql: String, vararg params: Any?): Int = query(sql, *params) { it.getInt(1) }.firstOrNull() ?: 0

    private fun bind(st: PreparedStatement, params: Array<out Any?>) {
        params.forEachIndexed { index, value ->
            val i = index + 1
            when (value) {
                null -> st.setNull(i, Types.NULL)
                is Enum<*> -> st.setString(i, value.name)
                is String -> st.setString(i, value)
                is Int -> st.setInt(i, value)
                is Long -> st.setLong(i, value)
                else -> st.setObject(i, value)
            }
        }
    }

    override fun close() {
        if (!connection.isClosed) connection.close()
    }

    companion object {
        /** ~/.missioncontrol/missioncontrol.db, or the file given in the MISSIONCONTROL_DB environment variable. */
        fun defaultPath(): Path =
            System.getenv("MISSIONCONTROL_DB")?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
                ?: Paths.get(System.getProperty("user.home"), ".missioncontrol", "missioncontrol.db")

        inline fun <reified E : Enum<E>> sqlList(): String = enumValues<E>().joinToString(",") { "'${it.name}'" }

        /** SQL CASE expression turning an enum column into its ordinal (useful to sort by priority/severity). */
        inline fun <reified E : Enum<E>> rank(column: String): String =
            "CASE $column " + enumValues<E>().joinToString(" ") { "WHEN '${it.name}' THEN ${it.ordinal}" } + " END"
    }
}
