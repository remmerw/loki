package io.github.remmerw.loki.mdht

import io.github.remmerw.buri.BEInteger
import io.github.remmerw.buri.BEList
import io.github.remmerw.buri.BEMap
import io.github.remmerw.buri.BEObject
import io.github.remmerw.buri.BEString
import io.github.remmerw.buri.bencode
import io.github.remmerw.loki.debug
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readUShort

private fun parseError(
    address: InetSocketAddress,
    map: Map<String, BEObject>
): Message {
    val error = map[Names.E]


    var errorCode = 0
    var errorMsg: String? = null

    if (error is BEString) errorMsg = stringGet(error)
    else if (error is BEList) {
        val errmap = error.toList()
        try {
            errorCode = (errmap[0] as BEInteger).toInt()
            errorMsg = stringGet(errmap[1])
        } catch (_: Exception) {
            // do nothing
        }
    }
    if (errorMsg == null) errorMsg = ""

    var tid = arrayGet(map[Names.T])
    if (tid == null) {
        tid = ByteArray(TID_LENGTH)
    }
    var id = arrayGet(map[Names.ID])
    if (id == null || id.size != SHA1_HASH_LENGTH) {
        id = ByteArray(SHA1_HASH_LENGTH)
    }

    return Error(address, id, tid, errorCode, errorMsg.encodeToByteArray())
}

private fun extractNodes6(
    args: Map<String, BEObject>
): List<Peer> {
    val raw = arrayGet(args[Names.NODES6])
    if (raw == null) return emptyList()
    require(raw.size % NODE_ENTRY_LENGTH_IPV6 == 0) {
        "expected length to be a multiple of " +
                NODE_ENTRY_LENGTH_IPV6 + ", received " + raw.size
    }
    return readBuckets(raw, ADDRESS_LENGTH_IPV6)
}


private fun extractNodes(
    args: Map<String, BEObject>
): List<Peer> {
    val raw = arrayGet(args[Names.NODES])
    if (raw == null) return emptyList()
    require(raw.size % NODE_ENTRY_LENGTH_IPV4 == 0) {
        "expected length to be a multiple of " +
                NODE_ENTRY_LENGTH_IPV4 + ", received " + raw.size
    }
    return readBuckets(raw, ADDRESS_LENGTH_IPV4)
}


internal fun writeBuckets(list: List<Peer>): BEString {
    val buffer = Buffer()
    list.forEach { peer: Peer ->
        val address = peer.address.encoded()!!
        buffer.write(peer.id)
        buffer.write(address)
    }
    return buffer.readByteArray().bencode()
}

internal fun readBuckets(src: ByteArray, length: Int): List<Peer> {
    val buffer = Buffer()
    buffer.write(src)

    val result = mutableListOf<Peer>()
    while (!buffer.exhausted()) {
        val rawId = buffer.readByteArray(SHA1_HASH_LENGTH)
        val raw = buffer.readByteArray(length - 2) // -2 because of port
        val port = buffer.readUShort()
        if (port > 0.toUShort() && port <= 65535.toUShort()) {
            val peer = Peer(
                InetSocketAddress(
                    hostname(raw),
                    port.toInt()
                ),
                rawId
            )
            result.add(peer)
        }
    }
    return result
}


internal fun parseMessage(
    address: InetSocketAddress,
    map: Map<String, BEObject>,
    tidMapper: (ByteArray) -> (Request?),
): Message? {
    val msgType = stringGet(map[Names.Y])

    if (msgType == null) {
        debug("message type (y) missing")
        return null
    }

    return when (msgType) {
        Names.Q -> {
            parseRequest(address, map)
        }

        Names.R -> {
            parseResponse(address, map, tidMapper)
        }

        Names.E -> {
            parseError(address, map)
        }

        else -> {
            debug("unknown RPC type (y=$msgType)")
            return null
        }
    }

}

