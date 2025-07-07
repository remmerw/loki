package io.github.remmerw.loki.benc

internal class BEIntegerBuilder : BEPrefixedTypeBuilder() {
    private val stringBuilder = StringBuilder()

    override fun doAccept(b: Int): Boolean {
        val c = b.toChar()
        if (c.isDigit() || stringBuilder.isEmpty() && c == '-') {
            stringBuilder.append(c)
            return true
        }
        throw IllegalArgumentException("Unexpected token while reading integer (as ASCII char): $c")
    }

    override fun acceptEOF(): Boolean {
        return true
    }

    override fun type(): BEType {
        return BEType.INTEGER
    }

    override fun doBuild(): BEInteger {
        return BEInteger(stringBuilder.toString().toLong())
    }
}
