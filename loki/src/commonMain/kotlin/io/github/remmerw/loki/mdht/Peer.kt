package io.github.remmerw.loki.mdht

import io.ktor.network.sockets.InetSocketAddress
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

internal class Peer(val address: InetSocketAddress, val id: ByteArray) {

    private val avgRTT = ExponentialWeightendMovingAverage().setWeight(RTT_EMA_WEIGHT)

    var creationTime: ValueTimeMark
    var lastSeen: ValueTimeMark
    private var verified = false

    /**
     * -1 = never queried / learned about it from incoming requests
     * 0 = last query was a success
     * > 0 = query failed
     */
    private var failedQueries = 0
    private var lastSendTime: ValueTimeMark? = null


    init {
        lastSeen = TimeSource.Monotonic.markNow()
        creationTime = lastSeen
    }


    override fun equals(other: Any?): Boolean {
        if (other is Peer) return this.equals(other)
        return false
    }

    fun equals(other: Peer?): Boolean {
        if (other == null) return false
        return id.contentEquals(other.id) && address == other.address
    }

    override fun hashCode(): Int {
        return id.hashCode() // note bucket entry
    }

    fun eligibleForNodesList(): Boolean {
        // 1 timeout can occasionally happen. should be fine to hand
        // it out as long as we've verified it at least once
        return verifiedReachable() && failedQueries < 2
    }

    fun verifiedReachable(): Boolean {
        return verified
    }

    // old entries, e.g. from routing table reload
    private fun oldAndStale(): Boolean {
        return failedQueries > OLD_AND_STALE_TIMEOUTS &&
                lastSeen.elapsedNow().inWholeMilliseconds > OLD_AND_STALE_TIME
    }

    fun needsReplacement(): Boolean {
        return (failedQueries > 1 && !verifiedReachable()) ||
                failedQueries > MAX_TIMEOUTS || oldAndStale()
    }

    fun mergeInTimestamps(other: Peer) {
        if (!this.equals(other) || this === other) return
        lastSeen = newerTimeMark(lastSeen, other.lastSeen)!!
        lastSendTime = newerTimeMark(lastSendTime, other.lastSendTime)
        creationTime = olderTimeMark(creationTime, other.creationTime)
        if (other.verifiedReachable()) setVerified()
        if (!other.avgRTT.average.isNaN()) avgRTT.updateAverage(other.avgRTT.average)
    }


    @Suppress("unused")
    val rTT: Int
        get() = avgRTT.getAverage(RPC_CALL_TIMEOUT_MAX.toDouble())
            .toInt()

    /**
     * @param rtt > 0 in ms. -1 if unknown
     */
    fun signalResponse(rtt: Long) {
        lastSeen = TimeSource.Monotonic.markNow()
        failedQueries = 0
        verified = true
        if (rtt > 0) avgRTT.updateAverage(rtt.toDouble())
    }

    fun mergeRequestTime(requestSent: ValueTimeMark?) {
        lastSendTime = newerTimeMark(lastSendTime, requestSent)
    }


    fun signalScheduledRequest() {
        lastSendTime = TimeSource.Monotonic.markNow()
    }

    /**
     * Should be called to signal that a request to this peer has timed out;
     */
    fun signalRequestTimeout() {
        failedQueries++
    }


    private fun setVerified() {
        verified = true
    }


    class DistanceOrder(val target: ByteArray) : Comparator<Peer> {
        override fun compare(a: Peer, b: Peer): Int {
            return threeWayDistance(target, a.id, b.id)
        }
    }
}