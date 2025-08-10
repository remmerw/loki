package io.github.remmerw.loki.data

internal class Choke : Message {

    override val type: Type
        get() = Type.Choke

}
