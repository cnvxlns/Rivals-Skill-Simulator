package com.example.skillsim.service.xlsx

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * xlsx 리더.
 *
 * 남이 올린 파일을 읽으므로 방어 테스트가 기능 테스트만큼 중요하다. XXE와 zip 폭탄은
 * 실제 파일로는 재현할 수 없으니 여기서 직접 만들어 본다.
 */
class XlsxReaderTest {

    private fun workbook(block: TestWorkbookBuilder.SheetBuilder.() -> Unit): ByteArray =
        TestWorkbookBuilder().sheet("라인업").apply(block).and().build()

    @Test
    fun `문자열과 숫자를 읽는다`() {
        val sheet = XlsxReader().readSheet(
            workbook {
                text("B10", "포지션")
                number("G11", "83")
            },
            "라인업",
        )

        assertThat(sheet.text("B", 10)).isEqualTo("포지션")
        assertThat(sheet.number("G", 11)).isEqualTo(83.0)
        assertThat(sheet.integer("G", 11)).isEqualTo(83)
        assertThat(sheet.value("Z", 99)).isNull()
    }

    @Test
    fun `두 글자 열 주소를 읽는다`() {
        // 덱 스코어 블록이 AT~HL에 있다. AA 이후를 못 읽으면 통째로 빈다.
        val sheet = XlsxReader().readSheet(
            workbook {
                number("Z1", "1")
                number("AA1", "2")
                number("AB1", "3")
                number("HL1", "4")
            },
            "라인업",
        )

        assertThat(sheet.number("AA", 1)).isEqualTo(2.0)
        assertThat(sheet.number("AB", 1)).isEqualTo(3.0)
        assertThat(sheet.number("HL", 1)).isEqualTo(4.0)
    }

    @Test
    fun `오류 셀은 값이 없는 것으로 본다`() {
        // 워크북에는 #N/A가 90칸 있다. FA 카드의 초월·강화 조회가 실패한 흔적이다.
        val sheet = XlsxReader().readSheet(workbook { error("AC11") }, "라인업")

        assertThat(sheet.value("AC", 11)).isNull()
    }

    @Test
    fun `수식은 건너뛰고 캐시된 값만 읽는다`() {
        val sheet = XlsxReader().readSheet(
            workbook { formula("BB11", "SUMIF(A1:A2,1,B1:B2)", 35) },
            "라인업",
        )

        assertThat(sheet.number("BB", 11)).isEqualTo(35.0)
    }

    @Test
    fun `런으로 쪼개진 공유 문자열을 이어 붙인다`() {
        val bytes = TestWorkbookBuilder.withSplitSharedString("라인업", listOf("스피드", "&컨택"))

        val sheet = XlsxReader().readSheet(bytes, "라인업")

        assertThat(sheet.text("A", 1)).isEqualTo("스피드&컨택")
    }

    @Test
    fun `없는 시트를 고르면 있는 시트를 알려 준다`() {
        val bytes = TestWorkbookBuilder().sheet("초월").number("A1", "1").and().build()

        assertThatThrownBy { XlsxReader().readSheet(bytes, "라인업") }
            .isInstanceOf(XlsxException::class.java)
            .hasMessageContaining("초월")
    }

    @Test
    fun `xlsx가 아니면 알아볼 수 있는 메시지를 준다`() {
        val notAWorkbook = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use {
                it.putNextEntry(ZipEntry("hello.txt"))
                it.write("hi".toByteArray())
                it.closeEntry()
            }
        }.toByteArray()

        assertThatThrownBy { XlsxReader().readSheet(notAWorkbook, "라인업") }
            .isInstanceOf(XlsxException::class.java)
            .hasMessageContaining("xlsx")
    }

    @Test
    fun `외부 엔티티를 읽지 않는다`() {
        // XXE. 막지 않으면 업로드 하나로 서버의 파일을 읽어 갈 수 있다.
        val xxe = """<?xml version="1.0"?>
            <!DOCTYPE t [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <sheetData><row r="1"><c r="A1"><v>&x;</v></c></row></sheetData></worksheet>"""
        val bytes = TestWorkbookBuilder.rawSheet("라인업", xxe)

        assertThatThrownBy { XlsxReader().readSheet(bytes, "라인업") }
            .isInstanceOf(XlsxException::class.java)
    }

    @Test
    fun `엔티티 폭탄을 펼치지 않는다`() {
        // billion laughs. DTD를 끄면 선언 단계에서 막힌다.
        val bomb = """<?xml version="1.0"?>
            <!DOCTYPE t [
              <!ENTITY a "aaaaaaaaaa">
              <!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">
              <!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;">
            ]>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <sheetData><row r="1"><c r="A1"><v>&c;</v></c></row></sheetData></worksheet>"""
        val bytes = TestWorkbookBuilder.rawSheet("라인업", bomb)

        assertThatThrownBy { XlsxReader().readSheet(bytes, "라인업") }
            .isInstanceOf(XlsxException::class.java)
    }

    @Test
    fun `압축을 푼 크기에 상한이 있다`() {
        // zip 폭탄. 0으로 채운 10MB는 거의 압축되지 않는 크기로 줄어든다.
        val big = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use {
                it.putNextEntry(ZipEntry("xl/workbook.xml"))
                it.write(ByteArray(2 * 1024 * 1024))
                it.closeEntry()
            }
        }.toByteArray()

        assertThatThrownBy { XlsxReader(maxInflatedBytes = 1024).readSheet(big, "라인업") }
            .isInstanceOf(XlsxException::class.java)
            .hasMessageContaining("너무 큽니다")
    }

    @Test
    fun `항목 수에 상한이 있다`() {
        val many = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                repeat(50) {
                    zip.putNextEntry(ZipEntry("file$it.xml"))
                    zip.write("x".toByteArray())
                    zip.closeEntry()
                }
            }
        }.toByteArray()

        assertThatThrownBy { XlsxReader(maxEntries = 10).readSheet(many, "라인업") }
            .isInstanceOf(XlsxException::class.java)
            .hasMessageContaining("너무 많습니다")
    }

    @Test
    fun `업로드 크기를 미리 막는다`() {
        val stream = ByteArray(100).inputStream()

        assertThat(stream.readAtMost(100)).hasSize(100)
        assertThatThrownBy { ByteArray(101).inputStream().readAtMost(100) }
            .isInstanceOf(XlsxException::class.java)
    }

    @Test
    fun `열 이름과 번호를 서로 옮긴다`() {
        assertThat(XlsxReader.columnIndex("A")).isEqualTo(1)
        assertThat(XlsxReader.columnIndex("Z")).isEqualTo(26)
        assertThat(XlsxReader.columnIndex("AA")).isEqualTo(27)
        assertThat(XlsxReader.columnName(1)).isEqualTo("A")
        assertThat(XlsxReader.columnName(27)).isEqualTo("AA")
        assertThat(XlsxReader.columnName(XlsxReader.columnIndex("HL"))).isEqualTo("HL")
    }

}
