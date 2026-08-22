import java.nio.ByteBuffer
import java.security.MessageDigest

fun ByteBuffer.toSha1(): ByteArray {
    val md = MessageDigest.getInstance("SHA-1")
    md.update(this)
    return md.digest()
}
