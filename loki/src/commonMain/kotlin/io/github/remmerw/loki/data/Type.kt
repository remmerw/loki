package io.github.remmerw.loki.data

enum class Type {
    UtMetadata, ExtendedHandshake, PeerExchange, Bitfield, Cancel, Choke, Have, Interested,
    NotInterested, Piece, Port, Request, Handshake, KeepAlive, Unchoke
}