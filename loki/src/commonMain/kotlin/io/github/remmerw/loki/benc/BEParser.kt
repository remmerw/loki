package io.github.remmerw.loki.benc

import kotlinx.io.Source


/**
 * BEncoding parser. Should be closed when the source is processed.
 */
class BEParser internal constructor(private val type: BEType, private val scanner: Scanner) {
    /**
     * Read type of the root object of the bencoded document that this parser was created for.
     */
    fun readType(): BEType {
        return type
    }

    /**
     * Try to read the document's root object as a bencoded dictionary.
     */
    fun readMap(): BEMap {
        return readMapObject(BEMapBuilder())
    }


    private fun readMapObject(builder: BEMapBuilder): BEMap {
        check(this.type == BEType.MAP) {
            "Can't read " + BEType.MAP.name.lowercase() +
                    " from: " + type.name.lowercase()
        }
        // relying on the default constructor being present
        return scanner.readMapObject(builder)
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