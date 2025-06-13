package io.github.remmerw.loki.buri.core

internal class BEListBuilder : BEPrefixedTypeBuilder() {
    private val objects: MutableList<BEObject> = mutableListOf()
    private var builder: BEObjectBuilder? = null

    override fun doAccept(b: Int): Boolean {
        if (builder == null) {
            val type = getTypeForPrefix(b.toChar())
            builder = builderForType(type)
        }
        if (!builder!!.accept(b)) {
            objects.add(builder!!.build())
            builder = null
            return accept(b)
        }
        return true
    }

    override fun acceptEOF(): Boolean {
        return builder == null
    }

    override fun doBuild(): BEList {
        return BEList(objects)
    }

    override fun type(): BEType {
        return BEType.LIST
    }
}
