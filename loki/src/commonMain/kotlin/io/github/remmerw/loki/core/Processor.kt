package io.github.remmerw.loki.core

import io.github.remmerw.loki.data.Messages
import io.github.remmerw.loki.data.HANDSHAKE_RESERVED_LENGTH
import io.github.remmerw.loki.data.Handshake
import io.github.remmerw.loki.data.PROTOCOL_NAME
import io.github.remmerw.loki.data.Peer
import io.github.remmerw.loki.data.TorrentId
import io.github.remmerw.loki.mdht.Address
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.DurationUnit
import kotlin.time.toDuration


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

        connection.posting(handshake)

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

    if (!worker.mightAdd()) {
        return false
    }

    val existing = worker.getConnection(connection.peer())
    if (existing != null) {
        return false
    }

    val success = performHandshake(connection, peerId, torrentId, handshakeHandlers)
    if (success) {
        val existing = worker.getConnection(connection.peer())
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


internal suspend fun connectAddress(selectorManager: SelectorManager, address: Address): Socket {
    val remoteAddress = InetSocketAddress(address.hostname(), address.port.toInt())

    return aSocket(selectorManager).tcp().connect(remoteAddress) {
        socketTimeout = 30.toDuration(DurationUnit.SECONDS).inWholeMilliseconds
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun CoroutineScope.processMessages(
    worker: Worker,
    channel: ReceiveChannel<Connection>
): ReceiveChannel<Any> = produce {


    channel.consumeEach { connection ->
        launch {
            while (!connection.isClosed) {
                ensureActive()
                val message = connection.reading()
                if (message == null) {
                    break
                }
                worker.consume(connection, message)
            }
        }
        launch {
            while (!connection.isClosed) {
                ensureActive()
                val send = worker.produce(connection)
                if (send != null) {
                    connection.posting(send)
                }
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

    channel.consumeEach { connection ->
        launch {
            withTimeoutOrNull(3000) {
                if (performHandshake(connection, peerId, torrentId, handshakeHandlers, worker)) {
                    send(connection)
                }
            }
        }
    }
}


@OptIn(ExperimentalCoroutinesApi::class)
internal fun CoroutineScope.performConnection(
    messages: Messages,
    worker: Worker,
    selectorManager: SelectorManager,
    channel: ReceiveChannel<Address>
): ReceiveChannel<Connection> = produce {

    channel.consumeEach { address ->
        launch {
            withTimeoutOrNull(3000) {
                try {
                    val socket = connectAddress(selectorManager, address)
                    val peer = Peer(address.address, address.port)
                    val connection = Connection(peer, worker, socket, messages)
                    send(connection)
                } catch (_: Throwable) {
                    // this is the normal case when address is unreachable or timeouted
                }
            }
        }
    }
}