private fun parseRequest(address: InetSocketAddress, map: Map<String, BEObject>): Message? {

    val ro = roGet(map[Names.RO])
    val tid = arrayGet(map[Names.T])
    checkNotNull(tid) { "missing transaction ID in request" }
    require(tid.isNotEmpty()) { "zero-length transaction ID in request" }

    val root = map[Names.A] as BEMap
    val args = root.toMap()

    val id = arrayGet(args[Names.ID])
    checkNotNull(id) { "missing id" }
    require(id.size == SHA1_HASH_LENGTH) { "invalid node id" }

    val requestMethod = stringGet(map[Names.Q])

    return when (requestMethod) {
        Names.PING -> PingRequest(address, id, tid, ro)
        Names.FIND_NODE, Names.GET_PEERS, Names.GET -> {
            var hash = arrayGet(args[Names.TARGET])
            if (hash == null) {
                hash = arrayGet(args[Names.INFO_HASH])
            }

            if (hash == null) {
                debug("missing/invalid target key in request")
                return null
            }

            if (hash.size != SHA1_HASH_LENGTH) {
                debug("invalid target key in request")
                return null
            }


            return when (requestMethod) {
                Names.FIND_NODE -> FindNodeRequest(address, id, tid, ro, hash)

                Names.GET_PEERS -> GetPeersRequest(address, id, tid, ro, hash)

                Names.GET -> GetRequest(address, id, tid, ro, hash, null) // TODO [Low Priority]

                else -> {
                    debug("not handled branch $requestMethod")
                    return null
                }
            }
        }

        Names.PUT -> {
            val token = arrayGet(args[Names.TOKEN])

            require(token != null) {
                "missing or invalid mandatory arguments (token) for announce"
            }

            require(!token.isEmpty()) {
                "zero-length token in announce_peer request. see BEP33 for reasons why " +
                        "tokens might not have been issued by get_peers response"
            }

            val data = args[Names.V]

            require(data != null) {
                "missing or invalid mandatory arguments (v) for put"
            }


            PutRequest(
                address, id, tid, ro, token, data, null, null,
                null, null, null
            ) // TODO [Low Priority]
        }

        Names.ANNOUNCE_PEER -> {
            val infoHash = arrayGet(args[Names.INFO_HASH])
            checkNotNull(infoHash) {
                "missing info_hash for announce"
            }
            require(infoHash.size == SHA1_HASH_LENGTH) { "invalid info_hash" }


            val port = longGet(args[Names.PORT])
            checkNotNull(port) { "missing port for announce" }
            require(port in 1..65535) { "invalid port" }


            val token = arrayGet(args[Names.TOKEN])

            require(token != null) {
                "missing or invalid mandatory arguments (info_hash, port, token) for announce"
            }

            require(!token.isEmpty()) {
                "zero-length token in announce_peer request. see BEP33 for reasons why " +
                        "tokens might not have been issued by get_peers response"
            }
            val name = arrayGet(args[Names.NAME])
            AnnounceRequest(
                address, id, tid, ro,
                infoHash, port.toInt(), token, name
            )

        }

        else -> {
            debug("method unknown in request")
            return null
        }
    }
}

private fun parseResponse(
    address: InetSocketAddress,
    map: Map<String, BEObject>,
    tidMapper: (ByteArray) -> (Request?)
): Message? {
    val tid = arrayGet(map[Names.T])

    if (tid == null || tid.isEmpty()) {
        debug("missing transaction ID")
        return null
    }

    // responses don't have explicit methods, need to match them to a request to figure that one out
    val request = tidMapper.invoke(tid)
    if (request == null) {
        debug("response does not have a known request (tid)")
        return null
    }
    return parseResponse(address, map, request, tid)
}


