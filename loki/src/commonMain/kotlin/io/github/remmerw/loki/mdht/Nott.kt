package io.github.remmerw.loki.mdht

import io.github.remmerw.buri.BEObject
import io.github.remmerw.buri.decodeBencodeToMap
import io.github.remmerw.loki.debug
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.util.collections.ConcurrentMap
import io.ktor.util.sha1
import io.ktor.utils.io.core.remaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.writeUShort
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

class Nott(val peerId: ByteArray, val port: Int, val readOnlyState: Boolean = true) {

    private val unsolicitedThrottle: MutableMap<InetSocketAddress, Long> =
        mutableMapOf() // runs in same thread

    private val requestCalls: ConcurrentMap<Int, Call> = ConcurrentMap()

    private val database: Database = Database()
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private var socket: BoundDatagramSocket? = null
    private val routingTable = RoutingTable()

    suspend fun startup() {
        socket = aSocket(selectorManager).udp().bind(
            InetSocketAddress("::", port)
        )

        selectorManager.launch {
            while (isActive) {
                val datagram = socket!!.receive()
                handleDatagram(datagram)
            }
        }
    }

    internal fun closestPeers(key: ByteArray, take: Int): List<Peer> {
        return routingTable.closestPeers(key, take)
    }

    private suspend fun send(es: EnqueuedSend) {

        try {
            val buffer = Buffer()
            es.message.encode(buffer)
            val address = es.message.address


            val datagram = Datagram(buffer, address)

            socket!!.send(datagram)

            es.associatedCall?.hasSend()

        } catch (throwable: Throwable) {
            debug("Mdht", throwable)

            if (es.associatedCall != null) {
                es.associatedCall.injectStall()
                timeout(es.associatedCall)
            }
        }
    }

    fun shutdown() {

        try {
            socket?.close()
        } catch (throwable: Throwable) {
            debug("Mdht", throwable)
        }

        try {
            selectorManager.close()
        } catch (throwable: Throwable) {
            debug("Mdht", throwable)
        }
    }


    internal fun timeout(call: Call) {
        requestCalls.remove(call.request.tid.contentHashCode())

        // don't timeout anything if we don't have a connection
        if (call.expectedID != null) {
            routingTable.onTimeout(
                call.expectedID
            )
        }
    }

    internal suspend fun ping(request: PingRequest) {

        // ignore requests we get from ourself
        if (isLocalId(request.id)) {
            return
        }

        val rsp = PingResponse(
            address = request.address,
            id = peerId,
            tid = request.tid,
            ip = request.address.encoded()
        )

        sendMessage(rsp)

    }

    internal suspend fun findNode(request: FindNodeRequest) {
        // ignore requests we get from ourself
        if (isLocalId(request.id)) {
            return
        }

        val entries = routingTable.closestPeers(request.target, 8)

        val response = FindNodeResponse(
            address = request.address,
            id = peerId,
            tid = request.tid,
            ip = request.address.encoded(),
            nodes = entries.filter { peer: Peer ->
                peer.address.resolveAddress()?.size == 4
            },
            nodes6 = entries.filter { peer: Peer ->
                peer.address.resolveAddress()?.size == 16
            }
        )

        sendMessage(response)


    }


    internal suspend fun getPeers(request: GetPeersRequest) {
        // ignore requests we get from ourself
        if (isLocalId(request.id)) {
            return
        }

        val values = database.sample(request.infoHash, MAX_PEERS_PER_ANNOUNCE)

        // generate a token
        var token: ByteArray? = null

        if (database.insertForKeyAllowed(request.infoHash)) token =
            database.generateToken(
                request.id,
                request.address.encoded()!!,
                request.infoHash
            )


        val entries = routingTable.closestPeers(request.infoHash, 8)


        val resp = GetPeersResponse(
            address = request.address,
            id = peerId,
            tid = request.tid,
            ip = request.address.encoded(),
            token = token,
            nodes = entries.filter { peer: Peer ->
                peer.address.resolveAddress()?.size == 4
            },
            nodes6 = entries.filter { peer: Peer ->
                peer.address.resolveAddress()?.size == 16
            },
            values = values
        )

        sendMessage(resp)

    }

