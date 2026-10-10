package com.example.reto2.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reto2.data.model.User
import com.example.reto2.data.repository.AuthRepository
import com.example.reto2.data.repository.FirebaseAuthRepository
import com.example.reto2.data.repository.FirestoreRoleRepository
import com.example.reto2.data.repository.RoleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val user: User? = null,
    val isAdmin: Boolean = false,
    val roleLoading: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(
    private val repository: AuthRepository = FirebaseAuthRepository(),
    private val roleRepository: RoleRepository = FirestoreRoleRepository()
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState(user = repository.currentUser))
    val state = _state.asStateFlow()

    init {
        repository.currentUser?.let { loadRole(it.uid) }
    }

    fun emailChanged(value: String) = _state.update { it.copy(email = value, error = null) }
    fun passwordChanged(value: String) = _state.update { it.copy(password = value, error = null) }
    fun confirmPasswordChanged(value: String) = _state.update { it.copy(confirmPassword = value, error = null) }

    fun googleSignIn(idToken: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repository.googleLogin(idToken).fold(
                onSuccess = { user ->
                    _state.update { it.copy(user = user, loading = false, roleLoading = true) }
                    loadRole(user.uid)
                },
                onFailure = { error -> _state.update { it.copy(error = error.message, loading = false) } }
            )
        }
    }

    fun googleSignInFailed(message: String) = _state.update { it.copy(error = message, loading = false) }

    fun googleSignInStarted() = _state.update { it.copy(error = null, loading = true) }

    fun submit(register: Boolean) {
        val current = _state.value
        if (current.email.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "Ingresa tu correo y contraseña") }
            return
        }
        if (register && current.password.length < 6) {
            _state.update { it.copy(error = "La contraseña debe tener al menos 6 caracteres") }
            return
        }
        if (register && current.password != current.confirmPassword) {
            _state.update { it.copy(error = "Las contraseñas no coinciden") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = if (register) repository.register(current.email.trim(), current.password)
                else repository.login(current.email.trim(), current.password)
            result.fold(
                onSuccess = { user ->
                    _state.update { it.copy(user = user, loading = false, roleLoading = true) }
                    loadRole(user.uid)
                },
                onFailure = { error -> _state.update { it.copy(error = error.message, loading = false) } }
            )
        }
    }

    fun logout() {
        repository.logout()
        _state.update { AuthUiState() }
    }

    private fun loadRole(userId: String) {
        viewModelScope.launch {
            val result = roleRepository.isAdmin(userId)
            _state.update {
                if (it.user?.uid == userId) it.copy(isAdmin = result.getOrDefault(false), roleLoading = false)
                else it
            }
        }
    }
}
