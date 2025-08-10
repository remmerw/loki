package io.github.remmerw.loki.data


internal data class Port(val port: Int) : Message {

    init {
        require(port >= 0 && port <= 65535) { "Invalid argument: port ($port)" }
    }
}
