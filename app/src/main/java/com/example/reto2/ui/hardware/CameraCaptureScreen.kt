package com.example.reto2.ui.hardware

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File

// Cámara con permiso en tiempo de ejecución. Entrega la foto como File (almacenamiento interno).
@Composable
fun CameraCaptureScreen(
    onPhotoTaken: (File) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) { if (hasCamera && !granted) launcher.launch(Manifest.permission.CAMERA) }

    when {
        !hasCamera -> CameraMessage("Este dispositivo no tiene cámara", onClose)
        !granted -> CameraMessage(
            text = if (denied) "Sin permiso de cámara. Puedes activarlo en Ajustes." else "Solicitando permiso…",
            onClose = onClose,
            onSettings = if (denied) {
                {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }
            } else null
        )
        else -> CameraPreview(onPhotoTaken, onClose, modifier)
    }
}

@Composable
private fun CameraPreview(
    onPhotoTaken: (File) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val owner = remember { context.findActivity() }
    var error by remember { mutableStateOf<String?>(null) }
    // Calidad 70: las fotos de teléfono real pueden superar el límite de 5 MB del repositorio.
    val imageCapture = remember { ImageCapture.Builder().setJpegQuality(70).build() }
    val previewView = remember { PreviewView(context) }
    val executor = remember { ContextCompat.getMainExecutor(context) }

    // La cámara se une al ciclo de vida de la Activity y se libera al salir.
    DisposableEffect(owner) {
        if (owner == null) return@DisposableEffect onDispose {}
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector =
                    if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA
                    else CameraSelector.DEFAULT_FRONT_CAMERA
                provider.unbindAll()
                provider.bindToLifecycle(owner, selector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("Camara", "No se pudo iniciar", e)
                error = "No se pudo iniciar la cámara"
            }
        }, executor)
        onDispose { future.addListener({ runCatching { future.get().unbindAll() } }, executor) }
    }

    if (owner == null) {
        CameraMessage("No se pudo iniciar la cámara", onClose)
        return
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
            )
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onClose) { Text("Cerrar") }
            Button(onClick = {
                val file = File(context.filesDir, "foto_${System.currentTimeMillis()}.jpg")
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(r: ImageCapture.OutputFileResults) = onPhotoTaken(file)
                        override fun onError(e: ImageCaptureException) {
                            Log.e("Camara", "Error al tomar foto", e)
                            error = "No se pudo tomar la foto"
                        }
                    }
                )
            }) { Text("Tomar foto") }
        }
    }
}

@Composable
private fun CameraMessage(text: String, onClose: () -> Unit, onSettings: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text)
        onSettings?.let { Button(onClick = it) { Text("Abrir ajustes") } }
        OutlinedButton(onClick = onClose) { Text("Cerrar") }
    }
}