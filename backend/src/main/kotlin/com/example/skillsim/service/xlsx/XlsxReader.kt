package com.example.skillsim.service.xlsx

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/** 워크북을 읽을 수 없다. 400·422로 옮기는 것은 호출자 몫이다. */
class XlsxException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * xlsx에서 셀 값만 꺼내는 최소 리더.
 *
 * Apache POI를 넣지 않았다. 필요한 것은 시트 하나의 리터럴 값과 공유 문자열뿐이고, 수식
 * 평가도 서식도 쓰기도 필요 없다. POI는 poi + poi-ooxml + xmlbeans + commons-compress +
 * commons-io + log4j-api를 끌고 와 12MB쯤 되는데, `build.gradle`이 spring-boot-starter-security를
 * 거절한 것과 같은 이유로 여기서도 값이 안 맞는다. 언젠가 xlsx를 **쓰거나** 서식을 읽어야
 * 하면 그때 다시 볼 일이다.
 *
 * 실제 워크북을 뜯어 확인한 것: `inlineStr`이 없고, 한중일 워크북이 흔히 갖는 `rPh`(음성
 * 표기) 런도 없으며, 모든 `<c>`에 `r` 속성이 있고, 특이한 타입은 `t="e"`(#N/A)뿐이다.
 *
 * **이 리더는 남이 올린 파일을 읽는다.** 그래서 방어가 둘 필요하다.
 * - XXE·엔티티 폭탄: DTD와 외부 엔티티를 끈다.
 * - zip 폭탄: 압축을 푼 총 바이트와 항목 수에 상한을 둔다.
 */
class XlsxReader(
    private val maxInflatedBytes: Long = DEFAULT_MAX_INFLATED_BYTES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {

    /** 시트 한 장의 셀 값. 키는 `("AU", 10)`처럼 (열 이름, 행 번호)다. */
    class Sheet(val name: String, private val cells: Map<CellRef, String>) {
        fun value(column: String, row: Int): String? = cells[CellRef(column, row)]

        fun text(column: String, row: Int): String = value(column, row)?.trim().orEmpty()

        fun number(column: String, row: Int): Double? = value(column, row)?.trim()?.toDoubleOrNull()

        fun integer(column: String, row: Int): Int? = number(column, row)?.toInt()

        val size: Int get() = cells.size
    }

    data class CellRef(val column: String, val row: Int)

    /**
     * 워크북에서 시트 하나를 읽는다.
     *
     * zip 항목 순서는 보장되지 않아 두 번 훑는다. 95KB짜리 파일이라 두 번 읽어도 공짜다.
     */
    fun readSheet(bytes: ByteArray, sheetName: String): Sheet {
        val parts = firstPass(bytes)
        val target = parts.sheets[sheetName]
            ?: throw XlsxException(
                "워크북에 '$sheetName' 시트가 없습니다. 있는 시트: ${parts.sheets.keys.joinToString(", ")}",
            )
        val cells = secondPass(bytes, target, parts.sharedStrings)
        return Sheet(sheetName, cells)
    }

    private class Parts(val sheets: Map<String, String>, val sharedStrings: List<String>)

    private fun firstPass(bytes: ByteArray): Parts {
        var workbook: ByteArray? = null
        var rels: ByteArray? = null
        var strings: ByteArray? = null
        forEachEntry(bytes) { name, content ->
            when (name) {
                "xl/workbook.xml" -> workbook = content
                "xl/_rels/workbook.xml.rels" -> rels = content
                "xl/sharedStrings.xml" -> strings = content
            }
        }
        val workbookXml = workbook
            ?: throw XlsxException("xlsx 파일이 아닌 것 같습니다. xl/workbook.xml이 없습니다.")
        val targets = readRelationships(rels)
        return Parts(readSheetNames(workbookXml, targets), readSharedStrings(strings))
    }

    private fun secondPass(
        bytes: ByteArray,
        target: String,
        sharedStrings: List<String>,
    ): Map<CellRef, String> {
        var cells: Map<CellRef, String>? = null
        forEachEntry(bytes) { name, content ->
            if (name == target) {
                cells = readCells(content, sharedStrings)
            }
        }
        return cells ?: throw XlsxException("워크북 안에서 시트 파일($target)을 찾지 못했습니다.")
    }

    /**
     * zip 항목을 하나씩 넘긴다. 상한을 넘으면 즉시 멈춘다.
     *
     * 압축률이 극단적인 파일을 그대로 풀면 메모리가 날아간다. 항목 하나가 아니라 **누적**
     * 바이트를 세는 이유는, 작은 항목을 수천 개 넣어도 같은 결과가 되기 때문이다.
     */
    private inline fun forEachEntry(bytes: ByteArray, action: (String, ByteArray) -> Unit) {
        var total = 0L
        var count = 0
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (++count > maxEntries) {
                        throw XlsxException("워크북 안의 파일이 너무 많습니다(${maxEntries}개 초과).")
                    }
                    val content = zip.readNBytes((maxInflatedBytes - total).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    total += content.size
                    if (total >= maxInflatedBytes) {
                        throw XlsxException("워크북의 압축을 푼 크기가 너무 큽니다.")
                    }
                    action(entry.name, content)
                }
            }
        } catch (ex: java.io.IOException) {
            throw XlsxException("xlsx 파일을 열 수 없습니다.", ex)
        }
    }

    private fun readRelationships(rels: ByteArray?): Map<String, String> {
        if (rels == null) return emptyMap()
        val out = HashMap<String, String>()
        parse(rels) { reader ->
            if (reader.localName == "Relationship") {
                val id = reader.getAttributeValue(null, "Id")
                val target = reader.getAttributeValue(null, "Target")
                if (id != null && target != null) {
                    out[id] = if (target.startsWith("xl/")) target else "xl/" + target.removePrefix("/")
                }
            }
        }
        return out
    }

    private fun readSheetNames(workbook: ByteArray, targets: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        parse(workbook) { reader ->
            if (reader.localName == "sheet") {
                val name = reader.getAttributeValue(null, "name")
                // r:id는 네임스페이스가 붙는다. 접두사를 가정하지 않고 지역 이름으로 찾는다.
                val relationId = (0 until reader.attributeCount)
                    .firstOrNull { reader.getAttributeLocalName(it) == "id" }
                    ?.let { reader.getAttributeValue(it) }
                val target = targets[relationId]
                if (name != null && target != null) {
                    out[name] = target
                }
            }
        }
        return out
    }

    /**
     * 공유 문자열.
     *
     * `<si>`는 서식이 바뀌는 지점마다 `<r><t>`로 쪼개진다. 전부 이어 붙여야 원문이 된다.
     */
    private fun readSharedStrings(strings: ByteArray?): List<String> {
        if (strings == null) return emptyList()
        val out = ArrayList<String>()
        val current = StringBuilder()
        var inItem = false
        var inText = false
        parseAll(strings) { reader, event ->
            when (event) {
                XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                    "si" -> {
                        inItem = true
                        current.setLength(0)
                    }
                    "t" -> inText = true
                }
                XMLStreamConstants.CHARACTERS -> if (inItem && inText) current.append(reader.text)
                XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
                    "t" -> inText = false
                    "si" -> {
                        out += current.toString()
                        inItem = false
                    }
                }
            }
        }
        return out
    }

    private fun readCells(sheet: ByteArray, sharedStrings: List<String>): Map<CellRef, String> {
        val out = HashMap<CellRef, String>()
        var ref: String? = null
        var type: String? = null
        var inValue = false
        var inInline = false
        val value = StringBuilder()

        parseAll(sheet) { reader, event ->
            when (event) {
                XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                    "c" -> {
                        ref = reader.getAttributeValue(null, "r")
                        type = reader.getAttributeValue(null, "t")
                        value.setLength(0)
                    }
                    // 수식은 건너뛴다. 우리는 입력값만 읽는다.
                    "v" -> inValue = true
                    "is" -> inInline = true
                }
                XMLStreamConstants.CHARACTERS -> if (inValue || inInline) value.append(reader.text)
                XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
                    "v" -> inValue = false
                    "is" -> inInline = false
                    "c" -> {
                        val cell = ref?.let(::parseRef)
                        val raw = value.toString()
                        if (cell != null && raw.isNotEmpty()) {
                            // t="e"는 #N/A 같은 오류 셀이다. 값이 없는 것으로 본다.
                            val resolved = when (type) {
                                "e" -> null
                                "s" -> raw.trim().toIntOrNull()?.let { sharedStrings.getOrNull(it) }
                                else -> raw
                            }
                            if (resolved != null) out[cell] = resolved
                        }
                        ref = null
                        type = null
                        value.setLength(0)
                    }
                }
            }
        }
        return out
    }

    private inline fun parse(bytes: ByteArray, crossinline onStart: (XMLStreamReader) -> Unit) {
        parseAll(bytes) { reader, event ->
            if (event == XMLStreamConstants.START_ELEMENT) onStart(reader)
        }
    }

    private inline fun parseAll(bytes: ByteArray, action: (XMLStreamReader, Int) -> Unit) {
        val reader = try {
            factory().createXMLStreamReader(ByteArrayInputStream(bytes))
        } catch (ex: XMLStreamException) {
            throw XlsxException("워크북의 XML을 읽을 수 없습니다.", ex)
        }
        try {
            while (reader.hasNext()) {
                action(reader, reader.next())
            }
        } catch (ex: XMLStreamException) {
            throw XlsxException("워크북의 XML을 읽을 수 없습니다.", ex)
        } finally {
            runCatching { reader.close() }
        }
    }

    companion object {
        /** 업로드는 2MB로 막혀 있다. 압축을 풀어도 이만큼을 넘길 이유가 없다. */
        const val DEFAULT_MAX_INFLATED_BYTES = 40L * 1024 * 1024

        const val DEFAULT_MAX_ENTRIES = 512

        private val REF = Regex("([A-Z]{1,3})(\\d+)")

        fun parseRef(ref: String): CellRef? {
            val match = REF.matchEntire(ref) ?: return null
            val row = match.groupValues[2].toIntOrNull() ?: return null
            return CellRef(match.groupValues[1], row)
        }

        /** 열 이름을 1부터 세는 번호로. `A`=1, `AA`=27. */
        fun columnIndex(column: String): Int =
            column.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) }

        /** [columnIndex]의 역. */
        fun columnName(index: Int): String {
            var remaining = index
            val out = StringBuilder()
            while (remaining > 0) {
                val rem = (remaining - 1) % 26
                out.insert(0, ('A' + rem))
                remaining = (remaining - 1) / 26
            }
            return out.toString()
        }

        /**
         * 남이 올린 XML을 읽는다. DTD와 외부 엔티티를 끄지 않으면 XXE와 엔티티 폭탄에 열린다.
         */
        private fun factory(): XMLInputFactory = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.IS_COALESCING, false)
        }
    }
}

/** 스트림을 상한까지만 읽는다. 업로드를 통째로 메모리에 올리기 전에 크기를 막는다. */
fun InputStream.readAtMost(limit: Int): ByteArray {
    val bytes = readNBytes(limit + 1)
    if (bytes.size > limit) {
        throw XlsxException("업로드 파일이 너무 큽니다.")
    }
    return bytes
}
