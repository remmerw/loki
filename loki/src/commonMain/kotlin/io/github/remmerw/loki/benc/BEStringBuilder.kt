package io.github.remmerw.loki.benc


internal class BEStringBuilder : BEObjectBuilder {
    private var numericLength: String = ""
    private var result: ByteArray = byteArrayOf()
    private var length = 0
    private var bytesAcceptedCount = 0
    private var shouldReadBody = false


    override fun accept(b: Int): Boolean {
        val c = b.toChar()
        if (shouldReadBody) {
            if (bytesAcceptedCount + 1 > length) {
                return false
            }
            result[bytesAcceptedCount] = b.toByte()
            bytesAcceptedCount++
            return true
        } else {

            if (c == DELIMITER) {
                shouldReadBody = true
                bytesAcceptedCount = 0
                length = numericLength.toInt()
                result = ByteArray(length)
                return true
            }
            require(c.isDigit()) {
                "Unexpected token while reading string's length (as ASCII char)"
            }
            numericLength += c
            bytesAcceptedCount++
            return true
        }
    }

    override fun build(): BEString {
        check(shouldReadBody) { "Can't build string: no content" }
        check(bytesAcceptedCount >= length) { "Can't build string: insufficient content" }
        return BEString(result)
    }

    override fun type(): BEType {
        return BEType.STRING
    }

}