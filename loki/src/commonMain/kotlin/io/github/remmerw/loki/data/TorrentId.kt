package io.github.remmerw.loki.data


data class TorrentId(val bytes: ByteArray) {
    init {
        if (bytes.size != TORRENT_ID_LENGTH) {
            throw RuntimeException("Illegal threads.torrent ID length: " + bytes.size)
        }
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TorrentId) return false
        return bytes.contentEquals(other.bytes)
    }
}
