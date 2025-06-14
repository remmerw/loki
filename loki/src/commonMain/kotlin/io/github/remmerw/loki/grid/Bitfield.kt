package io.github.remmerw.loki.grid

internal data class Bitfield(val bitfield: ByteArray) : Message {
    override val messageId: Byte
        get() = BITFIELD_ID

    override val type: Type
        get() = Type.Bitfield

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Bitfield) return false

        if (!bitfield.contentEquals(other.bitfield)) return false
        if (messageId != other.messageId) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bitfield.contentHashCode()
        result = 31 * result + messageId
        result = 31 * result + type.hashCode()
        return result
    }
}
