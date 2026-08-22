import java.nio.ByteBuffer
import java.security.MessageDigest

internal fun ByteBuffer.toSha1(): ByteArray {
    rewind()
    val md = MessageDigest.getInstance("SHA-1")
    md.update(this)
    return md.digest()
}

internal fun ByteBuffer.readMemory(
        offset: Int,
        length: Int,
    ): ByteArray {
        rewind()
        position(offset)
        val result = ByteArray(length)
        get(result)
        return result
    }

    
internal fun ByteBuffer.writeMemory(
        bytes: ByteArray,
        offset: Int,
    ) {
        rewind()
        position(offset)
        put(bytes)
    }