package io.github.remmerw.loki.mdht

import io.github.remmerw.loki.debug
import io.ktor.network.sockets.InetSocketAddress
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicArray
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

internal class Bucket internal constructor() {

    @OptIn(ExperimentalAtomicApi::class)
    private val currentReplacementPointer = AtomicInt(0)

    @OptIn(ExperimentalAtomicApi::class)
    private val replacementBucket =
        AtomicArray<Peer?>(MAX_ENTRIES_PER_BUCKET) { i -> null }

    // using arraylist here since reading/iterating is far more common than writing.
    @Volatile
    private var entries: List<Peer> = emptyList()


    /**
     * Notify bucket of new incoming packet from a node, perform update or insert
     * existing nodes where appropriate
     *
     * @param newEntry The entry to insert
     */
    fun insertOrRefresh(newEntry: Peer) {


        val entriesRef = entries

        for (existing in entriesRef) {
            if (existing.equals(newEntry)) {
                existing.mergeInTimestamps(newEntry)
                return
            }

            if (existing.matchIPorID(newEntry)) {
                debug(
                    "new node $newEntry claims same ID or IP as $existing," +
                            " might be impersonation attack or IP change. ignoring until " +
                            "old entry times out"
                )
                return
            }
        }

        if (newEntry.verifiedReachable()) {
            if (entriesRef.size < MAX_ENTRIES_PER_BUCKET) {
                // insert if not already in the list and we still have room
                modifyMainBucket(null, newEntry)
                return
            }

            if (replaceBadEntry(newEntry)) return

            val youngest = entriesRef[entriesRef.size - 1]

            // older entries displace younger ones (although that kind of stuff should probably
            // go through #modifyMainBucket directly)
            // entries with a 2.5times lower RTT than the current youngest one displace the
            // youngest. safety factor to prevent fibrilliation due to changing RTT-estimates /
            // to only replace when it's really worth it
            if (youngest.creationTime > newEntry.creationTime || newEntry.rTT * 2.5 < youngest.rTT) {
                modifyMainBucket(youngest, newEntry)
                // it was a useful entry, see if we can use it to replace something questionable
                insertInReplacementBucket(youngest)
                return
            }
        }

        insertInReplacementBucket(newEntry)
    }

    fun refresh(toRefresh: Peer) {
        val e = entries.filter { other: Peer -> toRefresh.equals(other) }.randomOrNull()
        e?.mergeInTimestamps(toRefresh)
    }

    /**
     * mostly meant for internal use or transfering entries into a new bucket.
     * to update a bucket properly use [.insertOrRefresh]
     */
    fun modifyMainBucket(toRemove: Peer?, toInsert: Peer?) {
        // we're synchronizing all modifications, therefore we can freely reference
        // the old entry list, it will not be modified concurrently


        if (toInsert != null && entries
                .any { other: Peer? -> toInsert.matchIPorID(other) }
        ) return
        val newEntries: MutableList<Peer> = entries.toMutableList()
        var removed = false
        var added = false

        // removal never violates ordering constraint, no checks required
        if (toRemove != null) removed = newEntries.remove(toRemove)


        if (toInsert != null) {
            val oldSize = newEntries.size
            val wasFull = oldSize >= MAX_ENTRIES_PER_BUCKET
            val youngest = if (oldSize > 0) newEntries[oldSize - 1] else null
            val unorderedInsert =
                youngest != null && toInsert.creationTime < youngest.creationTime
            added = !wasFull || unorderedInsert
            if (added) {
                newEntries.add(toInsert)
                val entry = removeFromReplacement(toInsert)
                entry?.mergeInTimestamps(toInsert)
            } else {
                insertInReplacementBucket(toInsert)
            }
            /**
             * ascending order for timeCreated, i.e. the first value will be the oldest
             */
            if (unorderedInsert) newEntries.sortBy { peer: Peer ->
                peer.creationTime.elapsedNow().inWholeMilliseconds
            }

            if (wasFull && added) while (newEntries.size > MAX_ENTRIES_PER_BUCKET) insertInReplacementBucket(
                newEntries.removeAt(newEntries.size - 1)
            )
        }

        // make changes visible
        if (added || removed) entries = newEntries

    }

    val numEntries: Int
        /**
         * Get the number of entries.
         *
         * @return The number of entries in this Bucket
         */
        get() = entries.size

    val isFull: Boolean
        get() = entries.size >= MAX_ENTRIES_PER_BUCKET

    fun getEntries(): List<Peer> {
        return entries.toList()
    }

    fun entries(): List<Peer> {
        return entries
    }


    @OptIn(ExperimentalAtomicApi::class)
    val replacementEntries: List<Peer>
        get() {
            val repEntries: MutableList<Peer> =
                ArrayList(replacementBucket.size)
            val current = currentReplacementPointer.load()
            for (i in 1..replacementBucket.size) {
                val e =
                    replacementBucket.loadAt((current + i) % replacementBucket.size)
                if (e != null) repEntries.add(e)
            }
            return repEntries
        }

