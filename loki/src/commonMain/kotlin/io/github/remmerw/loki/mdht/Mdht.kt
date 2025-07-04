package io.github.remmerw.loki.mdht

import io.github.remmerw.loki.buri.BEObject
import io.github.remmerw.loki.buri.decode
import io.github.remmerw.loki.debug
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.util.collections.ConcurrentMap
import io.ktor.utils.io.core.remaining
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.writeUShort
import kotlin.concurrent.Volatile
import kotlin.math.min
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

internal class Mdht(val peerId: ByteArray, val port: Int) {
    private var numEntriesInRoutingTable: Int = 0

    private val unsolicitedThrottle: MutableMap<InetSocketAddress, Long> =
        mutableMapOf() // runs in same thread


    // keeps track of RTT histogram for nodes not in our routing table
    val timeoutFilter: ResponseTimeoutFilter = ResponseTimeoutFilter()
    private val requestCalls: ConcurrentMap<Int, Call> = ConcurrentMap()

    private val database: Database = Database()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private var socket: BoundDatagramSocket? = null


    @Volatile
    var routingTable = RoutingTable()

    suspend fun startup(addresses: List<InetSocketAddress>) {
        socket = aSocket(selectorManager).udp().bind(
            InetSocketAddress("::", port)
        )

        scope.launch {
            while (isActive) {
                val datagram = socket!!.receive()
                handleDatagram(datagram)
            }
        }

        addresses.forEach { address: InetSocketAddress ->
            ping(address, null)
        }

    }

