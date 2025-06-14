package io.github.remmerw.loki.mdht

import kotlin.math.max
import kotlin.math.min

internal class RoutingTable internal constructor(
    val entries: List<RoutingTableEntry> = listOf(
        RoutingTableEntry(
            Prefix(ByteArray(SHA1_HASH_LENGTH), -1), Bucket()
        )
    )
) {
    private val indexCache: IntArray

    init {
        indexCache = if (entries.size > 64) {
            buildCache()
        } else {
            intArrayOf(0, entries.size)
        }
    }


    private fun buildCache(): IntArray {
        val cache = IntArray(256)

        val bitsCount = (cache.size / 2) - 1
        val lsb = bitsCount.countOneBits() - 1

        val increment = setBitKey(lsb)
        val trailingBits = distance(createPrefixHash(Key.MAX_KEY, lsb), Key.MAX_KEY)
        var currentLower = createPrefixHash(Key.MIN_KEY, lsb)
        var currentUpper = distance(createPrefixHash(Key.MIN_KEY, lsb), trailingBits)

        var innerOffset = 0

        var i = 0
        while (i < cache.size) {
            cache[i + 1] = entries.size

            for (j in innerOffset until entries.size) {
                val p = entries[j].prefix

                if (compareUnsigned(p.hash, currentLower) <= 0) {
                    cache[i] = max(cache[i].toDouble(), j.toDouble()).toInt()
                    innerOffset = cache[i]
                }

                if (compareUnsigned(p.hash, currentUpper) >= 0) {
                    cache[i + 1] = min(cache[i + 1].toDouble(), j.toDouble()).toInt()
                    break
                }
            }

            currentLower = createPrefixHash(add(currentLower, increment), lsb)
            currentUpper = distance(currentLower, trailingBits)
            i += 2
        }


        return cache
    }

    fun indexForId(id: ByteArray): Int {
        val mask = indexCache.size / 2 - 1
        val bits = mask.countOneBits()

        var cacheIdx = getInt(id, 0)

        cacheIdx = cacheIdx.rotateLeft(bits)
        cacheIdx = cacheIdx and mask
        cacheIdx = cacheIdx shl 1

        var lowerBound = indexCache[cacheIdx]
        var upperBound = indexCache[cacheIdx + 1]

        var pivot: Prefix?

        while (true) {
            val pivotIdx = (lowerBound + upperBound) ushr 1
            pivot = entries[pivotIdx].prefix

            if (pivotIdx == lowerBound) break

            if (compareUnsigned(pivot.hash, id) <= 0) lowerBound = pivotIdx
            else upperBound = pivotIdx
        }

        return lowerBound
    }


    fun entryForId(id: ByteArray): RoutingTableEntry {
        return entries[indexForId(id)]
    }

    fun size(): Int {
        return entries.size
    }

    fun get(idx: Int): RoutingTableEntry {
        return entries[idx]
    }

    fun modify(
        toRemove: Collection<RoutingTableEntry>?,
        toAdd: Collection<RoutingTableEntry>?
    ): RoutingTable {
        val temp: MutableList<RoutingTableEntry> = ArrayList(entries)
        if (toRemove != null) temp.removeAll(toRemove)
        if (toAdd != null) temp.addAll(toAdd)
        return RoutingTable(temp)
    }
}