private fun parseResponse(
    address: InetSocketAddress,
    map: Map<String, BEObject>,
    request: Request, tid: ByteArray
): Message? {
    val inner = map[Names.R]
    if (inner !is BEMap) {
        return null
    }
    val args = inner.toMap()

    val id = arrayGet(args[Names.ID])
    require(id != null) { "mandatory parameter 'id' missing" }
    require(id.size == SHA1_HASH_LENGTH) { "invalid or missing origin ID" }

    val ip = arrayGet(map[Names.IP])

    val msg: Message

    when (request) {
        is PingRequest -> msg = PingResponse(address, id, tid, ip)
        is PutRequest -> msg = PutResponse(address, id, tid, ip)
        is GetRequest -> {
            val token = arrayGet(args[Names.TOKEN])
            val nodes6 = extractNodes6(args)
            val nodes = extractNodes(args)
            val data = args[Names.V]
            val k = arrayGet(args[Names.K])
            val sec = longGet(args[Names.SEQ])
            val sig = arrayGet(args[Names.SIG])
            return GetResponse(
                address, id, tid, token, ip,
                nodes, nodes6, data, k, sec, sig
            )
        }

        is AnnounceRequest -> msg = AnnounceResponse(address, id, tid, ip)
        is FindNodeRequest -> {
            val nodes6 = extractNodes6(args)
            val nodes = extractNodes(args)
            msg = FindNodeResponse(address, id, tid, ip, nodes, nodes6)
        }

        is GetPeersRequest -> {
            val token = arrayGet(args[Names.TOKEN])
            val nodes6 = extractNodes6(args)
            val nodes = extractNodes(args)
            val addresses: MutableList<Address> = mutableListOf()

            var vals: List<ByteArray> = listOf()
            val values = args[Names.VALUES]
            if (values != null) {
                vals = (values as BEList).toList().map { it ->
                    (it as BEString).toByteArray()
                }
            }


            if (vals.isNotEmpty()) {
                for (i in vals.indices) {
                    // only accept ipv4 or ipv6 for now
                    val length = vals[i].size
                    when (length) {
                        ADDRESS_LENGTH_IPV4 -> {
                            val buffer = vals[i]

                            val address: ByteArray = buffer.copyOfRange(0, 4)
                            val port: UShort = ((buffer[4]
                                .toInt() and 0xFF) shl 8 or (buffer[5].toInt() and 0xFF)).toUShort()

                            if (port > 0.toUShort() && port <= 65535.toUShort()) {
                                addresses.add(
                                    Address(address, port)
                                )
                            }
                        }

                        ADDRESS_LENGTH_IPV6 -> {
                            val buffer = vals[i]

                            val address: ByteArray = buffer.copyOfRange(0, 16)
                            val port: UShort = ((buffer[16]
                                .toInt() and 0xFF) shl 8 or (buffer[17].toInt() and 0xFF)).toUShort()

                            if (port > 0.toUShort() && port <= 65535.toUShort()) {
                                addresses.add(
                                    Address(address, port)
                                )
                            }
                        }

                        else -> {
                            debug("not accepted address length")
                            return null
                        }
                    }
                }
            }
            return GetPeersResponse(
                address, id, tid, ip,
                token, nodes, nodes6, addresses
            )
        }

        else -> {
            debug("not handled request response")
            return null
        }
    }

    return msg
}


private fun numericInet4(address: ByteArray): String {
    return (address[0].toInt() and 255).toString() + "." +
            (address[1].toInt() and 255) + "." +
            (address[2].toInt() and 255) + "." + (address[3].toInt() and 255)
}

private fun numericInet6(address: ByteArray): String {
    val builder = StringBuilder(39)

    for (i in 0 until 8) {
        val highByte = address[i * 2].toInt() and 0xFF
        val lowByte = address[i * 2 + 1].toInt() and 0xFF
        val segment = (highByte shl 8) or lowByte
        builder.append(segment.toString(16))

        if (i < 7) {
            builder.append(":")
        }
    }

    return builder.toString()
}

internal fun hostname(address: ByteArray): String {
    if (address.size == 4) {
        return numericInet4(address)
    }
    if (address.size == 16) {
        return numericInet6(address)
    }
    throw Exception("Invalid address")
}


fun stringGet(beObject: BEObject?): String? {
    if (beObject == null) {
        return null
    }

    if (beObject is BEString) {
        return beObject.toString()
    }
    return null
}

fun arrayGet(beObject: BEObject?): ByteArray? {
    if (beObject == null) {
        return null
    }

    if (beObject is BEString) {
        return beObject.toByteArray()
    }
    return null
}

fun longGet(beObject: BEObject?): Long? {
    if (beObject == null) {
        return null
    }
    if (beObject is BEInteger) {
        return beObject.toLong()
    }
    return null
}

fun roGet(beObject: BEObject?): Boolean {
    if (beObject == null) {
        return false
    }
    if (beObject is BEInteger) {
        return beObject.toInt() == 1
    }
    return false
}

internal const val GENERIC_ERROR = 201
internal const val SERVER_ERROR = 202
internal const val PROTOCOL_ERROR = 203