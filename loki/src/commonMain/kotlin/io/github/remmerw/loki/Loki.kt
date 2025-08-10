package io.github.remmerw.loki

import com.eygraber.uri.Uri
import io.github.remmerw.loki.core.BitfieldCollectingConsumer
import io.github.remmerw.loki.core.BitfieldConnectionHandler
import io.github.remmerw.loki.core.BitfieldConsumer
import io.github.remmerw.loki.core.DataStorage
import io.github.remmerw.loki.core.ExtendedHandshakeConsumer
import io.github.remmerw.loki.core.ExtendedProtocolHandshakeHandler
import io.github.remmerw.loki.core.MetadataAgent
import io.github.remmerw.loki.core.MetadataConsumer
import io.github.remmerw.loki.core.PeerRequestAgent
import io.github.remmerw.loki.core.PieceAgent
import io.github.remmerw.loki.core.RequestProducer
import io.github.remmerw.loki.core.StorageUnit
import io.github.remmerw.loki.core.Worker
import io.github.remmerw.loki.core.performConnection
import io.github.remmerw.loki.data.ExtendedMessageHandler
import io.github.remmerw.loki.data.ExtendedProtocol
import io.github.remmerw.loki.data.PeerExchangeHandler
import io.github.remmerw.loki.data.TorrentId
import io.github.remmerw.loki.data.UtMetadataHandler
import io.github.remmerw.nott.Peer
import io.github.remmerw.nott.newNott
import io.github.remmerw.nott.nodeId
import io.github.remmerw.nott.requestGetPeers
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem


data class State(val piecesTotal: Int, val piecesComplete: Int)

interface Storage {
    fun storeTo(directory: Path)

    /**
     * Delete the storage, all intermediate files will be deleted
     */
    fun delete()

    fun storageUnits(): List<StorageUnit>
}


interface PeerStore {
    suspend fun peers(limit: Int): List<Peer>

    suspend fun store(peer: Peer)
}


class MemoryPeers : PeerStore {
    private val peers: MutableSet<Peer> = mutableSetOf()
    private val mutex = Mutex()

    override suspend fun peers(limit: Int): List<Peer> {
        mutex.withLock {
            return peers.take(limit).toList()
        }
    }

