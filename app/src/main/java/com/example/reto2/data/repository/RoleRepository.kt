package com.example.reto2.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface RoleRepository {
    suspend fun isAdmin(userId: String): Result<Boolean>
}

class FirestoreRoleRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : RoleRepository {
    override suspend fun isAdmin(userId: String): Result<Boolean> = runCatching {
        firestore.collection("admins").document(userId).get().await().exists()
    }
}