    internal suspend fun get(request: GetRequest) {
        // ignore requests we get from ourself
        if (isLocalId(request.id)) {
            return
        }


        // generate a token
        var token: ByteArray? = null
        if (database.insertForKeyAllowed(request.target)) token =
            database.generateToken(
                request.id,
                request.address.encoded()!!,
                request.target
            )


        val entries = routingTable.closestPeers(request.target, 8)


        val resp = GetResponse(
            address = request.address,
            id = peerId,
            tid = request.tid,
            ip = request.address.encoded(),
            token = token,
            nodes = entries.filter { peer: Peer ->
                peer.address.resolveAddress()?.size == 4
            },
            nodes6 = entries.filter { peer: Peer ->
                peer.address.resolveAddress()?.size == 16
            },
            null, null, null, null // TODO [Low Priority]
        )

        sendMessage(resp)

    }

    internal suspend fun put(request: PutRequest) {
        // ignore requests we get from ourself
        if (isLocalId(request.id)) {
            return
        }

        val buffer = Buffer()
        request.v.encodeTo(buffer)
        val data = buffer.readByteArray()

        // first check if the token is OK
        if (!database.checkToken(
                request.token,
                request.id,
                request.address.encoded()!!,
                sha1(data)
            )
        ) {
            sendError(
                request, PROTOCOL_ERROR,
                "Invalid Token; tokens expire after " + TOKEN_TIMEOUT + "ms; " +
                        "only valid for the IP/port to which it was issued;" +
                        " only valid for the info hash for which it was issued"
            )
            return
        }

        // Note: right now no data is stored (someday in the future, when server is supported)


        // send a proper response to indicate everything is OK
        val rsp = PutResponse(
            address = request.address,
            id = peerId,
            tid = request.tid,
            ip = request.address.encoded()
        )
        sendMessage(rsp)

    }

    internal suspend fun announce(request: AnnounceRequest) {
        // ignore requests we get from ourself
        if (isLocalId(request.id)) {
            return
        }

        // first check if the token is OK
        if (!database.checkToken(
                request.token,
                request.id,
                request.address.encoded()!!,
                request.infoHash
            )
        ) {
            sendError(
                request, PROTOCOL_ERROR,
                "Invalid Token; tokens expire after " + TOKEN_TIMEOUT + "ms; " +
                        "only valid for the IP/port to which it was issued;" +
                        " only valid for the info hash for which it was issued"
            )
            return
        }

        // everything OK, so store the value
        val address = Address(
            request.address.resolveAddress()!!,
            request.address.port.toUShort()
        )

        database.store(request.infoHash, address)


        // send a proper response to indicate everything is OK
        val rsp = AnnounceResponse(
            address = request.address,
            id = peerId,
            tid = request.tid,
            ip = request.address.encoded()
        )
        sendMessage(rsp)

    }


    internal suspend fun sendError(origMsg: Message, code: Int, msg: String) {
        sendMessage(
            Error(
                address = origMsg.address,
                id = peerId,
                tid = origMsg.tid,
                code = code,
                message = msg.encodeToByteArray()
            )
        )
    }


