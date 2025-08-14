package io.github.remmerw.loki.data

import io.github.remmerw.buri.BEReader
import kotlinx.io.Buffer
import java.net.InetSocketAddress

internal class ExtendedProtocol(messageHandlers: List<ExtendedMessageHandler>) {

    private val extendedHandshakeHandler = ExtendedHandshakeHandler()
    private val handlers: MutableMap<Type, MessageHandler> = mutableMapOf()
    private val uniqueTypes: MutableMap<String, Type> = mutableMapOf()
    private val handlersByTypeName: MutableMap<String, ExtendedMessageHandler> = mutableMapOf()
    private val nameMap: MutableMap<Byte, String> = mutableMapOf()
    private val typeMap: MutableMap<Type, String> = mutableMapOf()


    init {
        handlers[Type.ExtendedHandshake] = extendedHandshakeHandler

        messageHandlers.forEach { handler ->
            nameMap[handler.localTypeId()] = handler.localName()
            handler.supportedTypes()
                .forEach { messageType -> typeMap[messageType] = handler.localName() }

            if (handler.supportedTypes().isEmpty()) {
                throw RuntimeException("No supported types declared in handler")
            } else {
                uniqueTypes[handler.localName()] = handler.supportedTypes().iterator().next()
            }
            handler.supportedTypes().forEach { messageType ->
                if (handlers.containsKey(messageType)) {
                    throw RuntimeException(
                        "Encountered duplicate handler for message type: $messageType"
                    )
                }
                handlers[messageType] = handler
            }
            this.handlersByTypeName[handler.localName()] = handler
        }
    }

    private fun getTypeNameForId(typeId: Byte): String {
        return nameMap[typeId]!!
    }

    fun getTypeNameFor(type: Type): String {
        return typeMap[type]!!
    }


    fun doDecode(address: InetSocketAddress, reader: BEReader): ExtendedMessage {
        val typeId = reader.read()
        val handler: MessageHandler?
        if (typeId == EXTENDED_HANDSHAKE_TYPE_ID) {
            handler = extendedHandshakeHandler
        } else {
            val extendedType = getTypeNameForId(typeId)
            handler = handlersByTypeName[extendedType]
        }
        return checkNotNull(handler).doDecode(address, reader)
    }

    fun doEncode(address: InetSocketAddress, message: ExtendedMessage, buffer: Buffer) {

        buffer.writeByte(EXTENDED_MESSAGE_ID)
        if (message is ExtendedHandshake) {
            buffer.writeByte(EXTENDED_HANDSHAKE_TYPE_ID)
        } else {
            val typeName = getTypeNameFor(message.type)
            var typeId: Int? = null
            for (entry in extendedHandshakeHandler.getPeerTypeMapping(address)) {
                if (entry.value == typeName) {
                    typeId = entry.key
                }
            }
            checkNotNull(typeId) { "Peer does not support extension message: $typeName" }
            buffer.writeByte(typeId.toByte())
        }
        checkNotNull(handlers[message.type]).doEncode(message, buffer)
    }

}
