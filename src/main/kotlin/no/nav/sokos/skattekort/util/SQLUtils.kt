package no.nav.sokos.skattekort.util

import java.security.MessageDigest
import javax.sql.DataSource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using

object SQLUtils {
    fun <A> DataSource.transaction(operation: (TransactionalSession) -> A): A =
        using(sessionOf(this, returnGeneratedKey = true)) { session ->
            session.transaction { tx ->
                operation(tx)
            }
        }

    /**
     * Suspend-variant av [transaction]. JDBC er blokkerende, så hele transaksjonen kjøres på
     * Dispatchers.IO, og den suspendende operasjonen bridges med runBlocking inni
     * kotliquery-transaksjonen. Bevarer transaksjonshelheten (atomitet) når suspend-kall
     * (f.eks. HTTP-klienter) må skje inne i transaksjonen.
     *
     * Merk: transaksjonen holder en DB-tilkobling mens operasjonen pågår. Unngå derfor
     * langvarige suspend-kall her dersom de kan gjøres før/etter transaksjonen i stedet.
     */
    suspend fun <A> DataSource.transactionSuspending(operation: suspend (TransactionalSession) -> A): A =
        withContext(Dispatchers.IO) {
            using(sessionOf(this@transactionSuspending, returnGeneratedKey = true)) { session ->
                session.transaction { tx ->
                    runBlocking { operation(tx) }
                }
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
