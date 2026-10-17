package com.example.reto2.ui.profile

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reto2.data.repository.CloudinaryProfileRepository
import com.example.reto2.data.repository.FinanceRepository
import com.example.reto2.data.repository.FinnhubFinanceRepository
import com.example.reto2.data.repository.ProfileRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val photoUrl: String? = null,
    val uploading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val usdBalance: String = "0"
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ProfileRepository = CloudinaryProfileRepository()
    private val financeRepository: FinanceRepository = FinnhubFinanceRepository()
    private val auth = FirebaseAuth.getInstance()
    private val resolver: ContentResolver = application.contentResolver
    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()

    init {
        auth.currentUser?.uid?.let { userId ->
            viewModelScope.launch {
                repository.photoUrl(userId).onSuccess { url -> _state.update { it.copy(photoUrl = url) } }
                financeRepository.userBalance(userId).onSuccess { balance ->
                    _state.update { it.copy(usdBalance = balance.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()) }
                }.onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "No se pudo cargar el balance") }
                }
            }
        }
    }

    fun upload(uri: Uri) {
        val userId = auth.currentUser?.uid ?: run {
            _state.update { it.copy(error = "Inicia sesión para actualizar tu perfil") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null, message = null) }
            val result = withContext(Dispatchers.IO) {
                repository.uploadPhoto(userId, uri, resolver)
            }
            result.fold(
                onSuccess = { url -> _state.update { it.copy(photoUrl = url, uploading = false, message = "Foto de perfil actualizada") } },
                onFailure = { e -> _state.update { it.copy(uploading = false, error = e.message ?: "No se pudo subir la foto") } }
            )
        }
    }
    // Foto tomada con la cámara: se sube igual que una de la galería.
    fun uploadFile(file: java.io.File) = upload(Uri.fromFile(file))

}
