package dev.pankajkhannade.nexus

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Reads text from a shared screenshot on the phone with ML Kit's bundled Latin and Devanagari models.
 * No image or text leaves the phone. Returns "" when nothing readable is found.
 */
suspend fun readImageText(context: Context, uri: Uri): String {
    val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull() ?: return ""
    val lines = LinkedHashSet<String>()
    for (options in listOf(TextRecognizerOptions.DEFAULT_OPTIONS, DevanagariTextRecognizerOptions.Builder().build())) {
        val client = TextRecognition.getClient(options)
        val text = suspendCancellableCoroutine { cont ->
            client.process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }
        client.close()
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines += it }
    }
    return lines.joinToString("\n")
}

/** Copies a shared image into Nexus's cache so it can be re-shared to WhatsApp through our FileProvider. */
fun copyToCache(context: Context, uri: Uri): File? = runCatching {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "check.jpg")
    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return null
    file
}.getOrNull()
