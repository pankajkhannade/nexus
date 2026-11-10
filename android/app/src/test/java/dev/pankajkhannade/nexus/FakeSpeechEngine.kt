package dev.pankajkhannade.nexus

/**
 * A recogniser for tests: each [start] opens a session, and the test plays what Android would send
 * (partial words, end of speech, final words or an error). Also a fake clock for the time limits.
 */
class FakeSpeechEngine(private val isAvailable: Boolean = true) : SpeechEngine {
    val requests = mutableListOf<SpeechRequest>()
    var resets = 0
        private set
    private var current: SpeechCallbacks? = null

    /** Milliseconds on the fake clock. Pass `{ fake.clock }` to the listener. */
    var clock = 1_000_000L

    val starts: Int get() = requests.size
    val listening: Boolean get() = current != null

    override fun available() = isAvailable
    override fun checkMode(language: String, onResult: (VoiceMode) -> Unit) =
        onResult(if (isAvailable) VoiceMode.ONLINE else VoiceMode.UNAVAILABLE)

    override fun start(request: SpeechRequest, callbacks: SpeechCallbacks) {
        requests += request
        current = callbacks
    }

    override fun reset() {
        resets++
        current = null
    }

    private fun session(): SpeechCallbacks = checkNotNull(current) { "no recogniser session is running" }

    fun partial(text: String) = session().onPartial(text)
    fun endOfSpeech() = session().onEndOfSpeech()

    /** Ends the session with final words (several = the recogniser's n-best, best first). */
    fun final(vararg texts: String) {
        val s = session(); current = null; s.onFinal(texts.toList())
    }

    /** Ends the session with a SpeechRecognizer.ERROR_* code. */
    fun error(code: Int) {
        val s = session(); current = null; s.onError(code)
    }

    fun advance(ms: Long) { clock += ms }
}
