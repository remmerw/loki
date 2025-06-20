package io.github.remmerw.loki.core

import io.github.remmerw.loki.FIRST_BLOCK_ARRIVAL_TIMEOUT
import io.github.remmerw.loki.META_EXCHANGE_MAX_SIZE
import io.github.remmerw.loki.WAIT_BEFORE_REREQUESTING_AFTER_REJECT
import io.github.remmerw.loki.debug
import io.github.remmerw.loki.grid.ExtendedHandshake
import io.github.remmerw.loki.grid.Message
import io.github.remmerw.loki.grid.MetaType
import io.github.remmerw.loki.grid.TorrentId
import io.github.remmerw.loki.grid.Type
import io.github.remmerw.loki.grid.UtMetadata
import io.github.remmerw.loki.grid.request
import kotlinx.coroutines.delay
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource

internal class MetadataConsumer internal constructor(
    private val dataStorage: DataStorage,
    private val torrentId: TorrentId
) : Produces, Consumers {

    // set immediately after metadata has been fetched and verified
    @OptIn(ExperimentalAtomicApi::class)
    private val done = AtomicBoolean(false)

    @Volatile
    private var metadata: ExchangedMetadata? = null

    @Volatile
    private var doConsume: Boolean = true

    private fun doConsume(message: Message, connection: Connection) {
        if (message is ExtendedHandshake) {
            consume(message, connection)
        }
        if (message is UtMetadata) {
            consume(message, connection)
        }
    }

    override val consumers: List<MessageConsumer>
        get() {
            val list: MutableList<MessageConsumer> = mutableListOf()
            list.add(object : MessageConsumer {
                override fun consumedType(): Type {
                    return Type.ExtendedHandshake
                }

                override fun consume(
                    message: Message,
                    connection: Connection
                ) {
                    doConsume(message, connection)
                }
            })
            list.add(object : MessageConsumer {
                override fun consumedType(): Type {
                    return Type.UtMetadata
                }

                override fun consume(
                    message: Message,
                    connection: Connection
                ) {
                    doConsume(message, connection)
                }
            })
            return list
        }

    private fun consume(handshake: ExtendedHandshake, connection: Connection) {
        if (handshake.supportedMessageTypes.contains("ut_metadata")) {
            // moreover the extended handshake message type map is additive,
            // so we can't learn about the peer turning off extensions solely from the message
            connection.hasUtMetadata = true
        }
    }


    private fun consume(message: UtMetadata, connection: Connection) {
        if (doConsume) {

            // being lenient herer and not checking if the peer advertised ut_metadata support
            when (message.metaType) {
                MetaType.DATA -> {
                    val totalSize = message.totalSize
                    check(totalSize < META_EXCHANGE_MAX_SIZE) {
                        "Declared metadata size is too large: " + totalSize +
                                "; max allowed is " + META_EXCHANGE_MAX_SIZE
                    }
                    processMetadataBlock(connection, message.pieceIndex, totalSize, message.data)

                    connection.withoutMetadata = TimeSource.Monotonic.markNow()

                }

                MetaType.REJECT -> {
                    connection.withoutMetadata = TimeSource.Monotonic.markNow()
                }

                else -> {}
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun processMetadataBlock(
        connection: Connection,
        pieceIndex: Int, totalSize: Int, data: ByteArray
    ) {
        if (metadata == null) {
            metadata = ExchangedMetadata(totalSize)
        }

        if (!metadata!!.isBlockPresent(pieceIndex)) {
            metadata!!.setBlock(pieceIndex, data)

            if (metadata!!.isComplete) {
                val digest = metadata!!.digest()

                if (digest.contentEquals(torrentId.bytes)) {

                    var fetchedTorrent: Torrent? = null
                    try {
                        fetchedTorrent = buildTorrent(metadata!!.data())
                    } catch (throwable: Throwable) {
                        debug("MetadataConsumer", throwable)
                        metadata = null
                    }

                    if (fetchedTorrent != null) {

                        dataStorage.initialize(fetchedTorrent, metadata!!.data())

                        done.store(true)
                        connection.requestedFirst = null
                        connection.requestedAllPeers = false

                        doConsume = false
                        metadata = null
                    }
                } else {
                    // restart the process
                    metadata = null
                }
            }
        }
    }


    @OptIn(ExperimentalAtomicApi::class)
    override fun produce(connection: Connection, messageConsumer: (Message) -> Unit) {
        // stop here if metadata has already been fetched
        if (done.load()) {
            return
        }

        if (connection.hasUtMetadata) {
            var time = connection.withoutMetadata
            if (time != null) {
                if (time.elapsedNow().inWholeMilliseconds >= WAIT_BEFORE_REREQUESTING_AFTER_REJECT
                ) {
                    connection.withoutMetadata = null
                }
            }
            time = connection.withoutMetadata
            if (time == null) {
                if (metadata == null) {
                    val time = connection.requestedFirst
                    if (time == null ||
                        (time.elapsedNow().inWholeMilliseconds > FIRST_BLOCK_ARRIVAL_TIMEOUT)
                    ) {
                        connection.requestedFirst = TimeSource.Monotonic.markNow()
                        // start with the first piece of metadata
                        messageConsumer.invoke(request(0))
                    }
                } else if (!connection.requestedAllPeers) {
                    connection.requestedAllPeers = true
                    // starting with block #1 because by now we should have already received block #0
                    for (i in 1 until metadata!!.blockCount) {
                        messageConsumer.invoke(request(i))
                    }
                }
            }
        }
    }

    /**
     * @return Torrent, blocking the calling thread if it hasn't been fetched yet
     */
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun waitForTorrent() {
        while (!done.load()) {
            delay(100)
        }
    }
}
