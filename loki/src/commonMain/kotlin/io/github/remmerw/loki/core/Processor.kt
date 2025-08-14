package io.github.remmerw.loki.core

import io.github.remmerw.loki.data.ExtendedProtocol
import io.github.remmerw.loki.data.HANDSHAKE_RESERVED_LENGTH
import io.github.remmerw.loki.data.Handshake
import io.github.remmerw.loki.data.PROTOCOL_NAME
import io.github.remmerw.loki.data.TorrentId
import io.github.remmerw.loki.debug
import io.github.remmerw.nott.PeerResponse
import io.github.remmerw.nott.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

internal const val MAX_CONCURRENCY: Int = 32

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


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalAtomicApi::class)
internal fun CoroutineScope.performRequester(
    store: Store,
    counter: AtomicInt,
    channel: ReceiveChannel<PeerResponse>
): ReceiveChannel<InetSocketAddress> = produce {

    channel.consumeEach { response ->
        try {
            store.store(response.peer)
        } catch (throwable: Throwable) {
            debug(throwable)
        }
        response.addresses.forEach { address ->

            launch {
                try {
                    if (address.address.isReachable(3000)) {
                        counter.incrementAndFetch()
                        send(address)
                    }
                } catch (throwable: Throwable) {
                    debug(throwable)
                }
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun CoroutineScope.performConnection(
    peerId: ByteArray,
    torrentId: TorrentId,
    extendedProtocol: ExtendedProtocol,
    handshakeHandlers: Collection<HandshakeHandler>,
    dataStorage: DataStorage,
    worker: Worker,
    channel: ReceiveChannel<InetSocketAddress>
): ReceiveChannel<Any> = produce {

    val semaphore = Semaphore(MAX_CONCURRENCY)
    channel.consumeEach { address ->

        launch {
            semaphore.withPermit {
                ensureActive()

                try {
                    Socket().use { socket ->
                        socket.soTimeout = 10000
                        socket.connect(address)

                        val connection = Connection(
                            address, dataStorage,
                            worker, socket, extendedProtocol
                        )
                        val handshake = withTimeoutOrNull(3000) {
                            performHandshake(
                                connection,
                                peerId,
                                torrentId,
                                handshakeHandlers,
                                worker
                            )
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
                            } finally {
                                worker.purgedConnections()
                            }
                        }
                    }
                } catch (throwable: Throwable) {
                    debug("Processor.performConnection " + throwable.message)
                }
            }
        }
    }
}

