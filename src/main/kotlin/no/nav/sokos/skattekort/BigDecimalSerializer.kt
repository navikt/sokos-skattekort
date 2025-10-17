package no.nav.sokos.skattekort

import java.math.BigDecimal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive

object BigDecimalSerializer : KSerializer<BigDecimal> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: BigDecimal,
    ) = when (encoder) {
        // use JsonUnquotedLiteral() to encode the BigDecimal literally
        is JsonEncoder -> encoder.encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
        else -> encoder.encodeString(value.toPlainString())
    }

    override val descriptor: SerialDescriptor = SerialDescriptor("CustomType", IntArraySerializer().descriptor)

    override fun deserialize(decoder: Decoder): BigDecimal =
        when (decoder) {
            // must use decodeJsonElement() to get the value, and then convert it to a BigDecimal
            is JsonDecoder ->
                decoder
                    .decodeJsonElement()
                    .jsonPrimitive.content
                    .toBigDecimal()
            else -> decoder.decodeString().toBigDecimal()
        }
}

typealias BigDecimalJson =
    @Serializable(with = BigDecimalSerializer::class)
    BigDecimal
