package io.github.remmerw.loki.mdht


import io.github.remmerw.loki.benc.BEObject
import io.github.remmerw.loki.benc.bencode
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Sink


internal interface Message {
    val address: InetSocketAddress
    val id: ByteArray
    val tid: ByteArray
    fun encode(sink: Sink)
}

internal interface Response : Message {
    val ip: ByteArray?
}

internal interface NodesResponse : Response {
    val nodes: List<Peer>
    val nodes6: List<Peer>
}

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

    override fun encode(sink: Sink) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        val inner: MutableMap<String, BEObject> = mutableMapOf()

        inner[Names.ID] = id.bencode()
        inner[Names.INFO_HASH] = infoHash.bencode()
        inner[Names.PORT] = port.toLong().bencode()
        inner[Names.TOKEN] = token.bencode()
        if (name != null) inner[Names.NAME] = name.bencode()
        base[Names.A] = inner.bencode()

        // transaction ID
        base[Names.T] = tid.bencode()
        // message type
        base[Names.Y] = Names.Q.bencode()

        // message method
        base[Names.Q] = Names.ANNOUNCE_PEER.bencode()

        base.bencode().encodeTo(sink)
    }
}

@Suppress("ArrayInDataClass")
internal data class AnnounceResponse(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray,
    override val ip: ByteArray?
) : Response {

    override fun encode(sink: Sink) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        val inner: MutableMap<String, BEObject> = mutableMapOf()
        inner[Names.ID] = id.bencode()
        base[Names.R] = inner.bencode()

        // transaction ID
        base[Names.T] = tid.bencode()
        // message type
        base[Names.Y] = Names.R.bencode()

        base.bencode().encodeTo(sink)
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

    override fun encode(sink: Sink) {


        val base: MutableMap<String, BEObject> = mutableMapOf()

        // transaction ID
        base[Names.T] = tid.bencode()
        // message type
        base[Names.Y] = Names.E.bencode()

        base[Names.E] = listOf(code.bencode(), message.bencode()).bencode()

        base.bencode().encodeTo(sink)
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

    override fun encode(sink: Sink) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        base[Names.A] =
            mapOf<String, BEObject>(
                Names.ID to id.bencode(),
                Names.TARGET to target.bencode()
            ).bencode()


        // transaction ID
        base[Names.T] = tid.bencode()
        // message type
        base[Names.Y] = Names.Q.bencode()
        // message method
        base[Names.Q] = Names.FIND_NODE.bencode()

        base.bencode().encodeTo(sink)
    }

}

@Suppress("ArrayInDataClass")
internal data class FindNodeResponse(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray,
    override val ip: ByteArray?,
    override val nodes: List<Peer>,
    override val nodes6: List<Peer>
) : NodesResponse {

    override fun encode(sink: Sink) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        val inner: MutableMap<String, BEObject> = mutableMapOf()
        inner[Names.ID] = id.bencode()
        if (nodes.isNotEmpty()) inner[Names.NODES] = writeBuckets(nodes)
        if (nodes6.isNotEmpty()) inner[Names.NODES6] = writeBuckets(nodes6)
        base[Names.R] = inner.bencode()

        // transaction ID
        base[Names.T] = tid.bencode()

        // message type
        base[Names.Y] = Names.R.bencode()

        if (ip != null) base[Names.IP] = ip.bencode()

        base.bencode().encodeTo(sink)
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

    override fun encode(sink: Sink) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        base[Names.A] =
            mapOf<String, BEObject>(
                Names.ID to id.bencode(),
                Names.INFO_HASH to infoHash.bencode()
            ).bencode()


        // transaction ID
        base[Names.T] = tid.bencode()

        // message type
        base[Names.Y] = Names.Q.bencode()

        // message method
        base[Names.Q] = Names.GET_PEERS.bencode()

        base.bencode().encodeTo(sink)
    }
}

@Suppress("ArrayInDataClass")
internal data class GetPeersResponse(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray,
    override val ip: ByteArray?,
    val token: ByteArray?,
    override val nodes: List<Peer>,
    override val nodes6: List<Peer>,
    val items: List<Address>
) : NodesResponse {


    override fun encode(sink: Sink) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        val inner: MutableMap<String, BEObject> = mutableMapOf()
        inner[Names.ID] = id.bencode()
        if (token != null) inner[Names.TOKEN] = token.bencode()
        if (nodes.isNotEmpty()) inner[Names.NODES] = writeBuckets(nodes)
        if (nodes6.isNotEmpty()) inner[Names.NODES6] = writeBuckets(nodes6)
        if (items.isNotEmpty()) {
            val values: List<BEObject> = items.map { it -> it.encoded().bencode() }
            inner[Names.VALUES] = values.bencode()
        }
        base[Names.R] = inner.bencode()

        // transaction ID
        base[Names.T] = tid.bencode()

        // message type
        base[Names.Y] = Names.R.bencode()

        if (ip != null) base[Names.IP] = ip.bencode()

        base.bencode().encodeTo(sink)
    }
}


