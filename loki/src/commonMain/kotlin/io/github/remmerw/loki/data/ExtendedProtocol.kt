package io.github.remmerw.loki.data

import kotlinx.io.Buffer

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
        message as ExtendedMessage

        val temp = Buffer()
        if (message is ExtendedHandshake) {
            temp.writeByte(EXTENDED_HANDSHAKE_TYPE_ID)
        } else {
            val typeName = getTypeNameForJavaType(message.type)
            var typeId: Int? = null
            for (entry in extendedHandshakeHandler.getPeerTypeMapping(peer)) {
                if (entry.value == typeName) {
                    typeId = entry.key
                }
            }
            checkNotNull(typeId) { "Peer does not support extension message: $typeName" }
            temp.writeByte(typeId.toByte())
        }
        checkNotNull(handlers[message.type]).doEncode(peer, message, temp)

        val payloadLength = temp.size
        val size = (payloadLength + MESSAGE_TYPE_SIZE).toInt()
        buffer.writeInt(size)
        buffer.writeByte(message.messageId)
        buffer.transferFrom(temp)

    }

}
