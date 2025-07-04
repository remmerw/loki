package io.github.remmerw.loki.mdht

import io.github.remmerw.loki.buri.BEInteger
import io.github.remmerw.loki.buri.BEList
import io.github.remmerw.loki.buri.BEMap
import io.github.remmerw.loki.buri.BEObject
import io.github.remmerw.loki.buri.BEString
import io.github.remmerw.loki.buri.encode
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeUShort


internal interface Message {
    val address: InetSocketAddress
    val id: ByteArray
    val tid: ByteArray
    fun encode(buffer: Buffer)
}

internal interface Response : Message
internal interface Request : Message


@Suppress("ArrayInDataClass")
internal data class AnnounceRequest(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray,
    val infoHash: ByteArray,
    val port: Int,
    val token: ByteArray,
    val name: ByteArray?,
) :
    Request {

    override fun encode(buffer: Buffer) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        val inner: MutableMap<String, BEObject> = mutableMapOf()

        inner[Names.ID] = BEString(id)
        inner[Names.INFO_HASH] = BEString(infoHash)
        inner[Names.PORT] = BEInteger(port.toLong())
        inner[Names.TOKEN] = BEString(token)
        if (name != null) inner[Names.NAME] = BEString(name)
        base[Names.A] = BEMap(inner)

        // transaction ID
        base[Names.T] = BEString(tid)
        // message type
        base[Names.Y] = BEString(Names.Q.encodeToByteArray())

        // message method
        base[Names.Q] = BEString(Names.ANNOUNCE_PEER.encodeToByteArray())

        encode(base, buffer)
    }
}

@Suppress("ArrayInDataClass")
internal data class AnnounceResponse(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray
) : Response {

    override fun encode(buffer: Buffer) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        val inner: MutableMap<String, BEObject> = mutableMapOf()
        inner[Names.ID] = BEString(id)
        base[Names.R] = BEMap(inner)

        // transaction ID
        base[Names.T] = BEString(tid)
        // message type
        base[Names.Y] = BEString(Names.R.encodeToByteArray())

        encode(base, buffer)
    }

}

@Suppress("ArrayInDataClass")
internal data class Error(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray,
    val code: Int,
    val message: ByteArray
) : Message {

    override fun encode(buffer: Buffer) {


        val base: MutableMap<String, BEObject> = mutableMapOf()

        // transaction ID
        base[Names.T] = BEString(tid)
        // message type
        base[Names.Y] = BEString(Names.E.encodeToByteArray())

        base[Names.E] = BEList(listOf(BEInteger(code.toLong()), BEString(message)))

        encode(base, buffer)
    }

}


@Suppress("ArrayInDataClass")
internal data class FindNodeRequest(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray,
    val target: ByteArray
) :
    Request {

    override fun encode(buffer: Buffer) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        base[Names.A] = BEMap(
            mapOf<String, BEObject>(
                Names.ID to BEString(id),
                Names.TARGET to BEString(target)
            )
        )

        // transaction ID
        base[Names.T] = BEString(tid)
        // message type
        base[Names.Y] = BEString(Names.Q.encodeToByteArray())
        // message method
        base[Names.Q] = BEString(Names.FIND_NODE.encodeToByteArray())

        encode(base, buffer)
    }

}

@Suppress("ArrayInDataClass")
internal data class FindNodeResponse(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray,
    val nodes: List<Peer>,
    val nodes6: List<Peer>
) : Response {

    override fun encode(buffer: Buffer) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        val inner: MutableMap<String, BEObject> = mutableMapOf()
        inner[Names.ID] = BEString(id)
        if (nodes.isNotEmpty()) inner[Names.NODES] = writeBuckets(nodes)
        if (nodes6.isNotEmpty()) inner[Names.NODES6] = writeBuckets(nodes6)
        base[Names.R] = BEMap(inner)

        // transaction ID
        base[Names.T] = BEString(tid)

        // message type
        base[Names.Y] = BEString(Names.R.encodeToByteArray())

        // message method if we are a response
        base[Names.IP] = BEString(encode(address))

        encode(base, buffer)
    }


}

@Suppress("ArrayInDataClass")
internal data class GetPeersRequest(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray,
    val infoHash: ByteArray
) :
    Request {

    override fun encode(buffer: Buffer) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        base[Names.A] = BEMap(
            mapOf<String, BEObject>(
                Names.ID to BEString(id),
                Names.INFO_HASH to BEString(infoHash)
            )
        )

        // transaction ID
        base[Names.T] = BEString(tid)

        // message type
        base[Names.Y] = BEString(Names.Q.encodeToByteArray())

        // message method
        base[Names.Q] = BEString(Names.GET_PEERS.encodeToByteArray())

        encode(base, buffer)
    }
}

@Suppress("ArrayInDataClass")
internal data class GetPeersResponse(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray,
    val token: ByteArray?,
    val nodes: List<Peer>,
    val nodes6: List<Peer>,
    val items: List<InetSocketAddress>
) : Response {


    override fun encode(buffer: Buffer) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        val inner: MutableMap<String, BEObject> = mutableMapOf()
        inner[Names.ID] = BEString(id)
        if (token != null) inner[Names.TOKEN] = BEString(token)
        if (nodes.isNotEmpty()) inner[Names.NODES] = writeBuckets(nodes)
        if (nodes6.isNotEmpty()) inner[Names.NODES6] = writeBuckets(nodes6)
        if (items.isNotEmpty()) {
            val values: List<BEObject> = items.map { it -> BEString(it.encoded()) }
            inner[Names.VALUES] = BEList(values)
        }
        base[Names.R] = BEMap(inner)

        // transaction ID
        base[Names.T] = BEString(tid)

        // message type
        base[Names.Y] = BEString(Names.R.encodeToByteArray())

        encode(base, buffer)
    }
}

private fun InetSocketAddress.encoded(): ByteArray {
    val buffer = Buffer()
    buffer.write(resolveAddress()!!)
    buffer.writeUShort(port.toUShort())
    return buffer.readByteArray()
}

@Suppress("ArrayInDataClass")
internal data class PingRequest(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray
) : Request {

    override fun encode(buffer: Buffer) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        base[Names.A] = BEMap(mapOf<String, BEObject>(Names.ID to BEString(id)))

        // transaction ID
        base[Names.T] = BEString(tid)

        // message type
        base[Names.Y] = BEString(Names.Q.encodeToByteArray())

        // message method
        base[Names.Q] = BEString(Names.PING.encodeToByteArray())

        encode(base, buffer)
    }

}

@Suppress("ArrayInDataClass")
internal data class PingResponse(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray
) : Response {

    override fun encode(buffer: Buffer) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        base[Names.R] = BEMap(mapOf<String, BEObject>(Names.ID to BEString(id)))

        // transaction ID
        base[Names.T] = BEString(tid)
        // message type
        base[Names.Y] = BEString(Names.R.encodeToByteArray())

        // message method if we are a response
        base[Names.IP] = BEString(encode(address))

        encode(base, buffer)
    }

}

