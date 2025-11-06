package no.nav.sokos.skattekort.util

import javax.sql.DataSource

import kotlin.reflect.full.memberProperties

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import org.postgresql.util.PSQLException

object SQLUtils {
    inline fun <reified T : Any> Row.optionalOrNull(columnLabel: String): T? =
        runCatching {
            this.any(columnLabel) as? T
        }.getOrNull()

    inline fun <reified T : Any> T.asMap(): Map<String, Any?> {
        val props = T::class.memberProperties.associateBy { it.name }
        return props.keys.associateWith { props[it]?.get(this) }
    }

    inline fun <reified T : Any> DataSource.withTx(
        existing: TransactionalSession?,
        crossinline action: (TransactionalSession) -> T,
    ): T =
        if (existing != null) {
            action(existing)
        } else {
            this.transaction { action(it) }
        }

    fun <A> DataSource.transaction(
        maxRetries: Int = 3,
        initialDelayMs: Long = 100L,
        operation: (TransactionalSession) -> A,
    ): A {
        var delay = initialDelayMs
        repeat(maxRetries) { attempt ->
            try {
                return using(sessionOf(this, returnGeneratedKey = true)) { session ->
                    session.transaction { tx ->
                        operation(tx)
                    }
                }
            } catch (e: PSQLException) {
                if (e.sqlState == "40001" && attempt < maxRetries - 1) {
                    Thread.sleep(delay)
                    delay *= 2
                } else {
                    throw e
                }
            }
        }
        error("Unreachable code")
    }
}
