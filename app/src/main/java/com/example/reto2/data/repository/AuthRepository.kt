package com.example.reto2.data.repository

import com.example.reto2.data.model.User

interface AuthRepository {
    val currentUser: User?
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(email: String, password: String): Result<User>
    fun logout()
}