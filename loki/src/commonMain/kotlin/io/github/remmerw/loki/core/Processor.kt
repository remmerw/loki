package io.github.remmerw.loki.core

import io.github.remmerw.loki.data.ExtendedProtocol
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

internal const val MAX_CONCURRENCY: Int = 32


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

                try {
                    Socket().use { socket ->
                        socket.soTimeout = 10000
                        socket.connect(address, 3000)

                        Connection(
                            address, dataStorage,
                            worker, socket, extendedProtocol
                        ).use { connection ->

                            withTimeout(3000) {
                                connection.performHandshake(
                                    peerId,
                                    torrentId,
                                    handshakeHandlers
                                )
                            }


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
                        }
                    }
                } catch (throwable: Throwable) {
                    debug("Processor.performConnection " + throwable.message)
                }
            }
        }
    }
}

