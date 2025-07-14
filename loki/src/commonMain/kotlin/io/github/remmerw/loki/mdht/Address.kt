package io.github.remmerw.loki.mdht

import io.github.remmerw.loki.createInetSocketAddress
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeUShort

internal data class Address(val data: ByteArray, val port: UShort) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Address

        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }

    fun encoded(): ByteArray {
        val buffer = Buffer()
        buffer.write(data)
        buffer.writeUShort(this.port)
        return buffer.readByteArray()
    }

    fun toInetSocketAddress(): InetSocketAddress {
        return createInetSocketAddress(data, port.toInt())
    }
}