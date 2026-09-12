package app.vaakku.domain.lexicon

import kotlinx.serialization.json.Json

/** Loads `lexicon_ta_en.json` (build plan §5.3) from JSON text or the classpath. */
object LexiconLoader {

    private val json = Json { ignoreUnknownKeys = true }

    fun load(text: String): Lexicon = Lexicon(json.decodeFromString(LexiconData.serializer(), text))

    /** The lexicon bundled at `domain/src/main/resources/lexicon/lexicon_ta_en.json`. */
    fun loadDefault(): Lexicon = loadFromResource("/lexicon/lexicon_ta_en.json")

    fun loadFromResource(
        resourcePath: String,
        classLoader: ClassLoader = LexiconLoader::class.java.classLoader,
    ): Lexicon {
        val stream = classLoader.getResourceAsStream(resourcePath.removePrefix("/"))
            ?: error("Lexicon resource not found on classpath: $resourcePath")
        return stream.use { load(it.readBytes().toString(Charsets.UTF_8)) }
    }
}
