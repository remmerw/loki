package io.github.remmerw.loki.core

import io.github.remmerw.loki.data.ExtendedProtocol
import io.github.remmerw.loki.data.HANDSHAKE_RESERVED_LENGTH
import io.github.remmerw.loki.data.Handshake
import io.github.remmerw.loki.data.PROTOCOL_NAME
import io.github.remmerw.loki.data.TorrentId
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal const val MAX_CONCURRENCY: Int = 10

private suspend fun performHandshake(
    connection: Connection,
    peerId: ByteArray,
    torrentId: TorrentId,
    handshakeHandlers: Collection<HandshakeHandler>
): Boolean {
    try {
        val handshake = Handshake(
            PROTOCOL_NAME,
            ByteArray(HANDSHAKE_RESERVED_LENGTH), torrentId, peerId
        )
        handshakeHandlers.forEach { handler: HandshakeHandler ->
            handler.processOutgoingHandshake(handshake)
        }

        connection.posting(handshake, byteArrayOf())

        val peerHandshake = connection.receiveHandshake()

        if (peerHandshake is Handshake) {
            if (torrentId == peerHandshake.torrentId) {
                handshakeHandlers.forEach { handler: HandshakeHandler ->
                    handler.processIncomingHandshake(connection)
                }
                return true
            }
        }
        connection.close()
        return false
    } catch (_: Exception) {
        connection.close()
        return false
    }
}

internal suspend fun performHandshake(
    connection: Connection,
    peerId: ByteArray,
    torrentId: TorrentId,
    handshakeHandlers: Collection<HandshakeHandler>,
    worker: Worker
): Boolean {

    val existing = worker.getConnection(connection.address())
    if (existing != null) {
        return false
    }

    val success = performHandshake(connection, peerId, torrentId, handshakeHandlers)
    if (success) {
        val existing = worker.getConnection(connection.address())
        if (existing != null) {
            connection.close()
            return false
        } else {
            worker.addConnection(connection)
            return true
        }
    }

    return false
}

private fun process(connection: Connection): Unit = runBlocking(Dispatchers.IO) {
    launch {
        connection.reading()
    }
    launch {
        connection.posting()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun CoroutineScope.processMessages(
    channel: ReceiveChannel<Connection>
): ReceiveChannel<Any> = produce {

    val semaphore = Semaphore(2 * MAX_CONCURRENCY)
    channel.consumeEach { connection ->
        launch {
            semaphore.withPermit {
                process(connection)
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun CoroutineScope.performHandshake(
    peerId: ByteArray,
    torrentId: TorrentId,
    handshakeHandlers: Collection<HandshakeHandler>,
    worker: Worker,
    channel: ReceiveChannel<Connection>
): ReceiveChannel<Connection> = produce {

    val semaphore = Semaphore(MAX_CONCURRENCY)
    channel.consumeEach { connection ->
        launch {
            semaphore.withPermit {
                withTimeoutOrNull(3000) {
                    if (performHandshake(
                            connection,
                            peerId,
                            torrentId,
                            handshakeHandlers,
                            worker
                        )
                    ) {
                        send(connection)
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalCoroutinesApi::class)
internal fun CoroutineScope.performConnection(
    extendedProtocol: ExtendedProtocol,
    dataStorage: DataStorage,
    worker: Worker,
    selectorManager: SelectorManager,
    channel: ReceiveChannel<InetSocketAddress>
): ReceiveChannel<Connection> = produce {

    val semaphore = Semaphore(MAX_CONCURRENCY)
    channel.consumeEach { address ->

        launch {
            semaphore.withPermit {
                withTimeoutOrNull(3000) {
                    try {
                        val socket = aSocket(selectorManager)
                            .tcp().connect(address) {
                                socketTimeout =
                                    30.toDuration(DurationUnit.SECONDS).inWholeMilliseconds
                            }
                        send(
                            Connection(address, dataStorage, worker, socket, extendedProtocol)
                        )
                    } catch (_: Throwable) {

                        // this is the normal case when address is unreachable or timeouted
                    }
                }
            }
        }
    }
}

