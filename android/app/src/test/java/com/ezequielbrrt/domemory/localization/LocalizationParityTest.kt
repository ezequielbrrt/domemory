package com.ezequielbrrt.domemory.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the shipped Android string tables against silent English fallback.
 *
 * The resource directories are named explicitly so removing a supported locale fails instead
 * of quietly shrinking the comparison set. Parsing the checked-in resource XML keeps this a
 * fast JVM test; Android's resource compiler is exercised separately by assembleDebug.
 */
class LocalizationParityTest {
    private val resourceDirectories = listOf(
        "values",
        "values-b+es+419",
        "values-pt-rBR",
        "values-de",
        "values-it",
        "values-fr",
        "values-hi",
        "values-ja",
        "values-ko",
        "values-b+zh+Hans",
    )

    private val resourcesRoot = File("src/main/res")

    @Test fun `every supported locale has exactly the base keys`() {
        val base = strings(resourceDirectories.first())
        assertTrue("The base string table must not be empty", base.isNotEmpty())

        resourceDirectories.drop(1).forEach { directory ->
            val localized = strings(directory)
            assertEquals(
                "Locale $directory must define every base key and no extra keys",
                base.keys,
                localized.keys,
            )
        }
    }

    @Test fun `format specifiers match the base locale`() {
        val base = strings(resourceDirectories.first())

        resourceDirectories.drop(1).forEach { directory ->
            val localized = strings(directory)
            base.forEach { (key, baseValue) ->
                assertEquals(
                    "Locale $directory key $key has incompatible format specifiers: ${localized.getValue(key)}",
                    conversionCharacters(baseValue),
                    conversionCharacters(localized.getValue(key)),
                )
            }
        }
    }

    private fun strings(directory: String): Map<String, String> {
        val file = resourcesRoot.resolve("$directory/strings.xml")
        assertTrue("Missing string table: ${file.path}", file.isFile)

        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        val entries = buildList {
            repeat(nodes.length) { index ->
                val element = nodes.item(index) as Element
                add(element.getAttribute("name") to element.textContent)
            }
        }
        val table = entries.toMap()
        assertEquals("Duplicate string key in ${file.path}", entries.size, table.size)
        return table
    }

    private fun conversionCharacters(value: String): List<String> =
        FORMAT_SPECIFIER.findAll(value).map { it.groupValues[1] }.sorted().toList()

    private companion object {
        val FORMAT_SPECIFIER = Regex("%(?:\\d+\\$)?([a-zA-Z])")
    }
}