@Suppress("ArrayInDataClass")
internal data class PingRequest(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray
) : Request {

    override fun encode(sink: Sink) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        base[Names.A] = mapOf<String, BEObject>(Names.ID to id.bencode()).bencode()

        // transaction ID
        base[Names.T] = tid.bencode()

        // message type
        base[Names.Y] = Names.Q.bencode()

        // message method
        base[Names.Q] = Names.PING.bencode()

        base.bencode().encodeTo(sink)
    }

}

@Suppress("ArrayInDataClass")
internal data class PingResponse(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray,
    override val ip: ByteArray?
) : Response {

    override fun encode(sink: Sink) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        base[Names.R] = mapOf<String, BEObject>(Names.ID to id.bencode()).bencode()

        // transaction ID
        base[Names.T] = tid.bencode()
        // message type
        base[Names.Y] = Names.R.bencode()


        if (ip != null) base[Names.IP] = ip.bencode()

        base.bencode().encodeTo(sink)
    }

}

@Suppress("ArrayInDataClass")
internal data class PutRequest(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray,
    val token: ByteArray,
    val v: BEObject,
    val cas: Long?,
    val k: ByteArray?,
    val salt: ByteArray?,
    val seq: Long?,
    val sig: ByteArray?

) :
    Request {

    override fun encode(sink: Sink) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        val inner: MutableMap<String, BEObject> = mutableMapOf()

        inner[Names.ID] = id.bencode()
        inner[Names.V] = v
        inner[Names.TOKEN] = token.bencode()
        if (cas != null) inner.put(Names.CAS, cas.bencode())
        if (k != null) inner.put(Names.K, k.bencode())
        if (salt != null) inner.put(Names.SALT, salt.bencode())
        if (seq != null) inner.put(Names.SEQ, seq.bencode())
        if (sig != null) inner.put(Names.SIG, sig.bencode())

        base[Names.A] = inner.bencode()

        // transaction ID
        base[Names.T] = tid.bencode()
        // message type
        base[Names.Y] = Names.Q.bencode()

        // message method
        base[Names.Q] = Names.PUT.bencode()

        base.bencode().encodeTo(sink)
    }
}


@Suppress("ArrayInDataClass")
internal data class PutResponse(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray,
    override val ip: ByteArray?
) : Response {

    override fun encode(sink: Sink) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        val inner: MutableMap<String, BEObject> = mutableMapOf()
        inner[Names.ID] = id.bencode()
        base[Names.R] = inner.bencode()

        // transaction ID
        base[Names.T] = tid.bencode()
        // message type
        base[Names.Y] = Names.R.bencode()

        base.bencode().encodeTo(sink)
    }

}


@Suppress("ArrayInDataClass")
internal data class GetRequest(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray,
    val target: ByteArray,
    val seq: Long?
) :
    Request {

    override fun encode(sink: Sink) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        val inner = mutableMapOf<String, BEObject>(
            Names.ID to id.bencode(),
            Names.TARGET to target.bencode()
        )
        if (seq != null) inner.put(Names.SEQ, seq.bencode())

        base[Names.A] = inner.bencode()

        // transaction ID
        base[Names.T] = tid.bencode()

        // message type
        base[Names.Y] = Names.Q.bencode()

        // message method
        base[Names.Q] = Names.GET.bencode()

        base.bencode().encodeTo(sink)
    }
}

@Suppress("ArrayInDataClass")
internal data class GetResponse(
    override val address: InetSocketAddress,
    override val id: ByteArray,
    override val tid: ByteArray,
    override val ip: ByteArray?,
    val token: ByteArray?,
    override val nodes: List<Peer>,
    override val nodes6: List<Peer>,
    val v: BEObject?,
    val k: ByteArray?,
    val seq: Long?,
    val sig: ByteArray?
) : NodesResponse {


    override fun encode(sink: Sink) {
        val base: MutableMap<String, BEObject> = mutableMapOf()
        val inner: MutableMap<String, BEObject> = mutableMapOf()
        inner[Names.ID] = id.bencode()
        if (token != null) inner[Names.TOKEN] = token.bencode()
        if (nodes.isNotEmpty()) inner[Names.NODES] = writeBuckets(nodes)
        if (nodes6.isNotEmpty()) inner[Names.NODES6] = writeBuckets(nodes6)
        if (v != null) inner[Names.V] = v
        if (k != null) inner.put(Names.K, k.bencode())
        if (seq != null) inner.put(Names.SEQ, seq.bencode())
        if (sig != null) inner.put(Names.SIG, sig.bencode())

        base[Names.R] = inner.bencode()

        // transaction ID
        base[Names.T] = tid.bencode()

        // message type
        base[Names.Y] = Names.R.bencode()

        base.bencode().encodeTo(sink)
    }
}