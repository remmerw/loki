package io.github.remmerw.loki.grid


internal data class Port(val port: Int) : Message {

    override val type: Type
        get() = Type.Port

    override val messageId: Byte
        get() = PORT_ID

    init {
        require(!(port < 0 || port > 65535)) { "Invalid argument: port ($port)" }
    }
}