    /**
     * A peer failed to respond
     *
     * @param address Address of the peer
     */

    @OptIn(ExperimentalAtomicApi::class)
    fun onTimeout(address: InetSocketAddress) {
        val entriesRef = entries
        run {
            var i = 0
            val n = entriesRef.size
            while (i < n) {
                val e = entriesRef[i]
                if (e.address == address) {
                    e.signalRequestTimeout()
                    //only removes the entry if it is bad
                    removeEntryIfBad(e)
                    return
                }
                i++
            }
        }

        var i = 0
        val n = replacementBucket.size
        while (i < n) {
            val e = replacementBucket.loadAt(i)
            if (e != null && e.address == address) {
                e.signalRequestTimeout()
                return
            }
            i++
        }
    }


    /**
     * Tries to instert entry by replacing a bad entry.
     *
     * @param entry Entry to insert
     * @return true if replace was successful
     */
    private fun replaceBadEntry(entry: Peer): Boolean {
        val entriesRef = entries
        var i = 0
        val n = entriesRef.size
        while (i < n) {
            val e = entriesRef[i]
            if (e.needsReplacement()) {
                // bad one get rid of it
                modifyMainBucket(e, entry)
                return true
            }
            i++
        }
        return false
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun pollVerifiedReplacementEntry(): Peer? {
        while (true) {
            var bestIndex = -1
            var bestFound: Peer? = null

            for (i in 0 until replacementBucket.size) {
                val entry = replacementBucket.loadAt(i)
                if (entry == null || !entry.verifiedReachable()) continue
                val isBetter =
                    bestFound == null || entry.rTT < bestFound.rTT ||
                            (entry.rTT == bestFound.rTT && entry.lastSeen > bestFound.lastSeen)

                if (isBetter) {
                    bestFound = entry
                    bestIndex = i
                }
            }

            if (bestFound == null) return null

            var newPointer = bestIndex - 1
            if (newPointer < 0) newPointer = replacementBucket.size - 1
            if (replacementBucket.compareAndSetAt(bestIndex, bestFound, null)) {
                currentReplacementPointer.store(newPointer)
                return bestFound
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun removeFromReplacement(toRemove: Peer): Peer? {
        for (i in 0 until replacementBucket.size) {
            val e = replacementBucket.loadAt(i)
            if (e == null || !e.matchIPorID(toRemove)) continue
            replacementBucket.compareAndSetAt(i, e, null)
            if (e.equals(toRemove)) return e
        }
        return null
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun insertInReplacementBucket(toInsert: Peer?) {
        if (toInsert == null) return

        outer@ while (true) {
            val insertationPoint =
                currentReplacementPointer.incrementAndFetch() and (replacementBucket.size - 1)

            val toOverwrite = replacementBucket.loadAt(insertationPoint)

            var canOverwrite: Boolean

            if (toOverwrite == null) {
                canOverwrite = true
            } else {
                val lingerTime =
                    if (toOverwrite.verifiedReachable() &&
                        !toInsert.verifiedReachable()
                    ) 5 * 60 * 1000 else 1000
                canOverwrite =
                    toInsert.lastSeen.minus(toOverwrite.lastSeen).inWholeMilliseconds > lingerTime
                            || toInsert.rTT < toOverwrite.rTT
            }

            if (!canOverwrite) break

            for (i in 0 until replacementBucket.size) {
                // don't insert if already present
                val potentialDuplicate = replacementBucket.loadAt(i)
                if (toInsert.matchIPorID(potentialDuplicate)) {
                    if (toInsert.equals(potentialDuplicate)) potentialDuplicate!!.mergeInTimestamps(
                        toInsert
                    )
                    break@outer
                }
            }

            if (replacementBucket.compareAndSetAt(insertationPoint, toOverwrite, toInsert)) break
        }
    }

    fun findByIPorID(ip: InetSocketAddress?, id: ByteArray?): Peer? {
        return entries.firstOrNull { e: Peer -> e.id.contentEquals(id) || e.address == ip }
    }

    fun notifyOfResponse(msg: Message, associatedCall: Call) {
        val entriesRef = entries
        var i = 0
        val n = entriesRef.size
        while (i < n) {
            val entry = entriesRef[i]

            // update last responded. insert will be invoked soon,
            // thus we don't have to do the move-to-end stuff
            if (entry.id.contentEquals(msg.id)) {
                entry.signalResponse(associatedCall.rTT)
                return
            }
            i++
        }
    }


    /**
     * @param toRemove Entry to remove, if its bad

     */
    private fun removeEntryIfBad(toRemove: Peer) {
        val entriesRef = entries
        if (entriesRef.contains(toRemove) && toRemove.needsReplacement()) {
            val replacement = pollVerifiedReplacementEntry()

            // only remove if we have a replacement or really need to
            if (replacement != null) modifyMainBucket(toRemove, replacement)
        }
    }
}