    internal fun recieved(msg: Message, associatedCall: Call?) {
        val ip = msg.address
        val id = msg.id

        if (msg is Request) {
            if (msg.ro) {
                // A node that receives DHT messages should inspect incoming queries for the 'ro'
                // flag set to 1. If it is found, the node should not add the message sender
                // to its routing table.
                return
            }
        }

        /* Someday in the future the IP might be used in the implementation
        if (msg is Response) {
           val ip = msg.ip
           if (ip != null) {

               val buffer = Buffer()
               buffer.write(ip)
               val rawIP = buffer.readByteArray(ip.size - 2)
               val port = buffer.readUShort()
               val addr = createInetSocketAddress(rawIP, port.toInt())

                debug("My IP " + addr.hostname)
            }
        }*/

        val expectedId = associatedCall?.expectedID

        // server only verifies IP equality for responses.
        // we only want remote nodes with stable ports in our routing table, so appley a stricter check here
        if (associatedCall != null &&
            associatedCall.request.address != associatedCall.response!!.address
        ) {
            return
        }


        val entryById = routingTable.findPeerById(id)

        // entry is claiming the same ID as entry with different IP in our routing table -> ignore
        if (entryById != null && entryById.address != ip) return

        // ID mismatch from call (not the same as ID mismatch from routing table)
        // it's fishy at least. don't insert even if it proves useful during a lookup
        if (entryById == null && expectedId != null && !expectedId.contentEquals(id)) return

        val newEntry = Peer(msg.address, id)
        // throttle the insert-attempts for unsolicited requests, update-only once they exceed the threshold
        // does not apply to responses
        if (associatedCall == null && updateAndCheckThrottle(newEntry.address)) {

            routingTable.refresh(newEntry)
            return
        }

        if (associatedCall != null) {
            newEntry.signalResponse()
        }


        if (!peerId.contentEquals(newEntry.id)) {
            routingTable.insertOrRefresh(newEntry)
        }


        // we already should have the bucket. might be an old one by now due to splitting
        // but it doesn't matter, we just need to update the entry, which should stay the
        // same object across bucket splits
        if (msg is Response) {
            if (associatedCall != null) {
                routingTable.notifyOfResponse(msg)
            }
        }
    }

    /**
     * @return true if it should be throttled
     */
    private fun updateAndCheckThrottle(addr: InetSocketAddress): Boolean {

        val oldValue: Long? = unsolicitedThrottle[addr]
        val newValue: Long = if (oldValue == null) {
            THROTTLE_INCREMENT
        } else {
            min((oldValue + THROTTLE_INCREMENT), THROTTLE_SATURATION)
        }
        unsolicitedThrottle.put(addr, newValue)

        return (newValue - THROTTLE_INCREMENT) > THROTTLE_THRESHOLD
    }


    internal fun isLocalId(id: ByteArray): Boolean {
        return peerId.contentEquals(id)
    }


    internal suspend fun doRequestCall(call: Call) {

        requestCalls.put(call.request.tid.contentHashCode(), call)

        val es = EnqueuedSend(call.request, call)


        send(es)

    }

    suspend fun ping(address: InetSocketAddress, id: ByteArray?) {
        val tid = createRandomKey(TID_LENGTH)
        val pr = PingRequest(
            address = address,
            id = peerId,
            tid = tid,
            ro = readOnlyState
        )
        doRequestCall(Call(pr, id)) // expectedId can not be available (only address is known)
    }

    suspend fun handleDatagram(datagram: Datagram) {
        val inet = datagram.address as InetSocketAddress
        val source = datagram.packet


        // * no conceivable DHT message is smaller than 10 bytes
        // * port 0 is reserved
        // -> immediately discard junk on the read loop, don't even allocate a buffer for it
        if (source.remaining < 10 || inet.port == 0) return

        handlePacket(source, inet)
    }

