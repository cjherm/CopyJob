package com.akbigchris.copyjob

import javax.xml.parsers.DocumentBuilderFactory

/**
 * User-facing UI text, loaded from the bundled texts.xml (src/main/resources/texts.xml) so the
 * app can be translated without touching code. The app name "CopyJob" is never looked up here —
 * it stays untranslated everywhere it appears.
 */
object Texts {
    private val texts: Map<String, String> by lazy { load() }

    operator fun get(id: String): String = texts[id] ?: id

    /** Formats the text at [id] with [args], using positional specifiers (`%1$s`, `%2$d`, ...). */
    fun get(id: String, vararg args: Any?): String = String.format(this[id], *args)

    private fun load(): Map<String, String> {
        val stream = Texts::class.java.classLoader.getResourceAsStream("texts.xml")
            ?: return emptyMap()
        val document = stream.use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
        }
        val nodes = document.getElementsByTagName("text")
        return buildMap {
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val id = node.attributes?.getNamedItem("id")?.nodeValue ?: continue
                put(id, node.textContent.trim())
            }
        }
    }
}
