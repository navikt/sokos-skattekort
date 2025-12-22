package no.nav.sokos.skattekort.util

import java.security.MessageDigest
import javax.sql.DataSource

import kotlin.reflect.full.memberProperties

import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using

object SQLUtils {
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

    fun <A> DataSource.transaction(operation: (TransactionalSession) -> A): A =
        using(sessionOf(this, returnGeneratedKey = true)) { session ->
            session.transaction { tx ->
                operation(tx)
            }
        }

    fun advisoryKeysFromString(s: String): Pair<Int, Int> {
        val bytes = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())

        fun toInt(
            b: ByteArray,
            off: Int,
        ) = ((b[off].toInt() and 0xff) shl 24) or
            ((b[off + 1].toInt() and 0xff) shl 16) or
            ((b[off + 2].toInt() and 0xff) shl 8) or
            (b[off + 3].toInt() and 0xff)
        val k1 = toInt(bytes, 0)
        val k2 = toInt(bytes, 4)
        return k1 to k2
    }
}
