package io.github.remmerw.loki.grid.core


data class Peer(val address: ByteArray, val port: UShort) {

    init {
        require(port > 0.toUShort() && port <= 65535.toUShort()) {
            "Invalid port: $port"
        }
        require(address.size == 4 || address.size == 16) { "Invalid size for address" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Peer) return false


        if (!address.contentEquals(other.address)) return false
        if (port != other.port) return false

        return true
    }

    override fun hashCode(): Int {
        var result = address.contentHashCode()
        result = 31 * result + port.hashCode()

        return result
    }

}
