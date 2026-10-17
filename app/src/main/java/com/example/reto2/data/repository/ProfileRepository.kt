package com.example.reto2.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.example.reto2.BuildConfig
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.util.UUID

interface ProfileRepository {
    suspend fun photoUrl(userId: String): Result<String?>
    suspend fun uploadPhoto(userId: String, uri: Uri, contentResolver: ContentResolver): Result<String>
}

class CloudinaryProfileRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ProfileRepository {
    override suspend fun photoUrl(userId: String): Result<String?> = runCatching {
        firestore.collection("users").document(userId).get().await().getString("photoUrl")
    }

    override suspend fun uploadPhoto(
        userId: String,
        uri: Uri,
        contentResolver: ContentResolver
    ): Result<String> = runCatching {
        require(BuildConfig.CLOUDINARY_CLOUD_NAME.isNotBlank()) { "Configura CLOUDINARY_CLOUD_NAME en .env" }
        require(BuildConfig.CLOUDINARY_UPLOAD_PRESET.isNotBlank()) { "Configura CLOUDINARY_UPLOAD_PRESET en .env" }
        // Foto de la cámara = archivo JPEG propio; galería = se consulta al sistema.
        val mimeType = (if (uri.scheme == "file") "image/jpeg" else contentResolver.getType(uri))
            ?.takeIf { it.startsWith("image/") }
            ?: error("Selecciona un archivo de imagen")
            ?: error("Selecciona un archivo de imagen")
        val imageBytes = contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_IMAGE_BYTES) { "La imagen debe pesar máximo 5 MB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("No se pudo leer la imagen seleccionada")

        val boundary = "----RetoFx${UUID.randomUUID()}"
        val connection = (URL(
            "https://api.cloudinary.com/v1_1/${BuildConfig.CLOUDINARY_CLOUD_NAME}/image/upload"
        ).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            connection.outputStream.use { output ->
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"upload_preset\"\r\n\r\n${BuildConfig.CLOUDINARY_UPLOAD_PRESET}\r\n".toByteArray())
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"profile\"\r\nContent-Type: $mimeType\r\n\r\n".toByteArray())
                output.write(imageBytes)
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            check(status in 200..299) { "Cloudinary respondió HTTP $status" }
            val secureUrl = JSONObject(response).getString("secure_url")
            firestore.collection("users").document(userId)
                .set(mapOf("photoUrl" to secureUrl), SetOptions.merge()).await()
            secureUrl
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    }
}
