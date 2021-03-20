//@file:DependsOn("")

import org.w3c.dom.DOMException
import java.io.File
import kotlin.system.exitProcess
import kotlin.collections.AbstractMap
import java.util.AbstractMap.SimpleEntry
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.w3c.dom.NamedNodeMap
import javax.xml.xpath.*

val DefaultColorScheme = "resources/META-INF/Prplkai.xml"
val AttribKeyLen = 50

// Run script with no arguments to display all attributes for the DefaultColorScheme,
// or with a single argument pointing to another color scheme to display the difference
// between it and DefaultColorScheme
fun main(args: Array<String>) {

    val currentColorScheme = parseColorScheme(DefaultColorScheme)

    if (args.size > 0) {
        println("Comparing ${cyan(DefaultColorScheme)} with ${cyan(args[0])}...")

        val altColorScheme = parseColorScheme(args[0])
        val altAttribs = altColorScheme.attributes
        val currAttribs = currentColorScheme.attributes

        val allAttribs = (altAttribs.keys + currAttribs.keys).map { Triple(it, altAttribs[it], currAttribs[it]) }
        val changed = allAttribs.filter { (_, alt, curr) -> alt != null && curr != null && !alt.equals(curr) }
        val removed = allAttribs.filter { (_, alt, curr) -> curr != null && alt == null }
        val added = allAttribs.filter { (_, alt, curr) -> curr == null && alt != null }

        if (changed.size > 0) {
            println(yellow("Changed:"))
            for ((key, alt, curr) in changed) {
                println(key)
                println("  " + formatAttribMap("Current", curr))
                println("  " + formatAttribMap("Alternate", alt))
                println()
            }
            println()
        }

        if (removed.size > 0) {
            println(red("Removed:"))
            for ((key, _, curr) in removed) {
                println(formatAttribMap(key, curr))
            }
            println()
        }

        if (added.size > 0) {
            println(green("Added:"))
            for ((key, alt, _) in added) {
                println(formatAttribMap(key, alt))
            }
            println()
        }

        if ((changed.size + added.size + removed.size) < 1) println("Color schemes are equal!")
    } else {
        println("Displaying colors in ${cyan(DefaultColorScheme)}:\n")
        for ((name, color) in currentColorScheme.colors) {
            println(formatColor(name, color))
        }
        println()
        println("Displaying attributes in ${cyan(DefaultColorScheme)}:\n")
        for ((name, kv) in currentColorScheme.attributes) {
            println(formatAttribMap(name, kv))
        }
    }
}

fun formatAttribMap(name: String, kv: Map<String, String>?): String {
    val paddedName = name.plus(":").padEnd(AttribKeyLen)

    if (kv == null) return "${paddedName} ${red("<null>")}"
    if ("inherit" in kv) return "${paddedName} => ${cyan(kv["inherit"] ?: "")}\u001B[0m"

    val pre = "${rgb(kv["foreground"])}${rgb(kv["background"], true)}"
    val stripe = kv["error_stripe_color"]?.let { "\n${rgb(it)}${"¯".repeat(name.length)}\u001B[0m" } ?: ""
    return "${pre}${paddedName} ${kv.map { "${it.key}=${it.value}" }.joinToString()}\u001B[0m${stripe}"
}

fun formatColor(name: String, color: String): String {
    return "${name.plus(":").padEnd(AttribKeyLen)} " +
            if (color.isEmpty()) "" else "${rgb(color, true)}  \u001B[0m #${color}"
}

// Console colors! 🎉
fun cyan(s: String) = "\u001B[1;36m${s}\u001B[0m"
fun yellow(s: String) = "\u001B[1;33m${s}\u001B[0m"
fun green(s: String) = "\u001B[1;32m${s}\u001B[0m"
fun red(s: String) = "\u001B[1;31m${s}\u001B[0m"
fun rgb(hexColor: String?, background: Boolean = false): String =
        hexColor?.take(6)?.toIntOrNull(16)?.let {
            "\u001B[${if (background) 4 else 3}8;2;${it shr 16 and 0xff};${it shr 8 and 0xff};${it and 0xff}m"
        } ?: ""

// XPath Kotlin helpers
class XNodeIterator(val nodeList: NodeList) : Iterator<XNode> {
    var position = 0
    override fun hasNext(): Boolean = nodeList.length > position
    override fun next(): XNode = XNode(nodeList.item(position++) ?: throw NoSuchElementException())
}

class XAttribMap(var namedNodeMap: NamedNodeMap) : AbstractMap<String, String>(), NamedNodeMap by namedNodeMap {
    override val entries: Set<Map.Entry<String, String>> =
            List(namedNodeMap.length, { namedNodeMap.item(it) }).map { SimpleEntry(it.nodeName, it.nodeValue) }.toSet()
}

object emptyNamedNodeMap : NamedNodeMap {
    override fun getNamedItem(name: String?) = null
    override fun setNamedItem(arg: Node?) = throw DOMException(DOMException.NO_MODIFICATION_ALLOWED_ERR, "")
    override fun removeNamedItem(name: String?) = throw DOMException(DOMException.NOT_FOUND_ERR, "")
    override fun item(index: Int) = null
    override fun getLength(): Int = 0
    override fun getNamedItemNS(namespaceURI: String?, localName: String?) = null
    override fun setNamedItemNS(arg: Node?) = throw DOMException(DOMException.NO_MODIFICATION_ALLOWED_ERR, "")
    override fun removeNamedItemNS(namespaceURI: String?, localName: String?) = throw DOMException(DOMException.NOT_FOUND_ERR, "")
}

class XNode(val node: Node) : Iterable<XNode>, Node by node {
    var attribMap = XAttribMap(node.attributes ?: emptyNamedNodeMap)
    override fun iterator(): Iterator<XNode> = XNodeIterator(node.childNodes)
}

class XNodeList(val nodeList: NodeList) : Iterable<XNode> {
    override fun iterator(): Iterator<XNode> = XNodeIterator(nodeList)
}


// Color scheme record
typealias ColorSchemeAttribs = Map<String, Map<String, String>>

class ColorScheme(var attributes: ColorSchemeAttribs, var colors: Map<String, String>)

fun parseColorScheme(path: String): ColorScheme {
    val colorSchemeFile = File(path)
    if (!colorSchemeFile.exists()) {
        println("Scheme file \"${colorSchemeFile.absolutePath}\" does not exist")
        exitProcess(1)
    }

    val dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val doc = dBuilder.parse(colorSchemeFile)
    val xPath = XPathFactory.newInstance().newXPath()

    val attribOpts = XNodeIterator(xPath.evaluate("/scheme/attributes/option", doc, XPathConstants.NODESET) as NodeList)
    val attributes = attribOpts.asSequence().associate { option ->
        (option.attribMap["name"] ?: "") to (option.attribMap["baseAttributes"]?.let { mapOf("inherit" to it) }
                ?: option.firstOrNull {
                    it.nodeName.equals("value")
                }?.filter {
                    it.nodeType == Node.ELEMENT_NODE
                }?.associate {
                    (it.attribMap["name"] ?: "").toLowerCase() to (it.attribMap["value"] ?: "")
                } ?: emptyMap<String, String>())
    }

    val colorOpts = XNodeIterator(xPath.evaluate("/scheme/colors/option", doc, XPathConstants.NODESET) as NodeList)
    val colors = colorOpts.asSequence().associate { it.attribMap.let { a -> (a["name"] ?: "") to (a["value"] ?: "") } }

    return ColorScheme(attributes, colors)
}

// Script wrapper
main(args)