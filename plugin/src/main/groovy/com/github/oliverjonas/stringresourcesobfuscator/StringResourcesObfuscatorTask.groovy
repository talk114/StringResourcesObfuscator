package com.github.oliverjonas.stringresourcesobfuscator

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element
import org.w3c.dom.traversal.NodeFilter

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class StringResourcesObfuscatorTask extends DefaultTask {

    static class ValuesDirectoryFilter implements FilenameFilter {
        public boolean accept(File f, String filename) {
            return filename.startsWith("values")
        }
    }

    private static final int MAX = '\u07FE' as char
    private static final String TOOLS_NAMESPACE = "http://schemas.android.com/tools"

    private Random random
    private boolean quoting
    private boolean skipping

    @TaskAction
    def stringResourcesObfuscator() {

        println "Obfuscating string resources with seed: " + project.stringResourcesObfuscatorSettings.seed

        def manifestFile = new File("${project.name}/src/${project.stringResourcesObfuscatorSettings.sourceBuildType}/AndroidManifest.xml")
        def excludedStrings = parseManifest(manifestFile)
        println "Excluded strings: " + excludedStrings

        def sourceResDir = new File("${project.name}/src/${project.stringResourcesObfuscatorSettings.sourceBuildType}/res")
        def targetResDir = new File("${project.name}/src/${project.stringResourcesObfuscatorSettings.targetBuildType}/res")

        def list = project.stringResourcesObfuscatorSettings.files
        sourceResDir.listFiles(new ValuesDirectoryFilter()).each { dir ->
            list.each { file ->
                File sourceFile = new File(dir, file)
                if (sourceFile.exists()) {
                    def targetFile = new File(new File(targetResDir, dir.getName()), file)
                    println "Obfuscating: ${sourceFile} -> ${targetFile}"
                    processFile(sourceFile, targetFile, excludedStrings)
                }
            }
        }
    }

    def static parseManifest(File manifestFile) {
        println "Parsing manifest: ${manifestFile}"

        def excludedStrings = new LinkedHashSet<String>()

        def docFactory = DocumentBuilderFactory.newInstance()
        docFactory.setNamespaceAware(true)
        def docBuilder = docFactory.newDocumentBuilder()
        def doc = docBuilder.parse(manifestFile)

        def walker = doc.createTreeWalker(
                doc.getDocumentElement(),
                NodeFilter.SHOW_ELEMENT, null, false)

        def node
        while ((node = walker.nextNode()) != null) {
            def list = node.getAttributes()
            if (list != null) {
                for (def n = 0; n < list.getLength(); n++) {
                    def attr = list.item(n)
                    def value = attr.getValue()
                    if (value != null && value.startsWith("@string/")) {
                        excludedStrings.add(value as String)
                    }
                }
            }
        }

        return excludedStrings
    }

    def processFile(File source, File target, excludedStrings) {

        target.getParentFile().mkdirs()

        def docFactory = DocumentBuilderFactory.newInstance()
        docFactory.setNamespaceAware(true)
        def docBuilder = docFactory.newDocumentBuilder()
        def doc = docBuilder.parse(source)

        getElements(doc, "string").each { string ->
            if (!excludedStrings.contains("@string/" + string.getAttribute("name"))) {
                obfuscateElement(string)
            } else {
                string.getParentNode().removeChild(string)
            }
        }

        getElements(doc, "string-array").each { stringArray ->
            stringArray.getElementsByTagName("item").each { item ->
                obfuscateElement(item)
            }
        }

        getElements(doc, "plurals").each { stringArray ->
            stringArray.getElementsByTagName("item").each { item ->
                obfuscateElement(item)
            }
        }

        // Ensure that at least 250 strings are generated so that string resources are pushed into string blocks
        // (required for unobfuscation)
        def root = doc.getDocumentElement()
        (1..250).each {
            def el = doc.createElement("string")
            el.setAttribute("name", "\$${it}")
            el.appendChild(doc.createTextNode("\u07FF${it}"))
            root.appendChild(el)
        }

        def transformerFactory = TransformerFactory.newInstance()
        def transformer = transformerFactory.newTransformer()
        def domSource = new DOMSource(doc)
        def result = new StreamResult(target)
        transformer.transform(domSource, result)
    }

    def static getElements(doc, name) {

        def elements = new ArrayList<Element>()
        doc.getElementsByTagName(name).each { el ->
            elements.add(el as Element)
        }

        return elements;
    }

    def obfuscateElement(el) {

        if (el.getAttributeNS(TOOLS_NAMESPACE, "obfuscate") == "false") {
            el.getParentNode().removeChild(el)
        } else {
            def prefix = "\u07FF\u07FF\u07FF"
            def traversal = el.getOwnerDocument()
            def walker = traversal.createTreeWalker(el, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_CDATA_SECTION, null, true)
            random = new Random(project.stringResourcesObfuscatorSettings.seed)

            for (def n = walker.firstChild(); n != null; n = walker.nextSibling()) {
                n.setNodeValue(prefix + obfuscate(n.getNodeValue()))
                prefix = ""
            }
        }
    }

    def String obfuscate(string) {

        quoting = false
        skipping = false

        StringBuilder b = new StringBuilder()
        for (int n = 0; n < string.length(); n++) {
            def c = string.charAt(n) as int
            if (acceptChar(c)) {
                c ^= random.nextInt(MAX)
                if (c <= ' ' || c == '\\' || c == '\'' || c == '"' || c == '%') {
                    c = '\u07FF' as char
                    n--
                }
            }
            b.append((char) c)
        }

        return b.toString()
    }

    def boolean acceptChar(c) {

        if (c <= ' ' || c == '\\' || c == '\'' || c == '"' || skipping) {
            if (c == '"') quoting = !quoting
            skipping = c == '\\' && !skipping
            return false
        }
        return true
    }
}
