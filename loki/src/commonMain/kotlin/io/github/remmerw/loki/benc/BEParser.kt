package io.github.remmerw.loki.benc

import kotlinx.io.Source


internal class BEParser internal constructor(
    private val type: BEType,
    private val scanner: Scanner
) {

    fun readType(): BEType {
        return type
    }


    fun readMap(): BEMap {
        return readMapObject(BEMapBuilder())
    }

    fun readList(): BEList {
        return readListObject(BEListBuilder())
    }

    fun readString(): BEString {
        return readStringObject(BEStringBuilder())
    }

    fun readInteger(): BEInteger {
        return readIntegerObject(BEIntegerBuilder())
    }

    private fun readListObject(builder: BEListBuilder): BEList {
        check(this.type == BEType.LIST) {
            "Can't read " + BEType.LIST.name.lowercase() +
                    " from: " + type.name.lowercase()
        }
        return scanner.readListObject(builder)
    }

    private fun readMapObject(builder: BEMapBuilder): BEMap {
        check(this.type == BEType.MAP) {
            "Can't read " + BEType.MAP.name.lowercase() +
                    " from: " + type.name.lowercase()
        }
        return scanner.readMapObject(builder)
    }

    private fun readIntegerObject(builder: BEIntegerBuilder): BEInteger {
        check(this.type == BEType.INTEGER) {
            "Can't read " + BEType.INTEGER.name.lowercase() +
                    " from: " + type.name.lowercase()
        }
        return scanner.readIntegerObject(builder)
    }

    private fun readStringObject(builder: BEStringBuilder): BEString {
        check(this.type == BEType.STRING) {
            "Can't read " + BEType.STRING.name.lowercase() +
                    " from: " + type.name.lowercase()
        }
        return scanner.readStringObject(builder)
    }


}

const val DELIMITER: Char = ':'
const val EOF: Char = 'e'
const val INTEGER_PREFIX: Char = 'i'
const val LIST_PREFIX: Char = 'l'
const val MAP_PREFIX: Char = 'd'

internal class Scanner(private val source: Source) {

    fun read(): Int {
        if (!source.exhausted()) {
            return source.readByte().toInt() and 0xFF
        }
        return -1
    }

    fun peek(): Int {
        if (!source.exhausted()) {
            return source.peek().readByte().toInt() and 0xFF
        }
        return -1
    }

    fun readMapObject(builder: BEMapBuilder): BEMap {
        var c: Int

        while ((peek().also { c = it }) != -1) {
            if (builder.accept(c)) {
                read()
            } else {
                break
            }
        }
        return builder.build() as BEMap
    }

    fun readListObject(builder: BEListBuilder): BEList {
        var c: Int

        while ((peek().also { c = it }) != -1) {
            if (builder.accept(c)) {
                read()
            } else {
                break
            }
        }
        return builder.build() as BEList
    }

    fun readStringObject(builder: BEStringBuilder): BEString {
        var c: Int

        while ((peek().also { c = it }) != -1) {
            if (builder.accept(c)) {
                read()
            } else {
                break
            }
        }
        return builder.build()
    }

    fun readIntegerObject(builder: BEIntegerBuilder): BEInteger {
        var c: Int

        while ((peek().also { c = it }) != -1) {
            if (builder.accept(c)) {
                read()
            } else {
                break
            }
        }
        return builder.build() as BEInteger
    }
}

/**
 * Create a parser for the provided bencoded document.
 */
internal fun createParser(buffer: Source): BEParser {
    val scanner = Scanner(buffer)
    val type = getTypeForPrefix(scanner.peek().toChar())
    return BEParser(type, scanner)
}

internal fun getPrefixForType(type: BEType): Char {
    return when (type) {
        BEType.INTEGER -> INTEGER_PREFIX
        BEType.LIST -> LIST_PREFIX
        BEType.MAP -> MAP_PREFIX
        else -> throw IllegalArgumentException("Unknown type: " + type.name.lowercase())
    }
}

internal fun getTypeForPrefix(c: Char): BEType {
    if (c.isDigit()) {
        return BEType.STRING
    }
    return when (c) {
        INTEGER_PREFIX -> {
            BEType.INTEGER
        }

        LIST_PREFIX -> {
            BEType.LIST
        }

        MAP_PREFIX -> {
            BEType.MAP
        }

        else -> throw IllegalStateException("Invalid type prefix: $c")
    }
}

internal fun builderForType(type: BEType): BEObjectBuilder {
    return when (type) {
        BEType.STRING -> {
            BEStringBuilder()
        }

        BEType.INTEGER -> {
            BEIntegerBuilder()
        }

        BEType.LIST -> {
            BEListBuilder()
        }

        BEType.MAP -> {
            BEMapBuilder()
        }

    }
}