    private suspend fun handlePacket(source: Source, address: InetSocketAddress) {

        val map: Map<String, BEObject>
        try {
            map = decodeBencodeToMap(source)
        } catch (throwable: Throwable) {
            debug("Node", throwable)
            return
        }

        val msg: Message
        try {
            msg = parseMessage(address, map) { tid: ByteArray ->
                requestCalls[tid.contentHashCode()]?.request
            } ?: return
        } catch (throwable: Throwable) {
            debug("Node", throwable)
            return
        }

        // just respond to incoming requests, no need to match them to pending requests
        if (msg is Request) {

            // if readOnlyState is true we are in "Read-Only State"
            // It no longer responds to 'query' messages that it receives,
            // that is messages containing a 'q' flag in the top-level dictionary.
            if (!readOnlyState) {
                when (msg) {
                    is PutRequest -> put(msg)
                    is GetRequest -> get(msg)
                    is AnnounceRequest -> announce(msg)
                    is FindNodeRequest -> findNode(msg)
                    is GetPeersRequest -> getPeers(msg)
                    is PingRequest -> ping(msg)
                }
            }
            recieved(msg, null)
            return
        }

        // check if this is a response to an outstanding request
        val call = requestCalls[msg.tid.contentHashCode()]


        // message matches transaction ID and origin == destination
        if (call != null) {
            // we only check the IP address here. the routing table applies more strict
            // checks to also verify a stable port
            if (call.request.address == msg.address) {
                // remove call first in case of exception

                requestCalls.remove(msg.tid.contentHashCode())


                call.response(msg)


                // apply after checking for a proper response
                if (msg is Response) {

                    recieved(msg, call)
                }
                return
            }

            // 1. the message is not a request
            // 2. transaction ID matched
            // 3. request destination did not match response source!!
            // 4. we're using random 48 bit MTIDs
            // this happening by chance is exceedingly unlikely

            // indicates either port-mangling NAT, a multhomed host listening on
            // any-local address or some kind of attack
            // -> ignore response

            debug(
                "tid matched, socket address did not, ignoring message, request: "
                        + call.request.address + " -> response: " + msg.address
            )


            if (msg !is Error) {
                // this is more likely due to incorrect binding implementation in ipv6. notify peers about that
                // don't bother with ipv4, there are too many complications
                val err: Message = Error(
                    call.request.address, peerId,
                    msg.tid, GENERIC_ERROR,
                    ("A request was sent to " + call.request.address +
                            " and a response with matching transaction id was received from "
                            + msg.address + " . Multihomed nodes should ensure that sockets are " +
                            "properly bound and responses are sent with the " +
                            "correct source socket address. See BEPs 32 and 45.").encodeToByteArray()
                )

                sendMessage(err)
            }

            // but expect an upcoming timeout if it's really just a misbehaving node
            call.setSocketMismatch()
            call.injectStall()

            return
        }

        // a) it's a response b) didn't find a call c) uptime is high enough that
        // it's not a stray from a restart
        // -> did not expect this response
        if (msg is Response) {

            val err = Error(
                address = msg.address,
                id = peerId,
                tid = msg.tid,
                code = SERVER_ERROR,
                message = ("received a response message whose transaction ID did not " +
                        "match a pending request or transaction expired").encodeToByteArray()
            )
            sendMessage(err)
            return
        }


        if (msg is Error) {
            val b = StringBuilder()
            b.append(" [").append(msg.code).append("] from: ").append(msg.address)
            b.append(" Message: \"").append(msg.message).append("\"")
            debug("ErrorMessage $b")
            return
        }

        debug("not sure how to handle message $msg")
    }


    internal suspend fun sendMessage(msg: Message) {
        requireNotNull(msg.address) { "message destination must not be null" }

        send(EnqueuedSend(msg, null))
    }
}

internal data class EnqueuedSend(val message: Message, val associatedCall: Call?)

internal const val TID_LENGTH = 6


// 5 timeouts, used for exponential backoff as per kademlia paper
internal const val MAX_TIMEOUTS = 5

// haven't seen it for a long time + timeout == evict sooner than pure timeout
// based threshold. e.g. for old entries that we haven't touched for a long time
internal const val OLD_AND_STALE_TIME = 15 * 60 * 1000
internal const val OLD_AND_STALE_TIMEOUTS = 2

