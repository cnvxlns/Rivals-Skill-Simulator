package com.example.skillsim.service.xlsx

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 테스트용 최소 xlsx를 메모리에서 만든다.
 *
 * 원천 워크북은 `.gitignore`가 docs 아래를 통째로 빼기 때문에 테스트가 읽을 파일이 없다.
 * 바이너리를 커밋하는 대신 여기서 만든다 — 리더가 무엇을 기대하는지가 코드로 드러나고,
 * 리뷰어가 열어 볼 수 없는 파일에 계약을 숨기지 않게 된다.
 *
 * XML은 이어 붙여 만든다. 코틀린은 원시 문자열 안에 원시 문자열을 넣을 수 없어서다.
 */
internal class TestWorkbookBuilder {

    private val sheets = LinkedHashMap<String, MutableList<String>>()
    private val sharedStrings = ArrayList<String>()

    fun sheet(name: String): SheetBuilder {
        sheets.getOrPut(name) { ArrayList() }
        return SheetBuilder(name)
    }

    inner class SheetBuilder(private val name: String) {
        /** 숫자 셀. */
        fun number(ref: String, value: Any): SheetBuilder = apply {
            cells() += "<c r=\"$ref\"><v>$value</v></c>"
        }

        /** 공유 문자열 셀. 실제 워크북이 문자열을 담는 방식이다. */
        fun text(ref: String, value: String): SheetBuilder = apply {
            var index = sharedStrings.indexOf(value)
            if (index < 0) {
                sharedStrings += value
                index = sharedStrings.lastIndex
            }
            cells() += "<c r=\"$ref\" t=\"s\"><v>$index</v></c>"
        }

        /** 오류 셀(#N/A). 워크북에 90칸 있다. */
        fun error(ref: String): SheetBuilder = apply {
            cells() += "<c r=\"$ref\" t=\"e\"><v>#N/A</v></c>"
        }

        /** 수식 셀. 리더는 수식을 건너뛰고 캐시된 값만 읽어야 한다. */
        fun formula(ref: String, formula: String, cached: Any): SheetBuilder = apply {
            cells() += "<c r=\"$ref\"><f>${escape(formula)}</f><v>$cached</v></c>"
        }

        fun sheet(other: String): SheetBuilder = this@TestWorkbookBuilder.sheet(other)

        fun and(): TestWorkbookBuilder = this@TestWorkbookBuilder

        fun build(): ByteArray = this@TestWorkbookBuilder.build()

        private fun cells() = sheets.getValue(name)
    }

    fun build(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val entries = sheets.keys.mapIndexed { i, name ->
                "<sheet name=\"${escape(name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>"
            }.joinToString("")
            zip.put("xl/workbook.xml", XML_HEADER + WORKBOOK_OPEN + "<sheets>" + entries + "</sheets></workbook>")

            val rels = sheets.keys.indices.joinToString("") { i ->
                "<Relationship Id=\"rId${i + 1}\" Target=\"worksheets/sheet${i + 1}.xml\" Type=\"$SHEET_REL\"/>"
            }
            zip.put("xl/_rels/workbook.xml.rels", XML_HEADER + RELS_OPEN + rels + "</Relationships>")

            if (sharedStrings.isNotEmpty()) {
                val items = sharedStrings.joinToString("") { "<si><t>${escape(it)}</t></si>" }
                zip.put("xl/sharedStrings.xml", XML_HEADER + sstOpen(sharedStrings.size) + items + "</sst>")
            }

            sheets.values.forEachIndexed { i, cells ->
                zip.put(
                    "xl/worksheets/sheet${i + 1}.xml",
                    XML_HEADER + SHEET_OPEN + "<sheetData><row r=\"1\">" +
                        cells.joinToString("") + "</row></sheetData></worksheet>",
                )
            }
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.put(name: String, body: String) {
        putNextEntry(ZipEntry(name))
        write(body.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    companion object {
        const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        const val DOC_REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
        const val SHEET_REL = "$DOC_REL_NS/worksheet"

        const val WORKBOOK_OPEN = "<workbook xmlns=\"$MAIN_NS\" xmlns:r=\"$DOC_REL_NS\">"
        const val RELS_OPEN = "<Relationships xmlns=\"$PKG_REL_NS\">"
        const val SHEET_OPEN = "<worksheet xmlns=\"$MAIN_NS\">"

        fun sstOpen(count: Int) = "<sst xmlns=\"$MAIN_NS\" count=\"$count\">"

        fun escape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        /**
         * 시트 XML을 통째로 넣은 워크북.
         *
         * 방어 테스트(XXE·엔티티 폭탄)는 XML을 직접 짜야 해서 빌더로는 만들 수 없다.
         */
        fun rawSheet(name: String, sheetXml: String): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("xl/workbook.xml"))
                zip.write(
                    (
                        XML_HEADER + WORKBOOK_OPEN +
                            "<sheets><sheet name=\"${escape(name)}\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
                            "</workbook>"
                        ).toByteArray(),
                )
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
                zip.write(
                    (
                        XML_HEADER + RELS_OPEN +
                            "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\" Type=\"$SHEET_REL\"/>" +
                            "</Relationships>"
                        ).toByteArray(),
                )
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                zip.write(sheetXml.toByteArray())
                zip.closeEntry()
            }
            return out.toByteArray()
        }

        /** 공유 문자열이 서식 때문에 런으로 쪼개진 경우. 한중일 워크북에서 흔하다. */
        fun withSplitSharedString(sheetName: String, parts: List<String>): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("xl/workbook.xml"))
                zip.write(
                    (
                        XML_HEADER + WORKBOOK_OPEN +
                            "<sheets><sheet name=\"${escape(sheetName)}\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
                            "</workbook>"
                        ).toByteArray(),
                )
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
                zip.write(
                    (
                        XML_HEADER + RELS_OPEN +
                            "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\" Type=\"$SHEET_REL\"/>" +
                            "</Relationships>"
                        ).toByteArray(),
                )
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
                val runs = parts.joinToString("") { "<r><t>${escape(it)}</t></r>" }
                zip.write((XML_HEADER + sstOpen(1) + "<si>" + runs + "</si></sst>").toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                zip.write(
                    (
                        XML_HEADER + SHEET_OPEN +
                            "<sheetData><row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row></sheetData>" +
                            "</worksheet>"
                        ).toByteArray(),
                )
                zip.closeEntry()
            }
            return out.toByteArray()
        }
    }
}
