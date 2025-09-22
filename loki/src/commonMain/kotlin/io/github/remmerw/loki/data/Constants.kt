package io.github.remmerw.loki.data


private val choke = Choke()

internal fun choke(): Choke {
    return choke
}


private val interested = Interested()

internal fun interested(): Interested {
    return interested
}


private val notInterested = NotInterested()

internal fun notInterested(): NotInterested {
    return notInterested
}


private val unchoke = Unchoke()

internal fun unchoke(): Unchoke {
    return unchoke
}

const val UPPER_RESERVED_BOUND = 8 * 8 - 1
internal const val HANDSHAKE_RESERVED_LENGTH = 8
internal const val TORRENT_ID_LENGTH: Int = 20
internal const val SHA1_HASH_LENGTH: Int = 20

internal const val CHOKE_ID: Byte = 0x0

internal const val UNCHOKE_ID: Byte = 0x1

internal const val INTERESTED_ID: Byte = 0x2

internal const val NOT_INTERESTED_ID: Byte = 0x3

internal const val HAVE_ID: Byte = 0x4

internal const val BITFIELD_ID: Byte = 0x5

internal const val REQUEST_ID: Byte = 0x6

internal const val PIECE_ID: Byte = 0x7

internal const val CANCEL_ID: Byte = 0x8

internal const val PORT_ID: Byte = 9

internal const val EXTENDED_MESSAGE_ID: Byte = 20

internal const val EXTENDED_HANDSHAKE_TYPE_ID: Byte = 0

internal val PROTOCOL_NAME = "BitTorrent protocol".encodeToByteArray()

internal val KEEPALIVE = byteArrayOf(0, 0, 0, 0)

