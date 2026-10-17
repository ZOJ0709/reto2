package com.example.reto2.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.reto2.ui.hardware.CameraCaptureScreen
import java.io.File

@Composable
fun ProfileScreen(
    email: String,
    state: ProfileUiState,
    onChoosePhoto: () -> Unit,
    onBack: () -> Unit,
    onPhotoCaptured: (File) -> Unit = {}
) {
    var showCamera by remember { mutableStateOf(false) }

    // "Atrás" cierra la cámara y no la pantalla de perfil.
    BackHandler(enabled = showCamera) { showCamera = false }

    if (showCamera) {
        CameraCaptureScreen(
            onPhotoTaken = { file ->
                showCamera = false
                onPhotoCaptured(file)
            },
            onClose = { showCamera = false }
        )
    } else {
        ProfileContent(email, state, onChoosePhoto, { showCamera = true }, onBack)
    }
}

@Composable
private fun ProfileContent(
    email: String,
    state: ProfileUiState,
    onChoosePhoto: () -> Unit,
    onOpenCamera: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Mi perfil", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        if (!state.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = state.photoUrl,
                contentDescription = "Foto de perfil",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(144.dp).clip(CircleShape)
            )
        } else {
            Text("Sin foto de perfil", color = MaterialTheme.colorScheme.tertiary)
        }
        Spacer(Modifier.height(20.dp))
        Text(email, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        Text("Balance simulado en USD", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.tertiary)
        Text("\$ ${state.usdBalance}", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onChoosePhoto, enabled = !state.uploading) {
            if (state.uploading) CircularProgressIndicator()
            else Text(if (state.photoUrl == null) "Agregar foto" else "Cambiar foto")
        }
        OutlinedButton(onClick = onOpenCamera, enabled = !state.uploading) {
            Text("Tomar foto con la cámara")
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onBack) { Text("Volver a operaciones") }
    }
}