package com.example.spendflow.data.remote.mapper

import com.example.spendflow.data.remote.FirestoreFields
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Thrown when a stored document can't be turned into a domain model. */
class FirestoreMappingException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/**
 * Typed reads from a document's data map. Required reads throw [FirestoreMappingException]
 * naming the field, so a bad document is easy to find in the emulator UI.
 */
internal class FirestoreData(private val id: String, private val data: Map<String, Any?>) {

    fun string(key: String): String = optString(key) ?: missing(key)
    fun optString(key: String): String? = read(key)

    // Firestore returns whole numbers as Long; anything else means the field was written wrong
    fun long(key: String): Long = optLong(key) ?: missing(key)
    fun optLong(key: String): Long? = when (val value = data[key]) {
        null -> null
        is Long -> value
        is Int -> value.toLong()
        else -> wrongType(key, "integer", value)
    }

    fun boolean(key: String, default: Boolean): Boolean = read(key) ?: default

    // Via seconds/nanos: Timestamp.toInstant() is API 26+ and isn't covered by desugaring
    fun instant(key: String): Instant = optInstant(key) ?: missing(key)
    fun optInstant(key: String): Instant? =
        read<Timestamp>(key)?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) }

    /** Decimals are stored as plain strings ("312.45"): Firestore has no exact decimal type. */
    fun decimal(key: String): BigDecimal {
        val value = string(key)
        return try {
            BigDecimal(value)
        } catch (e: NumberFormatException) {
            fail("Field '$key' is not a decimal: '$value'", e)
        }
    }

    fun localDate(key: String): LocalDate = optLocalDate(key) ?: missing(key)
    fun optLocalDate(key: String): LocalDate? = optString(key)?.let { value ->
        try {
            LocalDate.parse(value)
        } catch (e: Exception) {
            fail("Field '$key' is not an ISO date: '$value'", e)
        }
    }

    inline fun <reified E : Enum<E>> enum(key: String): E = optEnum<E>(key) ?: missing(key)

    inline fun <reified E : Enum<E>> optEnum(key: String): E? {
        val value = optString(key) ?: return null
        return enumValues<E>().firstOrNull { it.name == value }
            ?: fail("Field '$key' has unknown ${E::class.simpleName} '$value'")
    }

    /** Runs the domain constructor, reporting its invariant failures as mapping errors. */
    fun <T> build(block: FirestoreData.() -> T): T = try {
        block()
    } catch (e: FirestoreMappingException) {
        throw e
    } catch (e: IllegalArgumentException) {
        fail(e.message ?: "Invalid data", e)
    }

    private inline fun <reified T> read(key: String): T? = when (val value = data[key]) {
        null -> null
        is T -> value
        else -> wrongType(key, T::class.simpleName ?: "?", value)
    }

    fun missing(key: String): Nothing = fail("Missing required field '$key'")

    private fun wrongType(key: String, expected: String, value: Any): Nothing =
        fail("Field '$key' should be $expected but was ${value::class.simpleName}")

    fun fail(message: String, cause: Throwable? = null): Nothing =
        throw FirestoreMappingException("Document '$id': $message", cause)
}

internal fun Instant.toTimestamp() = Timestamp(epochSecond, nano)

/** Keeps the scale ("1.50" stays "1.50") so the value reads back equal. */
internal fun BigDecimal.toFirestore(): String = toPlainString()

/** Dates are stored as ISO strings ("2026-10-19"): no time zone, and they sort correctly. */
internal fun LocalDate.toFirestore(): String = toString()

/** Fields for a new document. Use with `set()`. */
fun Map<String, Any?>.withCreateTimestamps(): Map<String, Any?> = this + mapOf(
    FirestoreFields.CREATED_AT to FieldValue.serverTimestamp(),
    FirestoreFields.UPDATED_AT to FieldValue.serverTimestamp(),
)

/** Fields for an existing document. Use with `set(..., SetOptions.merge())` so createdAt is kept. */
fun Map<String, Any?>.withUpdateTimestamp(): Map<String, Any?> =
    this + (FirestoreFields.UPDATED_AT to FieldValue.serverTimestamp())