    override suspend fun store(peer: Peer) {
        mutex.withLock {
            peers.add(peer)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)
suspend fun CoroutineScope.download(
    magnetUri: MagnetUri,
    directory: Path,
    peerStore: PeerStore = MemoryPeers(),
    progress: (State) -> Unit
): Storage {
    val torrentId = magnetUri.torrentId
    val path = Path(directory, torrentId.bytes.toHexString())
    SystemFileSystem.createDirectories(path)

    val dataStorage = DataStorage(path)
    val selectorManager = SelectorManager(Dispatchers.IO)

    val nodeId = nodeId()
    val nott = newNott(nodeId, peers = peerStore.peers(25))

    val extendedMessagesHandler: List<ExtendedMessageHandler> = listOf(
        PeerExchangeHandler(),
        UtMetadataHandler()
    )

    val extendedProtocol = ExtendedProtocol(extendedMessagesHandler)

    // add default handshake handlers to the beginning of the connection handling chain
    val handshakeHandlers = setOf(
        BitfieldConnectionHandler(dataStorage),
        ExtendedProtocolHandshakeHandler(
            dataStorage,
            extendedMessagesHandler,
            nott.port(),
            VERSION
        )
    )

    val metadataConsumer = MetadataConsumer(dataStorage, torrentId)
    // need to also receive Bitfields and Haves (without validation for the number of pieces...)
    val bitfieldConsumer = BitfieldCollectingConsumer(dataStorage)


    val worker = Worker(
        dataStorage, listOf(
            BitfieldConsumer(dataStorage), ExtendedHandshakeConsumer(),
            MetadataAgent(dataStorage), RequestProducer(dataStorage),
            PeerRequestAgent(dataStorage), PieceAgent(dataStorage),
            metadataConsumer, bitfieldConsumer
        )
    )


    try {
        val responses = requestGetPeers(nott, torrentId.bytes) {
            val size = worker.purgedConnections()
            if (size > 10) {
                30000 // 30 sec
            } else if (size > 5) {
                15000 // 15 sec
            } else {
                5000 // 5 sec
            }
        }

        performConnection(
            selectorManager, nodeId, torrentId, extendedProtocol,
            handshakeHandlers, dataStorage, worker, peerStore, responses
        )

        if (!dataStorage.initializeDone()) {
            metadataConsumer.waitForTorrent()
        }


        // process bitfields and haves that we received while fetching metadata
        bitfieldConsumer.processMessages()


        val dataBitfield = dataStorage.dataBitfield()!! // must be defined

        while (true) {
            if (dataBitfield.piecesRemaining() == 0) {

                progress.invoke(
                    State(
                        dataBitfield.piecesTotal,
                        dataBitfield.piecesComplete()
                    )
                )
                coroutineContext.cancelChildren()
                break
            } else {
                progress.invoke(
                    State(
                        dataBitfield.piecesTotal,
                        dataBitfield.piecesComplete()
                    )
                )
                delay(1000)
            }
        }
        return dataStorage
    } finally {

        try {
            nott.shutdown()
        } catch (throwable: Throwable) {
            debug(throwable)
        }

        try {
            dataStorage.shutdown()
        } catch (throwable: Throwable) {
            debug(throwable)
        }

        try {
            worker.shutdown()
        } catch (throwable: Throwable) {
            debug(throwable)
        }

        try {
            selectorManager.close()
        } catch (throwable: Throwable) {
            debug(throwable)
        }
    }

}


internal const val MAX_SIMULTANEOUSLY_ASSIGNED_PIECES: Int = 3
internal const val BLOCK_SIZE: Int = 16 * 1024 // 16 KB
internal const val META_EXCHANGE_MAX_SIZE: Int = 2 * 1024 * 1024 // 2 MB
internal const val MAX_OUTSTANDING_REQUESTS: Int = 250
internal const val MAX_PIECE_RECEIVING_TIME: Long = 5000 // 5 sec
internal const val PEER_INACTIVITY_THRESHOLD: Long = 3 * 60 * 1000 // 3 min
internal const val CHOKING_THRESHOLD: Long = 10000 // millis
internal const val FIRST_BLOCK_ARRIVAL_TIMEOUT: Long = 10000 // 10 sec
internal const val WAIT_BEFORE_REREQUESTING_AFTER_REJECT: Long = 10000 // 10 sec
internal const val UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL: Long = 1000
internal const val VERSION = "Loki 0.3.1"
internal const val SCHEME: String = "magnet"
internal const val INFO_HASH_PREFIX: String = "urn:btih:"


private object UriParams {
    const val TORRENT_ID: String = "xt"
    const val DISPLAY_NAME: String = "dn"
    const val TRACKER_URL: String = "tr"
    const val PEER: String = "x.pe"
}

/**
 * Create a magnet URI from its' string representation in BEP-9 format.
 * Current limitations:
 * - only v1 links are supported (xt=urn:btih:&lt;info-hash&gt;)
 * - base32-encoded info hashes are not supported
 */
fun parseMagnetUri(uri: String): MagnetUri {

    val paramsMap = collectParams(uri)

    val infoHashes = requiredParams(paramsMap)
        .filter { value: String -> value.startsWith(INFO_HASH_PREFIX) }
        .map { value: String -> value.substring(INFO_HASH_PREFIX.length) }
        .toSet()


    check(infoHashes.size == 1) {
        "Parameter ${UriParams.TORRENT_ID} has invalid number of values with prefix $INFO_HASH_PREFIX: ${infoHashes.size}"
    }
    val torrentId = buildTorrentId(infoHashes.iterator().next())
    val builder = MagnetUri.torrentId(torrentId)

    val displayName = optionalParams(UriParams.DISPLAY_NAME, paramsMap).firstOrNull()
    if (displayName != null) builder.name(displayName)

    optionalParams(UriParams.TRACKER_URL, paramsMap).forEach { trackerUrl: String ->
        builder.tracker(
            trackerUrl
        )
    }
    optionalParams(UriParams.PEER, paramsMap).forEach { value: String ->
        try {
            builder.peer(parsePeer(value))
        } catch (_: Throwable) {
        }
    }

    return builder.buildUri()
}

private fun collectParams(uri: String): Map<String, MutableList<String>> {
    val paramsMap: MutableMap<String, MutableList<String>> = mutableMapOf()


    // magnet:?param1=value1...
    // uri.getSchemeSpecificPart() will start with the question mark and contain all name-value pairs
    val url = Uri.parse(uri)
    require(SCHEME == url.scheme) { "Invalid scheme: " + url.scheme }
    var scheme = url.schemeSpecificPart!!
    if (scheme.startsWith("?")) {
        scheme = scheme.substring(1)
    }
    val params = scheme.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    for (param in params) {
        val parts = param.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size == 2) {
            val name = parts[0]
            val value = parts[1]
            val values = paramsMap.getOrPut(name) { mutableListOf() }
            values.add(value)
        }
    }

    return paramsMap
}

private fun requiredParams(paramsMap: Map<String, MutableList<String>>): List<String> {
    val values = paramsMap.getOrElse(UriParams.TORRENT_ID) { emptyList() }
    check(values.isNotEmpty()) { "Required parameter ${UriParams.TORRENT_ID} is missing" }
    return values
}

private fun optionalParams(
    paramName: String,
    paramsMap: Map<String, MutableList<String>>
): List<String> {
    return paramsMap.getOrElse(paramName) { emptyList() }
}

private fun buildTorrentId(infoHash: String): TorrentId {
    val len = infoHash.length
    require(len == 40) { "Invalid info hash length: $len" }
    return TorrentId(fromHex(infoHash))
}


private fun parsePeer(value: String): InetSocketAddress {
    val parts = value.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    require(parts.size == 2) { "Invalid peer format: $value; should be <host>:<port>" }
    val hostname = parts[0]
    val port = parts[1].toInt()
    return InetSocketAddress(hostname, port)
}


/**
 * Get binary data from its' hex-encoded representation (regardless of case).
 *
 * @param s Hex-encoded representation of binary data
 * @return Binary data
 */
private fun fromHex(s: String): ByteArray {
    require(!(s.isEmpty() || s.length % 2 != 0)) { "Invalid string: $s" }
    val chars = s.toCharArray()
    val len = chars.size / 2
    val bytes = ByteArray(len)
    var i = 0
    var j = 0
    while (i < len) {
        bytes[i] = (hexDigit(chars[j]) * 16 + hexDigit(
            chars[j + 1]
        )).toByte()
        i++
        j = i * 2
    }
    return bytes
}

private fun hexDigit(c: Char): Int {
    return when (c) {
        in '0'..'9' -> {
            c.code - '0'.code
        }

        in 'A'..'F' -> {
            c.code - 'A'.code + 10
        }

        in 'a'..'f' -> {
            c.code - 'a'.code + 10
        }

        else -> throw IllegalArgumentException("Illegal hexadecimal character: $c")
    }
}


@Suppress("unused")
class MagnetUri private constructor(
    /**
     * Represents the "xt" parameter.
     * E.g. xt=urn:btih:af0d9aa01a9ae123a73802cfa58ccaf355eb19f1
     */
    val torrentId: TorrentId,
    /**
     * Represents the "dn" parameter. Value is URL decoded.
     * E.g. dn=Some%20Display%20Name =&gt; "Some Display Name"
     */
    val displayName: String?,
    val trackerUrls: Collection<String>,
    /**
     * Represents the collection of values of "x.pe" parameters. Values are URL decoded.
     * E.g. `x.pe=124.131.72.242%3A6891&x.pe=11.9.132.61%3A6900`
     * =&gt; [124.131.72.242:6891, 11.9.132.61:6900]
     *
     * @return Collection of well-known peer addresses
     */
    val peerAddresses: Collection<InetSocketAddress>
) {


    class Builder internal constructor(private val torrentId: TorrentId) {
        private val trackerUrls: MutableSet<String> = mutableSetOf()
        private val peerAddresses: MutableSet<InetSocketAddress> = mutableSetOf()
        private var displayName: String? = null

        /**
         * Set "dn" parameter.
         * Caller must NOT perform URL encoding, otherwise the value will get encoded twice.
         */
        fun name(displayName: String) {
            this.displayName = displayName
        }

        /**
         * Add "tr" parameter.
         * Caller must NOT perform URL encoding, otherwise the value will get encoded twice.
         */
        fun tracker(trackerUrl: String) {
            trackerUrls.add(trackerUrl)
        }

        /**
         * Add "x.pe" parameter.
         *
         * @param address Well-known peer address
         */
        fun peer(address: InetSocketAddress) {
            peerAddresses.add(address)
        }

        fun buildUri(): MagnetUri {
            return MagnetUri(torrentId, displayName, trackerUrls, peerAddresses)
        }
    }

    companion object {
        fun torrentId(torrentId: TorrentId): Builder {
            return Builder(torrentId)
        }
    }
}


@Suppress("SameReturnValue")
private val isError: Boolean
    get() = true

@Suppress("SameReturnValue")
private val isDebug: Boolean
    get() = true

internal fun debug(text: String) {
    if (isDebug) {
        println(text)
    }
}

internal fun debug(throwable: Throwable) {
    if (isError) {
        throwable.printStackTrace()
    }
}





