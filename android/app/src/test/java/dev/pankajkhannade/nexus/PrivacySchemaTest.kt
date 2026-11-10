package dev.pankajkhannade.nexus

import dev.pankajkhannade.nexus.ui.newModel
import dev.pankajkhannade.nexus.ui.fakeInstall
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset

/** No personal data can be logged or uploaded: the schema has no text, and real flows leave none behind. */
@RunWith(RobolectricTestRunner::class)
class PrivacySchemaTest {
    private val allowed = setOf(Long::class.javaPrimitiveType, Long::class.javaObjectType, Int::class.javaObjectType, Boolean::class.javaObjectType)

    @Test fun usageEventHasNoFreeTextField() {
        val fields = UsageEvent::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertTrue(fields.isNotEmpty())
        fields.forEach { f ->
            val ok = f.type.isEnum || f.type in allowed
            assertTrue("UsageEvent.${f.name} is ${f.type}: only enums, numbers and booleans may be logged", ok)
            assertFalse(CharSequence::class.java.isAssignableFrom(f.type))
        }
    }

    @Test fun storedKeysAreTheFixedSet() {
        val e = UsageEvent(1, EventType.COMMAND, Lang.MR, CommandType.CALL, Source.VOICE, EndReason.COMPLETE, Perm.MIC, AppCategory.DIALER, true, 1, 2, 3, 4)
        assertEquals(setOf("t", "e", "lang", "cmd", "src", "end", "perm", "cat", "ok", "ms", "chars", "code", "step"), JSONObject(e.toJson()).keys().asSequence().toSet())
    }

    @Test fun aRealVoiceFlowLeavesNoNamesNumbersWordsOrPlacesInTheLog() {
        fakeInstall("com.whatsapp")
        val model = newModel(people = 1, home = "Shivaji Nagar, Pune 411005")
        val fake = FakeSpeechEngine()
        model.useSpeechEngine(fake) { fake.clock }
        model.startListening()
        fake.partial("पंकजला व्हॉट्सॲप कर आणि सांग की साबण घेऊन ये")
        fake.final("पंकजला व्हॉट्सॲप कर आणि सांग की साबण घेऊन ये")
        model.doneListening()
        dev.pankajkhannade.nexus.ui.idleMain()
        model.sendDraft(Channel.WHATSAPP)
        model.understood("take me to Pune airport", typed = true)
        model.tile(CommandType.CALL)
        model.cameBack()

        val dir = EventLog.dirFor(model.getApplication())
        val disk = java.io.File(dir, "events.jsonl").takeIf { it.exists() }?.readText().orEmpty()
        val all = disk + model.events.all().joinToString("\n") { it.toJson() } + model.summary() +
            uploadPayload(model.events.all(), model.events.all().map { dayOf(it.t, ZoneOffset.UTC) }.distinct(), "00000000-0000-4000-8000-000000000000", TesterSource.FAMILY, "1.0.0", "mr", ZoneOffset.UTC)
        assertTrue("the flow should have logged something", model.events.all().size >= 4)
        listOf("Pankaj", "पंकज", "साबण", "98765", "+91", "Shivaji", "411005", "airport", "Pune").forEach {
            assertFalse("'$it' leaked into analytics", all.contains(it))
        }
    }

    @Test fun uploadPayloadHasOnlyFixedFieldsAndIntegerCounts() {
        val day = LocalDate.of(2026, 11, 2)
        val t = day.toEpochDay() * 86_400_000L + 1000
        val events = listOf(UsageEvent(t, EventType.APP_OPEN), UsageEvent(t + 1, EventType.COMMAND, lang = Lang.MR, command = CommandType.CALL, source = Source.VOICE, ok = true))
        val p = uploadPayload(events, listOf(day), "11111111-1111-4111-8111-111111111111", TesterSource.STRANGER, "1.0.0", "mr", ZoneOffset.UTC)
        assertEquals(setOf("v", "install_id", "tester_source", "app_version", "lang", "days"), p.keys().asSequence().toSet())
        val d = p.getJSONArray("days").getJSONObject(0)
        assertEquals(setOf("day", "counts"), d.keys().asSequence().toSet())
        val counts = d.getJSONObject("counts")
        counts.keys().forEach { k ->
            assertTrue(k, COUNT_KEY.matches(k))
            assertTrue(k, counts.get(k) is Int)
        }
    }
}
