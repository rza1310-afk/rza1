package com.example.data

import android.content.Context
import com.example.R
import com.example.model.EdexUserPreferences
import com.example.model.HudProfile
import com.example.model.MacroDeck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreHudSyncRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  fun observeUserHudState(userId: String): Flow<CloudUserHudState?> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Signed-in user mismatch; refusing cross-user listener." }
    val path = "users/$userId"
    emitAll(
      db.collection("users")
        .document(userId)
        .snapshots()
        .map { snapshot ->
          if (!snapshot.exists()) null
          else snapshot.toObject(CloudUserHudState::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          logAndRethrowStreamError(error, OperationType.GET, path, uid)
        }
    )
  }

  fun observeHudProfiles(userId: String): Flow<List<CloudHudProfile>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Signed-in user mismatch; refusing cross-user listener." }
    val path = "users/$userId/hud_profiles"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("hud_profiles")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            CloudHudProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logAndRethrowStreamError(error, OperationType.LIST, path, uid)
        }
    )
  }

  fun observeMacroDecks(userId: String): Flow<List<CloudMacroDeck>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Signed-in user mismatch; refusing cross-user listener." }
    val path = "users/$userId/macro_decks"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("macro_decks")
        .orderBy("sortOrder")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            CloudMacroDeck::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logAndRethrowStreamError(error, OperationType.LIST, path, uid)
        }
    )
  }

  private fun logAndRethrowStreamError(
    error: Throwable,
    op: OperationType,
    path: String,
    expectedUid: String,
  ): Nothing {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
      handleFirestoreError(exceptionToLog, op, path)
    }
    throw error
  }

  suspend fun ensureUserSeeded(
    displayName: String,
    initialPrefs: EdexUserPreferences,
  ): Result<Unit> {
    var path = "users/unauthenticated"
    return try {
      val uid = requireUserId()
      val userDoc = db.collection("users").document(uid)
      path = userDoc.path
      val existingUser = userDoc.get().await()
      if (!existingUser.exists()) {
        userDoc.set(buildUserHudCreateMap(uid, displayName, initialPrefs)).await()
      }

      val profilesCol = userDoc.collection("hud_profiles")
      val existingProfiles = profilesCol.get().await()
      if (existingProfiles.isEmpty) {
        initialPrefs.hudProfiles.forEach { profile ->
          val docId = sanitizeFirestoreId(profile.id, "prof")
          path = "$path/hud_profiles/$docId"
          profilesCol.document(docId).set(profile.toFirestoreCreateMap(uid)).await()
        }
      }

      val decksCol = userDoc.collection("macro_decks")
      val existingDecks = decksCol.get().await()
      if (existingDecks.isEmpty) {
        initialPrefs.macroDecks.forEachIndexed { idx, deck ->
          val docId = sanitizeFirestoreId(deck.id, "deck")
          path = "users/$uid/macro_decks/$docId"
          decksCol.document(docId).set(deck.toFirestoreCreateMap(uid, idx)).await()
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun syncPreferencesUpdate(
    prefs: EdexUserPreferences,
    clearActiveProfile: Boolean = false,
  ): Result<Unit> {
    var path = "users/unauthenticated"
    return try {
      val uid = requireUserId()
      val userDoc = db.collection("users").document(uid)
      path = userDoc.path
      val updates =
        mutableMapOf<String, Any>(
          "themeId" to prefs.themeId.name,
          "telemetryThemeId" to prefs.telemetryThemeId.name,
          "soundEnabled" to prefs.soundEnabled,
          "scanlinesEnabled" to prefs.scanlinesEnabled,
          "showKeyboard" to prefs.showKeyboard,
          "glitchShaderEnabled" to prefs.glitchShaderEnabled,
          "glitchLoadThreshold" to prefs.glitchLoadThreshold.coerceIn(20f, 95f).toDouble(),
          "activeMacroDeckId" to prefs.activeMacroDeckId.take(64).ifBlank { "recon" },
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (clearActiveProfile) {
        updates["activeHudProfileId"] = FieldValue.delete()
      } else if (!prefs.activeHudProfileId.isNullOrBlank()) {
        updates["activeHudProfileId"] = prefs.activeHudProfileId.take(64)
      }
      userDoc.update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun saveCustomHudProfile(profile: HudProfile): Result<Unit> {
    var path = "users/unauthenticated/hud_profiles"
    return try {
      val uid = requireUserId()
      val safeId = sanitizeFirestoreId(profile.id, "prof")
      require(profile.name.trim().isNotEmpty()) { "Profile name must not be blank" }
      val docRef = db.collection("users").document(uid).collection("hud_profiles").document(safeId)
      path = docRef.path
      docRef.set(profile.toFirestoreCreateMap(uid)).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun deleteCustomHudProfile(profileId: String): Result<Unit> {
    var path = "users/unauthenticated/hud_profiles/$profileId"
    return try {
      val uid = requireUserId()
      val safeId = sanitizeFirestoreId(profileId, "prof")
      val docRef = db.collection("users").document(uid).collection("hud_profiles").document(safeId)
      path = docRef.path
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun saveMacroDeck(deck: MacroDeck, sortOrder: Int): Result<Unit> {
    var path = "users/unauthenticated/macro_decks"
    return try {
      val uid = requireUserId()
      val safeId = sanitizeFirestoreId(deck.id, "deck")
      require(deck.name.trim().isNotEmpty()) { "Deck name must not be blank" }
      val docRef = db.collection("users").document(uid).collection("macro_decks").document(safeId)
      path = docRef.path
      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef.update(
          mapOf(
            "name" to deck.name.trim().take(40),
            "sortOrder" to sortOrder.coerceIn(0, 100),
            "macros" to deck.macros.take(30).map { it.toFirestoreMap() },
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        docRef.set(deck.toFirestoreCreateMap(uid, sortOrder)).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }
}
