package dev.pankajkhannade.nexus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.UUID

/**
 * Faces you can tell apart (UX sprint 1, fix #1). A parent picks a person by face and colour before
 * reading the name, so two people never share a look: a photo when the child added one (or the phone
 * contact has one), else one of 8 fixed colours by position in the family list, with the initial.
 * Every colour carries white text at 4.5:1 or better (FaceColorsTest checks the maths).
 */
val FACE_COLORS: List<Color> = listOf(
    Color(0xFF0B5CAD), // blue
    Color(0xFFAD1457), // magenta
    Color(0xFF00695C), // teal
    Color(0xFF6A1B9A), // purple
    Color(0xFFBF360C), // deep orange
    Color(0xFF283593), // indigo
    Color(0xFF5D4037), // brown
    Color(0xFF33691E), // olive green
)

/**
 * The colour slot for [person]: their position among [family] (so up to 8 people always differ), else a
 * stable hash of the id for someone not saved (a phone-book match), offset by [fallbackIndex].
 */
fun faceColorIndex(person: Person, family: List<Person>, fallbackIndex: Int = 0): Int {
    val at = family.indexOfFirst { it.id == person.id }
    if (at >= 0) return at % FACE_COLORS.size
    return Math.floorMod(person.id.hashCode() + fallbackIndex, FACE_COLORS.size)
}

/** WCAG relative-luminance contrast between two colours. */
fun contrast(a: Color, b: Color): Double {
    fun lum(c: Color): Double {
        fun ch(v: Float): Double = if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }
    val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
    return (hi + 0.05) / (lo + 0.05)
}

/** Test hook: which colour slot (or "photo") an avatar shows. */
val FaceKey = SemanticsPropertyKey<String>("NexusFace")
private var SemanticsPropertyReceiver.face by FaceKey

/**
 * A round face: the photo, else the initial on the person's colour. Decorative for TalkBack (the name is
 * always written next to it); tests find it by the tag "face:<name>" and read [FaceKey].
 */
@Composable
fun Avatar(person: Person, family: List<Person>, size: Dp = 64.dp, fallbackIndex: Int = 0) {
    val photo = remember(person.photo) { loadFace(person.photo) }
    val slot = faceColorIndex(person, family, fallbackIndex)
    val key = if (photo != null) "photo" else "color$slot"
    val base = Modifier
        .size(size)
        .testTag("face:${person.name}")
        .clearAndSetSemantics { face = key }
    if (photo != null) {
        Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = base.clip(CircleShape))
        return
    }
    val initial = initialOf(shownName(person))
    Box(base.background(FACE_COLORS[slot], CircleShape), contentAlignment = Alignment.Center) {
        // Sized to the circle, not the text-size setting: at 2.0x a scaled letter overflowed the 48dp face.
        val letter = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 0.42f).toSp() }
        Text(
            initial, color = Color.White, fontSize = letter, lineHeight = letter * 1.3f,
            fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
        )
    }
}

/** Avatar for a name with no person (rare: setup's Try it before people load). */
@Composable
fun Avatar(name: String, size: Dp = 64.dp) = Avatar(Person(id = name, name = name, phone = ""), emptyList(), size)

private fun loadFace(path: String): Bitmap? {
    if (path.isBlank()) return null
    val file = File(path)
    if (!file.exists()) return null
    return runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
}

/** faces/ in the app's private files. Deleted with the app; never in backups or uploads. */
fun facesDir(context: Context): File = File(context.filesDir, "faces").apply { mkdirs() }

/**
 * Copies a picked image (photo picker, or a contact's photo) into faces/ as a small square JPEG,
 * so the original can be deleted and nothing outside Nexus is needed later. Returns the path or null.
 */
fun saveFace(context: Context, source: Uri): String? = runCatching {
    val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() } ?: return null
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val side = minOf(decoded.width, decoded.height)
    val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
    val small = Bitmap.createScaledBitmap(square, 256, 256, true)
    val out = File(facesDir(context), UUID.randomUUID().toString() + ".jpg")
    out.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    out.path
}.onFailure { Log.i("NexusFace", "face copy failed: ${it.javaClass.simpleName}") }.getOrNull()

/** The photo of a contact row returned by the system picker, when the phone has one and we may read it. */
fun contactPhotoUri(context: Context, pickedRow: Uri): Uri? = runCatching {
    context.contentResolver.query(pickedRow, arrayOf(ContactsContract.CommonDataKinds.Phone.PHOTO_URI), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0)?.let(Uri::parse) else null
    }
}.getOrNull()


/**
 * The person's name in the screen's language (UX sprint 2): the Devanagari name the child typed when the
 * app is in Marathi or Hindi, else the saved name. Latin names are never changed.
 */
@Composable
fun shownName(person: Person): String =
    person.shownIn(androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language)

/** The first letter as a reader sees it: a whole Devanagari cluster ("श्री" -> "श्री"), not a lone consonant. */
fun initialOf(name: String): String {
    val text = name.trim()
    if (text.isEmpty()) return "?"
    val it = java.text.BreakIterator.getCharacterInstance()
    it.setText(text)
    var end = it.next()
    // A consonant + virama joins the next letter into one cluster (क्ष, श्र): keep the cluster whole.
    while (end in 1 until text.length && text[end - 1] == '\u094D') end = it.next()
    return text.substring(0, end.coerceAtMost(text.length)).uppercase()
}
