package com.ezequielbrrt.domemory.services.multiplayer

import com.ezequielbrrt.domemory.core.model.Board
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/** Firebase adapter for the spec 10 room protocol. All move rules live in [MultiplayerTurnReducer]. */
class MultiplayerService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val now: () -> Long = { Instant.now().epochSecond },
) {
    suspend fun createRoom(board: Board): MultiplayerRoom {
        val uid = uid()
        val roomId = database.reference.child(ROOMS).push().key ?: error("Could not allocate room")
        var code = RoomCode.generate()
        for (attempt in 0 until 12) {
            if (!database.reference.child(CODES).child(code).get().await().exists()) break
            code = RoomCode.generate()
        }
        val timestamp = now()
        val room = MultiplayerRoom(roomId, code, MultiplayerRoomStatus.WAITING, timestamp, timestamp, uid, players = mapOf(uid to MultiplayerPlayer(uid, "Host", lastSeenAt = timestamp)), gameSource = if (board.isCustom) MultiplayerGameSource.CUSTOM else MultiplayerGameSource.FIREBASE, gameId = board.id, gameName = board.name, difficulty = board.difficulty, customGamePayload = board.takeIf { it.isCustom }?.let(MultiplayerCustomGamePayload::from))
        database.reference.child(ROOMS).child(roomId).setValue(MultiplayerRoomCodec.encode(room)).await()
        database.reference.child(CODES).child(code).setValue(roomId).await()
        configurePresence(roomId, uid)
        return room
    }

    suspend fun joinRoom(rawCode: String): String {
        val uid = uid(); val code = RoomCode.normalize(rawCode) ?: throw MultiplayerException.InvalidCode
        val roomId = database.reference.child(CODES).child(code).get().await().getValue(String::class.java) ?: throw MultiplayerException.NotFound
        val ref = database.reference.child(ROOMS).child(roomId)
        val room = room(ref.get().await().value)
        if (room.status !in setOf(MultiplayerRoomStatus.WAITING, MultiplayerRoomStatus.READY)) throw MultiplayerException.Unavailable
        if (room.hostId == uid || room.guestId == uid) { configurePresence(roomId, uid); return roomId }
        if (room.guestId != null) throw MultiplayerException.Full
        val time = now(); val guest = MultiplayerPlayer(uid, "Guest", lastSeenAt = time)
        ref.updateChildren(mapOf("guestId" to uid, "status" to "ready", "updatedAt" to time, "players/$uid" to mapOf("id" to uid, "name" to guest.name, "connected" to true, "lastSeenAt" to time, "score" to 0))).await()
        configurePresence(roomId, uid); return roomId
    }

    fun observe(roomId: String): Flow<Result<MultiplayerRoom>> = callbackFlow {
        val ref = database.reference.child(ROOMS).child(roomId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) { trySend(MultiplayerRoomCodec.decode(snapshot.value)?.let(Result.Companion::success) ?: Result.failure(MultiplayerException.NotFound)) }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) { trySend(Result.failure(error.toException())) }
        }
        ref.addValueEventListener(listener); awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun start(room: MultiplayerRoom, board: Board) {
        requireHost(room); val cards = board.buildCards().map(MultiplayerCard::from)
        update(room.id, room.copy(status = MultiplayerRoomStatus.PLAYING, currentPlayerId = room.hostId, cards = cards, selectedCardIds = emptyList(), winnerId = null, players = room.players.mapValues { it.value.copy(score = 0) }, updatedAt = now()))
    }
    suspend fun choose(roomId: String, cardId: Int) {
        val uid = uid(); val ref = database.reference.child(ROOMS).child(roomId); val current = room(ref.get().await().value)
        val result = MultiplayerTurnReducer.choose(current, uid, cardId, now()) as? MultiplayerTurnReducer.Result.Applied ?: throw MultiplayerException.InvalidMove
        update(roomId, result.room)
    }
    suspend fun clearMismatch(roomId: String, ids: List<Int>) { val ref = database.reference.child(ROOMS).child(roomId); val current = room(ref.get().await().value); update(roomId, MultiplayerTurnReducer.clearMismatch(current, ids, now())) }
    suspend fun leave(room: MultiplayerRoom) { val uid = uid(); val status = if (room.status in setOf(MultiplayerRoomStatus.WAITING, MultiplayerRoomStatus.READY)) MultiplayerRoomStatus.ABANDONED else room.status; update(room.id, room.copy(status = status, players = room.players + (uid to room.players.getValue(uid).copy(connected = false, lastSeenAt = now())), updatedAt = now())) }

    private suspend fun uid(): String = auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid ?: throw MultiplayerException.Authentication
    private suspend fun update(id: String, room: MultiplayerRoom) { database.reference.child(ROOMS).child(id).setValue(MultiplayerRoomCodec.encode(room)).await() }
    private fun room(value: Any?) = MultiplayerRoomCodec.decode(value) ?: throw MultiplayerException.Decoding
    private fun requireHost(room: MultiplayerRoom) { if (room.hostId != auth.currentUser?.uid) throw MultiplayerException.InvalidMove }
    private fun configurePresence(id: String, uid: String) { val ref = database.reference.child(ROOMS).child(id).child("players").child(uid); ref.child("connected").onDisconnect().setValue(false); ref.child("lastSeenAt").onDisconnect().setValue(now()) }
    companion object { private const val ROOMS = "multiplayerRooms"; private const val CODES = "multiplayerRoomCodes" }
}

sealed class MultiplayerException : Exception() { data object Authentication : MultiplayerException(); data object NotFound : MultiplayerException(); data object Full : MultiplayerException(); data object Unavailable : MultiplayerException(); data object InvalidMove : MultiplayerException(); data object InvalidCode : MultiplayerException(); data object Decoding : MultiplayerException() }