// DHT
internal const val RESPONSE_TIMEOUT = 3000
internal const val MAX_ENTRIES_PER_BUCKET: Int = 8
internal const val TOKEN_TIMEOUT: Int = 5 * 60 * 1000
internal const val MAX_DB_ENTRIES_PER_KEY: Int = 6000
internal const val MAX_PEERS_PER_ANNOUNCE: Int = 10
internal const val SHA1_HASH_LENGTH: Int = 20
internal const val ADDRESS_LENGTH_IPV6 = 16 + 2
internal const val ADDRESS_LENGTH_IPV4 = 4 + 2
internal const val NODE_ENTRY_LENGTH_IPV6 = ADDRESS_LENGTH_IPV6 + SHA1_HASH_LENGTH
internal const val NODE_ENTRY_LENGTH_IPV4 = ADDRESS_LENGTH_IPV4 + SHA1_HASH_LENGTH

// -1 token per minute, 60 saturation, 30 threshold
// if we see more than 1 per minute then it'll take 30 minutes until an
// unsolicited request can go into a replacement bucket again
internal const val THROTTLE_INCREMENT: Long = 10

/*
* Verification Strategy:
*
* - trust incoming requests less than responses to outgoing requests
* - most outgoing requests will have an expected ID - expected ID may come from external nodes,
* so don't take it at face value
*  - if response does not match expected ID drop the packet for routing table accounting
* purposes without penalizing any existing routing table entry
* - map routing table entries to IP addresses
*  - verified responses trump unverified entries
*  - lookup all routing table entry for incoming messages based on IP address (not node ID!)
*  and ignore them if ID does not match
*  - also ignore if port changed
*  - drop, not just ignore, if we are sure that the incoming message is not fake
* (tid-verified response)
* - allow duplicate addresses for unverified entries
*  - scrub later when one becomes verified
* - never hand out unverified entries to other nodes
*
* other stuff to keep in mind:
*
* - non-reachable nodes may spam -> floods replacements -> makes it hard to get proper
* replacements without active lookups
*
*/
internal const val THROTTLE_SATURATION: Long = 60
internal const val THROTTLE_THRESHOLD: Long = 30


// returns the newer timestamp
internal fun newerTimeMark(mark: ValueTimeMark?, cmp: ValueTimeMark?): ValueTimeMark? {
    if (mark == null) {
        return cmp
    }
    if (cmp == null) {
        return mark
    }
    val markElapsed = mark.elapsedNow().inWholeMilliseconds
    val cmpElapsed = cmp.elapsedNow().inWholeMilliseconds
    return if (markElapsed < cmpElapsed) mark else cmp
}


internal fun mismatch(a: ByteArray, b: ByteArray): Int {
    val min = min(a.size, b.size)
    for (i in 0 until min) {
        if (a[i] != b[i]) return i
    }

    return if (a.size == b.size) -1 else min
}


internal fun InetSocketAddress.encoded(): ByteArray? {
    val address = this.resolveAddress()
    if (address != null) {
        val buffer = Buffer()
        buffer.write(address)
        buffer.writeUShort(this.port.toUShort())
        return buffer.readByteArray()
    }
    return null
}


suspend fun newNott(peerId: ByteArray, port: Int, bootstrap: List<InetSocketAddress>): Nott {
    val nott = Nott(peerId, port)
    nott.startup()

    bootstrap.forEach { address: InetSocketAddress ->
        nott.ping(address, null)
    }
    return nott
}

fun peerId(): ByteArray {
    val peerId = ByteArray(SHA1_HASH_LENGTH)
    peerId[0] = '-'.code.toByte()
    peerId[1] = 'T'.code.toByte()
    peerId[2] = 'H'.code.toByte()
    peerId[3] = '0'.code.toByte()
    peerId[4] = '8'.code.toByte()
    peerId[5] = '1'.code.toByte()
    peerId[6] = '5'.code.toByte()
    peerId[7] = '-'.code.toByte()
    return Random.nextBytes(peerId, 8)
}