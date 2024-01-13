fun startSocket() {
    SocketManager.establishConnection()
}

private object KribbzSocket : EndPoints {

    val mSocket: Socket = IO.socket(stagingUrl,
        IO.Options().apply {
            reconnection = true
            reconnectionDelay = RECONNECTION_DELAY
            reconnectionAttempts = RECONNECTION_ATTEMPTS
            secure = false
        })
}

object KribbzSocketManager : SocketEventListener.Listener,
    HeartBeat.HeartBeatListener,
    AppLog {

    private var chatQueue: Queue<MessageItem>? = null
    private var connectionLost = false
    private var listenersMap: ConcurrentHashMap<String, SocketEventListener> = ConcurrentHashMap()
    private var mCompositeDisposable = CompositeDisposable()
    private var mInsRoomList: RoomDetails? = null

    override fun onHeartBeat() {
        if (!mSocket.connected()) {
            mSocket.connect()
        }
    }

    override fun onEventCall(event: String, args: Array<out Any?>) {
        when (event) {
            Socket.EVENT_CONNECT -> {
                logInfo("************* Connected")
                mInsRoomList?.let { joinRoom(it.roomList) }
                getAllChatRooms()
                chatQueue?.let { resendQueueMessages() }
            }
            Socket.EVENT_DISCONNECT -> {
                logInfo("************* Disconnected")
            }
            Socket.EVENT_CONNECT_ERROR -> {
                logInfo("************* Error in Connection")
                mSocket.connect()
            }
            Socket.EVENT_CONNECT_TIMEOUT -> {

            }
            Socket.EVENT_MESSAGE -> {

                val rawMessage = args[0]?.toString()
                rawMessage?.let {

                    val jsonObjectPacket = JSONObject(rawMessage)
                    logInfo("Event:-${Socket.EVENT_MESSAGE} ******  ${jsonObjectPacket.toString(4)}")
                    val jsonObjectData = jsonObjectPacket.optJSONObject("data")
                    val jsonObjectMessage = jsonObjectData.optJSONObject("msg")

                    val chat = isInsLocationChat(jsonObjectMessage, jsonObjectData)
                    return@let chat?.apply {
                        try {
                            RxBus.send(RxChatParcel(this, SocketEvent.EVENT_SEND_COORDINATES))
                        } catch (e: Exception) {
                            logError(e.message)
                        }
                    } ?: run {
                        val message = parseMessage(jsonObjectData, jsonObjectMessage)
                        return@run message?.apply { createMessageInDb(this) }
                    }
                }
            }
        }
    }

    fun closeConnection() {

        chatQueue?.clear()
        mSocket.disconnect()
        mCompositeDisposable.clear()
        for ((key, value) in listenersMap) {
            mSocket.off(key, value)
        }
        chatQueue = null
    }

    fun emitEvent(event: String?, data: Any?) {
        event?.let { eventType ->
            return@let when (eventType) {
                SocketEvent.EVENT_JOIN_ROOM -> {
                    mInsRoomList = if (null != data && data is RoomDetails) data else null
                    if (null != mInsRoomList && !mSocket.connected()) {
                        logInfo("connecting mSocket...")
                        return@let mSocket.connect()
                    } else {
                        return@let mInsRoomList?.apply {
                            joinRoom(roomList)
                        } ?: run {
                            getAllChatRooms()
                        }
                    }
                }
                SocketEvent.EVENT_SEND_MESSAGE -> {
                    return@let data?.takeIf { isSocketConnected() }?.apply {
                        if (this is MessageChat) sendMessage(this)
                    } ?: run {
                        if (data is MessageChat) chatQueue?.add(data)
                    }
                }
                SocketEvent.EVENT_SEND_COORDINATES -> {
                    return@let data?.let {
                        if (isSocketConnected() && it is MessageLocation) {
                            sendInsPosition(it)
                        }
                    }
                }
                else -> {
                    logError("Unknown event ")
                }
            }
        }
    }

    @Synchronized
    fun establishConnection() {

        if (isConnected() || null != chatQueue) return
        connectionLost = false

        registerConnectivityCallbacks()
        chatQueue = chatQueue ?: LinkedList<MessageItem>()

        getSocketListener()
        for ((key, value) in listenersMap) {
            mSocket.on(key, value)
        }

        logInfo("*************** Establishing connection ")
        mSocket.connect()
    }

    fun isConnected(): Boolean = mSocket.connected()
    private fun createMessageInDb(message: ChatMessage) {

        val taskData = ChatMessageCreateTaskData(message)
        val disposable =
            RxTask().call<ChatMessageCreateTask, ChatMessageCreateTaskResult>(taskData).doFinally {
            }.subscribe {
                if (it.isSuccess) {
                    try {
                        RxBus.send(RxChatParcel(message, SocketEvent.EVENT_SEND_MESSAGE, false))
                        RxBus.send(RxChatNotifier(message, it.unreadCount, false))
                    } catch (e: Exception) {
                        logError("createMessageInDb ${e.message}")
                    }
                }
            }
        mCompositeDisposable.add(disposable)
    }

    private fun getAllChatRooms() {
        val disposable = RxTask().call<ChatRoomReadTask, ChatRoomReadTaskResult>().doFinally {
        }.subscribe {
            if (it.isSuccess && mSocket.connected()) {
                for (i: Int in 0 until (it.chatRoomList?.size ?: 0)) {
                    it.chatRoomList?.get(i)
                        ?.roomId?.let { roomId -> joinRoom(roomId.toString()) }
                }
            } else if (!it.isSuccess) {
                Thread.sleep(500)
                if (null == mInsRoomList) {
                    logInfo("************** Closing Connection, No rooms found")
                    mSocket.disconnect()
                }
            }
        }
        mCompositeDisposable.add(disposable)
    }

    private fun getSocketListener() {

        listenersMap[Socket.EVENT_CONNECT] = SocketEventListener(Socket.EVENT_CONNECT, this)
        listenersMap[Socket.EVENT_DISCONNECT] = SocketEventListener(Socket.EVENT_DISCONNECT, this)
        listenersMap[Socket.EVENT_CONNECT_ERROR] =
            SocketEventListener(Socket.EVENT_CONNECT_ERROR, this)
        listenersMap[Socket.EVENT_CONNECT_TIMEOUT] =
            SocketEventListener(Socket.EVENT_CONNECT_TIMEOUT, this)
        listenersMap[Socket.EVENT_MESSAGE] = SocketEventListener(Socket.EVENT_MESSAGE, this)
    }

    private fun isInsLocationChat(
        jsonObjectMessage: JSONObject,
        jsonObjectData: JSONObject
    ): MessageLocation? {
        if (null != jsonObjectMessage.optJSONObject("coordinates")) {
            val jsonLatLang = jsonObjectMessage.optJSONObject("coordinates")
            return MessageLocation(
                jsonObjectData.optString(DataKeys.ROOM),
                LatLng(
                    jsonLatLang.optDouble(DataKeys.LATITUDE),
                    jsonLatLang.optDouble(DataKeys.LONGITUDE)
                )
            )
        } else if (null != jsonObjectMessage.optString(DataKeys.LATITUDE, null)
            && null != jsonObjectMessage.optString(DataKeys.LATITUDE, null)
        ) {
            return MessageLocation(
                jsonObjectData.optString(DataKeys.ROOM),
                LatLng(
                    jsonObjectMessage.optDouble(DataKeys.LATITUDE),
                    jsonObjectMessage.optDouble(DataKeys.LONGITUDE)
                ),
                jsonObjectMessage.optString("receiver")
            )
        } else return null
    }

    @Synchronized
    private fun isSocketConnected(): Boolean {
        return if (!mSocket.connected()) {
            mSocket.connect()
            for ((key, value) in listenersMap) {
                mSocket.on(key, value)
            }
            logInfo("reconnecting mSocket...")
            false
        } else true
    }

    private fun joinRoom(roomList: List<String>) {
        (0 until roomList.size).forEach { index ->
            if (roomList[index].isNotEmpty()) joinRoom(roomList[index])
        }
    }

    private fun joinRoom(room: String) {
        logInfo("Event:-${SocketEvent.EVENT_JOIN_ROOM} ************** $room")
        mSocket.emit(
            SocketEvent.EVENT_JOIN_ROOM,
            JSONObject().put(DataKeys.ROOM, room)
        )
    }

    private fun parseMessage(
        jsonObjectData: JSONObject,
        jsonObjectMessage: JSONObject
    ): ChatMessage? {

        val timestamp = jsonObjectMessage.optString(DataKeys.TIME)
        timestamp?.takeIf { it.isNotEmpty() }?.let {
            val arr = formatDateAndTimeFromPSTToLocal(
                DF_HYPHENATED_DD_MM_YYY, TIME_FORMAT_GEN,
                DF_HYPHENATED_YYY_MM_DD, TIME_FORMAT_TWELVE_HOUR,
                it
            )
            return ChatMessage(
                jsonObjectMessage.optString(DataKeys.MESSAGE_ID),
                jsonObjectData.optInt(DataKeys.ROOM),
                "${arr[0]} ${arr[1]}",
                jsonObjectMessage.optString(DataKeys.MESSAGE),
                ChatMessageStatus.UNREAD.value,
                ChatMessageType.RECEIVED.value,
                jsonObjectMessage.optInt(DataKeys.SENDER)
            )
        }
        return null
    }

    private fun registerConnectivityCallbacks() {

        val systemService =
            applicationContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val builder = NetworkRequest.Builder()
        systemService.registerNetworkCallback(
            builder.build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network?) {
                    super.onLost(network)
                    logInfo("Closing connection, Connectivity manager broadcast#Lost")
                    connectionLost = true
                    closeConnection()
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    logInfo("Closing connection, Connectivity manager broadcast#Unavailable")
                    connectionLost = true
                    closeConnection()
                }

                override fun onAvailable(network: Network?) {
                    super.onAvailable(network)
                    if (!mSocket.connected()
                        && connectionLost
                    ) {
                        connectionLost = false
                        establishConnection()
                    }
                }
            }
        )
    }

    private fun resendQueueMessages() {
        val chat = chatQueue?.poll()
        if (chat != null && chat is MessageChat) {
            sendMessage(chat)
            resendQueueMessages()
        }
    }

    private fun sendInsPosition(messageLocation: MessageLocation) {
        val jsonMessageHeader = JSONObject()
        jsonMessageHeader.put(DataKeys.ROOM, messageLocation.roomId)
        jsonMessageHeader.put("type", "chat")

        val jsonMessage = JSONObject()
        jsonMessage.put("thread", messageLocation.roomId)

        val jsonLatLang = JSONObject()
        jsonLatLang.put("latitude", messageLocation.latLng.latitude.toString())
        jsonLatLang.put("longitude", messageLocation.latLng.longitude.toString())

        jsonMessage.put("coordinates", jsonLatLang)
        jsonMessageHeader.put("msg", jsonMessage)

        mSocket.emit(SocketEvent.EVENT_SEND_COORDINATES, jsonMessageHeader)
    }

    private fun sendMessage(chatMessage: MessageChat) {

        val jsonMessageBody = JSONObject()
        jsonMessageBody.put("type", "chat")
        jsonMessageBody.put("thread", chatMessage.roomId.toString())
        jsonMessageBody.put(DataKeys.SENDER, chatMessage.senderId)
        jsonMessageBody.put("receiver", chatMessage.recipientId)
        jsonMessageBody.put(DataKeys.TIME, formatLocalDateToPST(Date()))
        jsonMessageBody.put(DataKeys.MESSAGE_ID, UUID.randomUUID().toString())
        jsonMessageBody.put(DataKeys.MESSAGE, chatMessage.message)

        val jsonMessageHeader = JSONObject()
        jsonMessageHeader.put("user", chatMessage.senderName)
        jsonMessageHeader.put(DataKeys.ROOM, chatMessage.roomId.toString())
        jsonMessageHeader.put("msg", jsonMessageBody)

        mSocket.emit(SocketEvent.EVENT_SEND_MESSAGE, jsonMessageHeader)
    }
}
