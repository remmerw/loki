package io.github.remmerw.loki.grid

import kotlinx.io.Buffer
import kotlin.collections.iterator

internal class ExtendedProtocol(messageHandlers: List<ExtendedMessageHandler>) : MessageHandler {

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

    fun getTypeNameForJavaType(type: Type): String {
        return typeMap[type]!!
    }

    override fun supportedTypes(): Collection<Type> = handlers.keys


    override fun doDecode(peer: Peer, buffer: Buffer): Message {
        val typeId = buffer.readByte()
        val handler: MessageHandler?
        if (typeId == EXTENDED_HANDSHAKE_TYPE_ID) {
            handler = extendedHandshakeHandler
        } else {
            val extendedType = getTypeNameForId(typeId)
            handler = handlersByTypeName[extendedType]
        }

        return checkNotNull(handler).doDecode(peer, buffer)
    }

    override fun doEncode(peer: Peer, message: Message, buffer: Buffer) {
        val extendedMessage = message as ExtendedMessage
        doEncode(peer, extendedMessage, extendedMessage.type, buffer)
    }


    private fun doEncode(peer: Peer, message: Message, type: Type, buffer: Buffer) {
        if (Type.ExtendedHandshake == type) {
            buffer.writeByte(EXTENDED_HANDSHAKE_TYPE_ID)
        } else {
            val typeName = getTypeNameForJavaType(type)
            var typeId: Int? = null
            for (entry in extendedHandshakeHandler.getPeerTypeMapping(peer)) {
                if (entry.value == typeName) {
                    typeId = entry.key
                }
            }
            checkNotNull(typeId) { "Peer does not support extension message: $typeName" }
            buffer.writeByte(typeId.toByte())
        }

        checkNotNull(handlers[type]).doEncode(peer, message, buffer)

    }

}
