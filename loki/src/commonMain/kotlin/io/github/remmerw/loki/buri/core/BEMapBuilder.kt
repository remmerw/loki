package io.github.remmerw.loki.buri.core

internal class BEMapBuilder internal constructor() : BEPrefixedTypeBuilder() {
    private val map: MutableMap<String, BEObject> = mutableMapOf()
    private var keyBuilder: BEStringBuilder? = null
    private var valueBuilder: BEObjectBuilder? = null

    override fun doAccept(b: Int): Boolean {
        if (keyBuilder == null) {
            keyBuilder = BEStringBuilder()
        }
        if (valueBuilder == null) {
            if (!keyBuilder!!.accept(b)) {
                val valueType = getTypeForPrefix(b.toChar())
                valueBuilder = builderForType(valueType)
                return valueBuilder!!.accept(b)
            }
        } else {
            if (!valueBuilder!!.accept(b)) {
                map[keyBuilder!!.build().string()] = valueBuilder!!.build()
                keyBuilder = null
                valueBuilder = null
                return accept(b)
            }
        }
        return true
    }

    override fun doBuild(): BEMap {
        return BEMap(map)
    }

    override fun acceptEOF(): Boolean {
        return keyBuilder == null && valueBuilder == null
    }

    override fun type(): BEType {
        return BEType.MAP
    }
}
