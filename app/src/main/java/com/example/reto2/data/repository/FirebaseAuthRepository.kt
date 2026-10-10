package com.example.reto2.data.repository

import com.example.reto2.data.model.AuthException
import com.example.reto2.data.model.User
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    // Firebase guarda la sesión: sigue activa al reiniciar la app.
    override val currentUser: User?
        get() = auth.currentUser?.toUser()

    override suspend fun login(email: String, password: String) =
        call { auth.signInWithEmailAndPassword(email, password).await().user }

    override suspend fun register(email: String, password: String) =
        call { auth.createUserWithEmailAndPassword(email, password).await().user }

    override fun logout() = auth.signOut()

    private suspend fun call(block: suspend () -> FirebaseUser?): Result<User> =
        try {
            Result.success(block()!!.toUser())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(AuthException(e.toMessage()))
        }

    private fun FirebaseUser.toUser() = User(uid, email.orEmpty())

    private fun Exception.toMessage() = when (this) {
        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException -> "Correo o contraseña incorrectos"
        is FirebaseAuthUserCollisionException -> "Ese correo ya está registrado"
        is FirebaseNetworkException -> "Sin conexión a internet"
        else -> "Ocurrió un error. Inténtalo de nuevo"
    }
}