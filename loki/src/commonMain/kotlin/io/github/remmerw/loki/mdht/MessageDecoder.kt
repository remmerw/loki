package io.github.remmerw.loki.mdht

import io.github.remmerw.loki.buri.BEInteger
import io.github.remmerw.loki.buri.BEList
import io.github.remmerw.loki.buri.BEMap
import io.github.remmerw.loki.buri.BEObject
import io.github.remmerw.loki.buri.BEString
import io.github.remmerw.loki.buri.arrayGet
import io.github.remmerw.loki.buri.longGet
import io.github.remmerw.loki.buri.stringGet
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
        val errmap = error.list
        try {
            errorCode = (errmap[0] as BEInteger).value.toInt()
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

@Throws(MessageException::class)
private fun extractNodes6(
    args: Map<String, BEObject>
): List<Peer> {
    val raw = arrayGet(args[Names.NODES6])
    if (raw == null) return emptyList()
    if (raw.size % NODE_ENTRY_LENGTH_IPV6 != 0) throw MessageException(
        "expected length to be a multiple of " +
                NODE_ENTRY_LENGTH_IPV6 + ", received " + raw.size,
        PROTOCOL_ERROR
    )
    return readBuckets(raw, ADDRESS_LENGTH_IPV6)
}


@Throws(MessageException::class)
private fun extractNodes(
    args: Map<String, BEObject>
): List<Peer> {
    val raw = arrayGet(args[Names.NODES])
    if (raw == null) return emptyList()
    if (raw.size % NODE_ENTRY_LENGTH_IPV4 != 0) throw MessageException(
        "expected length to be a multiple of " +
                NODE_ENTRY_LENGTH_IPV4 + ", received " + raw.size,
        PROTOCOL_ERROR
    )
    return readBuckets(raw, ADDRESS_LENGTH_IPV4)
}


internal fun writeBuckets(list: List<Peer>): BEString {
    val buffer = Buffer()
    list.forEach { peer: Peer ->
        val address = encode(peer.address)
        buffer.write(peer.id)
        buffer.write(address)
    }
    return BEString(buffer.readByteArray())
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


@Throws(MessageException::class)
internal fun parseMessage(
    address: InetSocketAddress,
    map: Map<String, BEObject>,
    tidMapper: (ByteArray) -> (Request?),
): Message {
    val msgType = stringGet(map[Names.Y])

    if (msgType == null) {
        throw MessageException("message type (y) missing", PROTOCOL_ERROR)
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

        else -> throw MessageException("unknown RPC type (y=$msgType)", GENERIC_ERROR)
    }

}

@Throws(MessageException::class)
private fun parseRequest(address: InetSocketAddress, map: Map<String, BEObject>): Message {
    val root = map[Names.A] as? BEMap ?: throw MessageException(
        "expected a bencoded dictionary under key a", PROTOCOL_ERROR
    )

    val args = root.map

    val tid = arrayGet(map[Names.T])
    checkNotNull(tid) { "missing transaction ID in request" }
    require(tid.isNotEmpty()) { "zero-length transaction ID in request" }

    val id = arrayGet(args[Names.ID])
    checkNotNull(id) { "missing id" }
    require(id.size == SHA1_HASH_LENGTH) { "invalid node id" }

    val requestMethod = stringGet(map[Names.Q])

    return when (requestMethod) {
        Names.PING -> PingRequest(address, id, tid)
        Names.FIND_NODE, Names.GET_PEERS -> {
            var hash = arrayGet(args[Names.TARGET])
            if (hash == null) {
                hash = arrayGet(args[Names.INFO_HASH])
            }

            if (hash == null) {
                throw MessageException(
                    "missing/invalid target key in request",
                    PROTOCOL_ERROR
                )
            }

            if (hash.size != SHA1_HASH_LENGTH) {
                throw MessageException(
                    "invalid target key in request",
                    PROTOCOL_ERROR
                )
            }


            return when (requestMethod) {
                Names.FIND_NODE -> FindNodeRequest(address, id, tid, hash)

                Names.GET_PEERS -> GetPeersRequest(address, id, tid, hash)

                else -> throw IllegalStateException("not handled branch")
            }
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

            if (token == null) throw MessageException(
                "missing or invalid mandatory arguments (info_hash, port, token) for announce",
                PROTOCOL_ERROR
            )
            if (token.isEmpty()) throw MessageException(
                "zero-length token in announce_peer request. see BEP33 for reasons why " +
                        "tokens might not have been issued by get_peers response",
                PROTOCOL_ERROR
            )
            val name = arrayGet(args[Names.NAME])
            AnnounceRequest(address, id, tid, infoHash, port.toInt(), token, name)

        }

        else -> {
            throw MessageException(
                "method unknown in request",
                METHOD_UNKNOWN
            )
        }
    }
}

@Throws(MessageException::class)
private fun parseResponse(
    address: InetSocketAddress,
    map: Map<String, BEObject>,
    tidMapper: (ByteArray) -> (Request?)
): Message {
    val tid = arrayGet(map[Names.T])
    if (tid == null || tid.isEmpty()) throw MessageException(
        "missing transaction ID",
        PROTOCOL_ERROR
    )

    // responses don't have explicit methods, need to match them to a request to figure that one out
    val request = tidMapper.invoke(tid)
    if (request == null) throw MessageException(
        "unknown message type",
        PROTOCOL_ERROR
    )
    return parseResponse(address, map, request, tid)
}


@Throws(MessageException::class)
private fun parseResponse(
    address: InetSocketAddress,
    map: Map<String, BEObject>,
    request: Request, tid: ByteArray
): Message {
    val args = (map[Names.R] as BEMap).map

    val id = arrayGet(args[Names.ID])
        ?: throw MessageException(
            "mandatory parameter 'id' missing",
            PROTOCOL_ERROR
        )

    if (id.size != SHA1_HASH_LENGTH) {
        throw MessageException("invalid or missing origin ID", PROTOCOL_ERROR)
    }

    val msg: Message

    when (request) {
        is PingRequest -> msg = PingResponse(address, id, tid)
        is AnnounceRequest -> msg = AnnounceResponse(address, id, tid)
        is FindNodeRequest -> {
            if (!args.containsKey(Names.NODES) && !args.containsKey(Names.NODES6)) throw MessageException(
                "received response to find_node request with " +
                        "neither 'nodes' nor 'nodes6' entry", PROTOCOL_ERROR
            )
            val nodes6 = extractNodes6(args)
            val nodes = extractNodes(args)
            msg = FindNodeResponse(address, id, tid, nodes, nodes6)
        }

        is GetPeersRequest -> {
            val token = arrayGet(args[Names.TOKEN])
            val nodes6 = extractNodes6(args)
            val nodes = extractNodes(args)
            val addresses: MutableList<Address> = mutableListOf()

            var vals: List<ByteArray> = listOf()
            val values = args[Names.VALUES]
            if (values != null) {
                if (values is BEList) {
                    vals = values.list.map { it ->
                        (it as BEString).content
                    }
                } else {
                    throw MessageException(
                        "expected 'values' " +
                                "field in get_peers to be list of strings",
                        PROTOCOL_ERROR
                    )
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

                            addresses.add(Address(address, port))
                        }

                        ADDRESS_LENGTH_IPV6 -> {
                            val buffer = vals[i]

                            val address: ByteArray = buffer.copyOfRange(0, 16)
                            val port: UShort = ((buffer[16]
                                .toInt() and 0xFF) shl 8 or (buffer[17].toInt() and 0xFF)).toUShort()

                            addresses.add(Address(address, port))
                        }

                        else -> {
                            debug(
                                "MessageDecoder",
                                "not accepted node " + vals[i].contentToString()
                            )
                        }
                    }
                }
            }

            if (addresses.isNotEmpty() || nodes6.isNotEmpty() || nodes.isNotEmpty()) {
                msg = GetPeersResponse(address, id, tid, token, nodes, nodes6, addresses)
            } else {
                throw MessageException(
                    "Neither nodes nor values in get_peers response",
                    PROTOCOL_ERROR
                )
            }
        }

        else -> {
            throw MessageException(
                "not handled request response",
                PROTOCOL_ERROR
            )
        }
    }


    val ip = arrayGet(map[Names.IP]) // not yet used
    if (ip != null) {
        val addr = unpackAddress(ip)
        if (addr != null) {
            debug("MessageDecoder", "External IP: $addr")
        }
    }

    return msg
}


private fun unpackAddress(raw: ByteArray): Address? {
    if (raw.size != 6 && raw.size != 18) return null
    val buffer = Buffer()
    buffer.write(raw)
    val rawIP = buffer.readByteArray(raw.size - 2)
    val port = buffer.readUShort()
    return Address(rawIP, port)
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

internal const val GENERIC_ERROR = 201
internal const val SERVER_ERROR = 202
internal const val PROTOCOL_ERROR = 203
internal const val METHOD_UNKNOWN = 204