    private suspend fun send(es: EnqueuedSend) {
        // simply assume nobody else is writing and attempt to do it
        // if it fails it's the current writer's job to double-check after releasing the write lock

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
            scope.cancel()
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
            routingTable.entryForId(call.expectedID).bucket.onTimeout(
                call.request.address
            )
        }
    }

    suspend fun ping(request: PingRequest) {

        // ignore requests we get from ourself
        if (isLocalId(request.id)) {
            return
        }

        val rsp = PingResponse(request.address, peerId, request.tid)

        sendMessage(rsp)

        recieved(request, null)
    }

    suspend fun findNode(request: FindNodeRequest) {
        // ignore requests we get from ourself
        if (isLocalId(request.id)) {
            return
        }

        val kns = ClosestSearch(request.target, MAX_ENTRIES_PER_BUCKET, this)
        kns.fill { peer: Peer -> peer.eligibleForNodesList() }
        val response = FindNodeResponse(
            request.address, peerId, request.tid,
            kns.inet4List(),
            kns.inet6List()
        )

        sendMessage(response)

        recieved(request, null)
    }


    suspend fun getPeers(request: GetPeersRequest) {
        // ignore requests we get from ourself
        if (isLocalId(request.id)) {
            return
        }

        val addresses = database.sample(request.infoHash, MAX_PEERS_PER_ANNOUNCE)

        // generate a token
        var token: ByteArray? = null
        if (database.insertForKeyAllowed(request.infoHash)) token =
            database.generateToken(
                request.id,
                encode(request.address),
                request.infoHash
            )


        val kns = ClosestSearch(request.infoHash, MAX_ENTRIES_PER_BUCKET, this)
        kns.fill { peer: Peer -> peer.eligibleForNodesList() }


        val resp = GetPeersResponse(
            request.address, peerId, request.tid, token,
            kns.inet4List(),
            kns.inet6List(),
            addresses
        )

        sendMessage(resp)

        recieved(request, null)
    }

    suspend fun announce(request: AnnounceRequest) {
        // ignore requests we get from ourself
        if (isLocalId(request.id)) {
            return
        }

        // first check if the token is OK
        if (!database.checkToken(
                request.token,
                request.id,
                encode(request.address),
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
        database.store(
            request.infoHash,
            transform(request.address)
        )

        // send a proper response to indicate everything is OK
        val rsp = AnnounceResponse(request.address, peerId, request.tid)
        sendMessage(rsp)

        recieved(request, null)
    }


    suspend fun sendError(origMsg: Message, code: Int, msg: String) {
        sendMessage(
            Error(
                origMsg.address, peerId, origMsg.tid, code,
                msg.encodeToByteArray()
            )
        )
    }


    suspend fun recieved(msg: Message, associatedCall: Call?) {
        val ip = msg.address
        val id = msg.id

        val expectedId = associatedCall?.expectedID

        // server only verifies IP equality for responses.
        // we only want remote nodes with stable ports in our routing table, so appley a stricter check here
        if (associatedCall != null &&
            associatedCall.request.address != associatedCall.response!!.address
        ) {
            return
        }

        val bucket = routingTable.entryForId(id).bucket
        val entryById = bucket.findByIPorID(null, id)

        // entry is claiming the same ID as entry with different IP in our routing table -> ignore
        if (entryById != null && entryById.address != ip) return

        // ID mismatch from call (not the same as ID mismatch from routing table)
        // it's fishy at least. don't insert even if it proves useful during a lookup
        if (entryById == null && expectedId != null && !expectedId.contentEquals(id)) return

        val newEntry = Peer(msg.address, id)
        // throttle the insert-attempts for unsolicited requests, update-only once they exceed the threshold
        // does not apply to responses
        if (associatedCall == null && updateAndCheckThrottle(newEntry.address)) {
            val bucket = routingTable.entryForId(newEntry.id).bucket

            bucket.refresh(newEntry)
            return
        }

        if (associatedCall != null) {
            newEntry.signalResponse(associatedCall.rTT)
            newEntry.mergeRequestTime(associatedCall.sentTime)
        }


        // force trusted entry into the routing table (by splitting if necessary) if
        // it passed all preliminary tests and it's not yet in the table
        // although we can only trust responses, anything else might be
        // spoofed to clobber our routing table


        val opts: MutableSet<InsertOptions> = mutableSetOf()

        if (msg is Response) opts.add(InsertOptions.RELAXED_SPLIT)

        insertEntry(newEntry, opts)

        // we already should have the bucket. might be an old one by now due to splitting
        // but it doesn't matter, we just need to update the entry, which should stay the
        // same object across bucket splits
        if (msg is Response) {
            if (associatedCall != null) {
                bucket.notifyOfResponse(msg, associatedCall)
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


    private suspend fun insertEntry(toInsert: Peer, opts: Set<InsertOptions>) {
        if (peerId.contentEquals(toInsert.id)) return

        var currentTable = routingTable
        var tableEntry = currentTable.entryForId(toInsert.id)

        while (!opts.contains(InsertOptions.NEVER_SPLIT) && tableEntry.bucket.isFull && (opts.contains(
                InsertOptions.FORCE_INTO_MAIN_BUCKET
            ) || toInsert.verifiedReachable()) && tableEntry.prefix.depth < KEY_BITS - 1
        ) {
            if (!opts.contains(InsertOptions.ALWAYS_SPLIT_IF_FULL) && !canSplit(
                    tableEntry,
                    toInsert,
                    opts.contains(InsertOptions.RELAXED_SPLIT)
                )
            ) break

            splitEntry(currentTable, tableEntry)
            currentTable = routingTable
            tableEntry = currentTable.entryForId(toInsert.id)
        }

        val oldSize = tableEntry.bucket.numEntries

        var toRemove: Peer? = null

        if (opts.contains(InsertOptions.REMOVE_IF_FULL)) {

            /**
             * ascending order for timeCreated, i.e. the first value will be the oldest
             */
            toRemove = tableEntry.bucket.getEntries().maxByOrNull { peer: Peer ->
                peer.creationTime.elapsedNow().inWholeMilliseconds
            }
        }

        if (opts.contains(InsertOptions.FORCE_INTO_MAIN_BUCKET)) tableEntry.bucket.modifyMainBucket(
            toRemove,
            toInsert
        )
        else tableEntry.bucket.insertOrRefresh(toInsert)

        // add delta to the global counter. inaccurate, but will be rebuilt by the bucket checks
        numEntriesInRoutingTable += tableEntry.bucket.numEntries - oldSize
    }

    private fun canSplit(
        entry: RoutingTableEntry,
        toInsert: Peer,
        relaxedSplitting: Boolean
    ): Boolean {
        if (isLocalBucket(entry.prefix)) return true

        if (!relaxedSplitting) return false

        val search = ClosestSearch(
            peerId, MAX_ENTRIES_PER_BUCKET,
            this
        )


        search.fill { true }
        val found = search.entries()

        if (found.size < MAX_ENTRIES_PER_BUCKET) return true

        val max = found[found.size - 1]

        return threeWayDistance(peerId, max.id, toInsert.id) > 0
    }

    private suspend fun splitEntry(expect: RoutingTable, entry: RoutingTableEntry) {

        val current = routingTable
        if (current != expect) return

        val a = RoutingTableEntry(
            splitPrefixBranch(entry.prefix, false), Bucket()
        )
        val b = RoutingTableEntry(
            splitPrefixBranch(entry.prefix, true), Bucket()
        )

        routingTable = current.modify(listOf(entry), listOf(a, b))

        // suppress recursive splitting to relinquish the lock faster.
        // this method is generally called in a loop anyway
        for (e in entry.bucket.getEntries()) insertEntry(
            e,
            setOf(InsertOptions.NEVER_SPLIT, InsertOptions.FORCE_INTO_MAIN_BUCKET)
        )


        // replacements are less important, transfer outside lock
        for (e in entry.bucket.replacementEntries) insertEntry(
            e, setOf()
        )
    }


    fun isLocalId(id: ByteArray): Boolean {
        return peerId.contentEquals(id)
    }

    private fun isLocalBucket(prefix: Prefix): Boolean {
        return isPrefixOf(prefix, peerId)
    }


    private fun onOutgoingRequest(call: Call) {
        val expectedId = call.expectedID ?: return
        val bucket = routingTable.entryForId(expectedId).bucket
        val peer = bucket.findByIPorID(call.request.address, expectedId)
        peer?.signalScheduledRequest()
    }

    suspend fun doRequestCall(call: Call) {

        onOutgoingRequest(call)
        requestCalls.put(call.request.tid.contentHashCode(), call)


        val es = EnqueuedSend(call.request, call)


        send(es)

    }

    suspend fun ping(address: InetSocketAddress, id: ByteArray?) {
        val mtid = createRandomKey(TID_LENGTH)
        val pr = PingRequest(address, peerId, mtid)
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
            map = decode(source)
        } catch (throwable: Throwable) {
            debug("Node", throwable)
            return
        }

        val msg: Message
        try {
            msg = parseMessage(address, map) { mtid: ByteArray ->
                requestCalls[mtid.contentHashCode()]?.request
            } ?: return
        } catch (throwable: Throwable) {
            debug("Node", throwable)
            return
        }

        // just respond to incoming requests, no need to match them to pending requests
        if (msg is Request) {
            when (msg) {
                is AnnounceRequest -> announce(msg)
                is FindNodeRequest -> findNode(msg)
                is GetPeersRequest -> getPeers(msg)
                is PingRequest -> ping(msg)
            }
            return
        }

        // now only response or error

        if (msg is Response && msg.tid.size != TID_LENGTH) {
            val mtid = msg.tid

            val err = Error(
                msg.address, peerId,
                mtid, SERVER_ERROR,
                ("received a response with a transaction id length of " +
                        mtid.size + " bytes, expected [implementation-specific]: " +
                        TID_LENGTH + " bytes").encodeToByteArray()
            )

            sendMessage(err)
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


                    // known nodes - routing table entries - keep track of their own RTTs
                    // they are also biased towards lower RTTs compared to the general population
                    // encountered during regular lookups
                    // don't let them skew the measurement of the general node population
                    if (!call.knownReachableAtCreationTime()) {
                        timeoutFilter.updateAndRecalc(call.rTT)
                    }
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
                msg.address, peerId,
                msg.tid, SERVER_ERROR,
                ("received a response message whose transaction ID did not " +
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


    suspend fun sendMessage(msg: Message) {
        requireNotNull(msg.address) { "message destination must not be null" }


        send(EnqueuedSend(msg, null))

    }


    internal enum class InsertOptions {
        ALWAYS_SPLIT_IF_FULL,
        NEVER_SPLIT,
        RELAXED_SPLIT,
        REMOVE_IF_FULL,
        FORCE_INTO_MAIN_BUCKET
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
internal const val RTT_EMA_WEIGHT = 0.3

// DHT
internal const val MAX_ENTRIES_PER_BUCKET: Int = 8
internal const val RPC_CALL_TIMEOUT_MAX: Long = 10 * 1000
internal const val TOKEN_TIMEOUT: Int = 5 * 60 * 1000
internal const val MAX_DB_ENTRIES_PER_KEY: Int = 6000
internal const val MAX_PEERS_PER_ANNOUNCE: Int = 10

// enter survival mode if we don't see new packets after this time
internal const val SHA1_HASH_LENGTH: Int = 20
internal const val KEY_BITS: Int = SHA1_HASH_LENGTH * 8


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

// returns the older timestamp
internal fun olderTimeMark(mark: ValueTimeMark, cmp: ValueTimeMark): ValueTimeMark {
    val markElapsed = mark.elapsedNow().inWholeMilliseconds
    val cmpElapsed = cmp.elapsedNow().inWholeMilliseconds
    return if (markElapsed > cmpElapsed) mark else cmp
}


internal fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
    val minLength = min(a.size.toDouble(), b.size.toDouble()).toInt()
    run {
        var i = 0
        while (i + 7 < minLength) {
            val la = a[i].toULong() shl 56 or (
                    a[i + 1].toULong() shl 48) or (
                    a[i + 2].toULong() shl 40) or (
                    a[i + 3].toULong() shl 32) or (
                    a[i + 4].toULong() shl 24) or (
                    a[i + 5].toULong() shl 16) or (
                    a[i + 6].toULong() shl 8) or
                    a[i + 7].toULong()
            val lb = b[i].toULong() shl 56 or (
                    b[i + 1].toULong() shl 48) or (
                    b[i + 2].toULong() shl 40) or (
                    b[i + 3].toULong() shl 32) or (
                    b[i + 4].toULong() shl 24) or (
                    b[i + 5].toULong() shl 16) or (
                    b[i + 6].toULong() shl 8) or
                    b[i + 7].toULong()

            if (la != lb) return la.compareTo(lb)

            i += 8
        }
    }


    for (i in 0 until minLength) {
        val ia = a[i].toULong()
        val ib = b[i].toULong()
        if (ia != ib) return ia.compareTo(ib)
    }

    return a.size - b.size
}

internal fun mismatch(a: ByteArray, b: ByteArray): Int {
    val min = min(a.size, b.size)
    for (i in 0 until min) {
        if (a[i] != b[i]) return i
    }

    return if (a.size == b.size) -1 else min
}


private fun activeInFlight(inFlight: MutableSet<Call>): Int {
    return inFlight.filter { call: Call ->
        val state = call.state()
        state == CallState.UNSENT || state == CallState.SENT
    }.map { call: Call -> call.expectedID!! }.count()
}

private fun inStabilization(closest: ClosestSet, candidates: Candidates): Boolean {
    val suggestedCounts = closest.entries().map { k: Peer ->
        candidates.nodeForEntry(
            k
        )!!.numSources()
    }

    return suggestedCounts.any { i: Int -> i >= 5 } ||
            suggestedCounts.count { i: Int -> i >= 4 } >= 2
}


private fun terminationPrecondition(
    candidate: Peer,
    closest: ClosestSet,
    candidates: Candidates
): Boolean {
    return !closest.candidateAheadOfTail(candidate) && (
            inStabilization(closest, candidates) ||
                    closest.maxAttemptsSinceTailModificationFailed())
}

/* algo:
* 1. check termination condition
* 2. allow if free slot
* 3. if stall slot check
* a) is candidate better than non-stalled in flight
* b) is candidate better than head (homing phase)
* c) is candidate better than tail (stabilizing phase)
*/
internal fun goodForRequest(
    candidate: Peer,
    closest: ClosestSet,
    candidates: Candidates,
    inFlight: MutableSet<Call>
): Boolean {

    var result = closest.candidateAheadOf(candidate)

    if (closest.candidateAheadOfTail(candidate) && inStabilization(closest, candidates)) result =
        true
    if (!terminationPrecondition(
            candidate,
            closest,
            candidates
        ) && activeInFlight(inFlight) == 0
    ) result = true

    return result
}

internal fun transform(socketAddress: InetSocketAddress): Address {
    val address = socketAddress.resolveAddress()!!
    return Address(address, socketAddress.port.toUShort())
}

internal fun encode(socketAddress: InetSocketAddress): ByteArray {
    val address = socketAddress.resolveAddress()!!
    val buffer = Buffer()
    buffer.write(address)
    buffer.writeUShort(socketAddress.port.toUShort())
    return buffer.readByteArray()
}