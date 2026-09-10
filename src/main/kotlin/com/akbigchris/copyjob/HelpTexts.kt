package com.akbigchris.copyjob

import javax.xml.parsers.DocumentBuilderFactory

/**
 * Hover help text for buttons/icons, loaded from the bundled help-texts.xml
 * (src/main/resources/help-texts.xml) so text can be edited without touching code.
 */
object HelpTexts {
    private val texts: Map<String, String> by lazy { load() }

    operator fun get(id: String): String = texts[id] ?: id

    private fun load(): Map<String, String> {
        val stream = HelpTexts::class.java.classLoader.getResourceAsStream("help-texts.xml")
            ?: return emptyMap()
        val document = stream.use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
        }
        val nodes = document.getElementsByTagName("helpText")
        return buildMap {
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val id = node.attributes?.getNamedItem("id")?.nodeValue ?: continue
                put(id, node.textContent.trim())
            }
        }
    }
}
