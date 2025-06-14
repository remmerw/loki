package io.github.remmerw.loki.idun

import kotlin.math.sign

internal class ClosestSearch(
    private val key: ByteArray,
    private val maxEntries: Int,
    private val node: Node
) {
    private val entries: MutableSet<Peer> = mutableSetOf()


    /**
     * consider the following routing table:
     *
     *
     * 0000000...
     * 0000001...
     * 000001...
     * 00001...
     * 0001...
     * 001...
     * 01...
     * 1...
     *
     *
     * now consider the following target key:
     *
     *
     * 1001101111011100000000011101011001111100001100000010111010111110101000100010101011101001101111010011011110000111010010001100001101011110100000010000011001101000
     *
     *
     * the first bucket we will want to pick values from is 1...
     * the second bucket with the next-higher xor distance actually is 0001...
     *
     *
     * This requires a non-contiguous search
     */
    private fun insertBucket(bucket: Bucket, filter: (Peer) -> Boolean) {
        bucket.entries().filter(filter).forEach { e: Peer -> entries.add(e) }
    }

    private fun shave() {
        val overshoot = entries.size - maxEntries

        if (overshoot <= 0) return
        entries().takeLast(overshoot).forEach { e: Peer -> entries.remove(e) }
    }

    fun fill(filter: (Peer) -> Boolean) {
        val table = node.routingTable


        val initialIdx = table.indexForId(key)
        var currentIdx = initialIdx

        var current: RoutingTableEntry? = table.get(initialIdx)


        while (true) {
            insertBucket(current!!.bucket, filter)

            if (entries.size >= maxEntries) break

            val bucketPrefix = current.prefix
            val targetToBucketDistance = createPrefix(
                distance(key, bucketPrefix.hash),
                bucketPrefix.depth
            ) // translate into xor distance, trim trailing bits
            val incrementedDistance =
                add(
                    targetToBucketDistance.hash,
                    setBitKey(targetToBucketDistance.depth)
                ) // increment distance by least significant *prefix* bit
            val nextBucketTarget =
                distance(key, incrementedDistance) // translate back to natural distance

            // guess neighbor bucket that might be next in target order

            val dir = compareUnsigned(nextBucketTarget, current.prefix.hash).sign
            var idx: Int

            current = null

            idx = currentIdx + dir
            if (0 <= idx && idx < table.size()) current = table.get(idx)

            // do binary search if guess turned out incorrect
            if (current == null || !isPrefixOf(current.prefix, nextBucketTarget)) {
                idx = table.indexForId(nextBucketTarget)
                current = table.get(idx)
            }

            currentIdx = idx

            // quit if there are insufficient routing table entries to reach the desired size
            if (currentIdx == initialIdx) break
        }

        shave()
    }

    fun inet4List(): List<Peer> {
        return entries().filter { peer: Peer ->
            peer.address.resolveAddress()?.size == 4
        }
    }

    fun inet6List(): List<Peer> {
        return entries().filter { peer: Peer ->
            peer.address.resolveAddress()?.size == 16
        }
    }

    fun entries(): List<Peer> {
        return entries.sortedWith(Peer.DistanceOrder(key))
    }
}
