package io.github.remmerw.loki.data

import kotlinx.io.Buffer


/**
 * Standard handshake message.
 * This is the very first message that peers must send,
 * when initializing a new peer connection.
 *
 *
 * Handshake message includes:
 * - a constant header, specified in standard BitTorrent protocol
 * - threads.torrent ID
 * - peer ID
 * - 8 reserved bytes, that are used by extensions, e.g. BEP-10: Extension Protocol
 *
 */
@Suppress("ArrayInDataClass")
internal data class Handshake(
    val name: String,
    val reserved: ByteArray,
    val torrentId: TorrentId,
    val peerId: ByteArray
) :
    Message {

    /**
     * Set a reserved bit.
     *
     * @param bitIndex Index of a bit to set (0..63 inclusive)
     */
    fun setReservedBit(bitIndex: Int) {
        if (bitIndex < 0 || bitIndex > UPPER_RESERVED_BOUND) {
            throw RuntimeException(
                "Illegal bit index: " + bitIndex +
                        ". Expected index in range [0.." + UPPER_RESERVED_BOUND + "]"
            )
        }
        setBit(reserved, bitIndex)
    }


    /**
     * Sets i-th bit in a bitmask.
     *
     * @param bytes    Bitmask.
     * @param i        Bit index (0-based)
     */
    private fun setBit(bytes: ByteArray, i: Int) {
        val byteIndex = (i / 8.0).toInt()
        if (byteIndex >= bytes.size) {
            throw RuntimeException("bit index is too large: $i")
        }

        val bitIndex = i % 8
        val shift = 7 - bitIndex
        val bitMask = 1 shl shift
        val currentByte = bytes[byteIndex]
        bytes[byteIndex] = (currentByte.toInt() or bitMask).toByte()
    }

    override val type: Type
        get() = Type.Handshake

    fun encode(buffer: Buffer) {
        // handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
        val data = name.encodeToByteArray()
        buffer.writeByte(data.size.toByte())
        buffer.write(data)
        buffer.write(reserved)
        buffer.write(torrentId.bytes)
        buffer.write(peerId)
    }

    override val messageId: Byte
        get() {
            throw UnsupportedOperationException()
        }


}
