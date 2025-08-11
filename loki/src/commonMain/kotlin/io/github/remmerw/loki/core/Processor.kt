package io.github.remmerw.loki.core

import io.github.remmerw.loki.Store
import io.github.remmerw.loki.data.ExtendedProtocol
import io.github.remmerw.loki.data.HANDSHAKE_RESERVED_LENGTH
import io.github.remmerw.loki.data.Handshake
import io.github.remmerw.loki.data.PROTOCOL_NAME
import io.github.remmerw.loki.data.TorrentId
import io.github.remmerw.loki.debug
import io.github.remmerw.nott.PeerResponse
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.util.network.hostname
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal const val MAX_CONCURRENCY: Int = 25

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


@OptIn(ExperimentalCoroutinesApi::class)
internal fun CoroutineScope.performConnection(
    selectorManager: SelectorManager,
    peerId: ByteArray,
    torrentId: TorrentId,
    extendedProtocol: ExtendedProtocol,
    handshakeHandlers: Collection<HandshakeHandler>,
    dataStorage: DataStorage,
    worker: Worker,
    store: Store,
    channel: ReceiveChannel<PeerResponse>
): ReceiveChannel<Any> = produce {

    val semaphore = Semaphore(MAX_CONCURRENCY)
    channel.consumeEach { response ->
        try {
            store.store(response.peer)
        } catch (throwable: Throwable) {
            debug(throwable)
        }
        response.addresses.forEach { address ->
            semaphore.acquire()
            launch {
                try {
                    val isa = InetSocketAddress(address.hostname, address.port)
                    val socket = withTimeoutOrNull(3000) {
                        try {
                            return@withTimeoutOrNull aSocket(selectorManager)
                                .tcp().connect(isa) {
                                    socketTimeout =
                                        30.toDuration(DurationUnit.SECONDS).inWholeMilliseconds
                                }
                        } catch (_: Throwable) {
                        }
                        return@withTimeoutOrNull null
                    }
                    if (socket != null) {
                        val connection = Connection(
                            isa, dataStorage,
                            worker, socket, extendedProtocol
                        )
                        val handshake = withTimeoutOrNull(3000) {
                            try {
                                return@withTimeoutOrNull performHandshake(
                                    connection,
                                    peerId,
                                    torrentId,
                                    handshakeHandlers,
                                    worker
                                )
                            } catch (_: Throwable) {
                            }
                            return@withTimeoutOrNull null
                        }
                        if (handshake == true) {
                            try {
                                withContext(Dispatchers.IO) {
                                    launch {
                                        connection.reading()
                                        coroutineContext.cancelChildren()
                                    }
                                    launch {
                                        connection.posting()
                                        coroutineContext.cancelChildren()
                                    }
                                }
                            } catch (throwable: Throwable) {
                                debug(throwable)
                            }
                        }
                    }
                } catch (throwable: Throwable) {
                    debug(throwable)
                } finally {
                    semaphore.release()
                }
            }
        }
    }
}

