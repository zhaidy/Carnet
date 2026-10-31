package com.personal.carnet.importer

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ImportException(message: String) : Exception(message)

/**
 * 读表格：.xlsx（只读第一个工作表）或 .csv。返回一行一个字符串列表，空格子是 ""。
 * xlsx 就是一个 zip，里面是 XML；表很小，直接用 DOM 读。
 */
object SheetReader {

    fun read(bytes: ByteArray): List<List<String>> =
        if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) readXlsx(bytes) else readCsv(String(bytes, Charsets.UTF_8))

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.endsWith(".xml") || entry.name.endsWith(".rels")) files[entry.name] = zip.readBytes()
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val sheetName = firstSheetPath(files) ?: throw ImportException("表格里找不到工作表")
        val sheet = files[sheetName] ?: throw ImportException("表格里找不到工作表")
        val doc = parse(sheet)
        val rows = mutableListOf<List<String>>()
        val rowNodes = doc.getElementsByTagName("row")
        for (i in 0 until rowNodes.length) {
            val row = rowNodes.item(i) as Element
            val cells = sortedMapOf<Int, String>()
            val cellNodes = row.getElementsByTagName("c")
            for (j in 0 until cellNodes.length) {
                val c = cellNodes.item(j) as Element
                val col = columnIndex(c.getAttribute("r")).takeIf { it >= 0 } ?: (cells.keys.maxOrNull()?.plus(1) ?: 0)
                cells[col] = cellValue(c, shared)
            }
            val width = (cells.keys.maxOrNull() ?: -1) + 1
            rows += List(width) { cells[it].orEmpty() }
        }
        return rows
    }

    /** workbook.xml 里第一个 sheet 对应的文件，找不到就退回 sheet1.xml */
    private fun firstSheetPath(files: Map<String, ByteArray>): String? {
        val workbook = files["xl/workbook.xml"]?.let(::parse)
        val rels = files["xl/_rels/workbook.xml.rels"]?.let(::parse)
        if (workbook != null && rels != null) {
            val first = workbook.getElementsByTagName("sheet").item(0) as? Element
            val rid = first?.getAttribute("r:id")
            val relNodes = rels.getElementsByTagName("Relationship")
            for (i in 0 until relNodes.length) {
                val r = relNodes.item(i) as Element
                if (r.getAttribute("Id") == rid) {
                    val target = r.getAttribute("Target").removePrefix("/xl/").removePrefix("/")
                    return if (target.startsWith("xl/")) target else "xl/$target"
                }
            }
        }
        return files.keys.firstOrNull { it.startsWith("xl/worksheets/sheet") }
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val doc = parse(bytes)
        val items = doc.getElementsByTagName("si")
        return (0 until items.length).map { i ->
            val si = items.item(i) as Element
            val ts = si.getElementsByTagName("t")
            (0 until ts.length).joinToString("") { ts.item(it).textContent }
        }
    }

    private fun cellValue(c: Element, shared: List<String>): String {
        val v = c.getElementsByTagName("v").item(0)?.textContent
        return when (c.getAttribute("t")) {
            "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
            "inlineStr" -> {
                val ts = c.getElementsByTagName("t")
                (0 until ts.length).joinToString("") { ts.item(it).textContent }
            }
            "b" -> if (v == "1") "TRUE" else "FALSE"
            else -> v.orEmpty()
        }
    }

    /** "C12" → 2 */
    fun columnIndex(ref: String): Int {
        var n = 0
        var seen = false
        for (ch in ref) {
            if (ch in 'A'..'Z') { n = n * 26 + (ch - 'A' + 1); seen = true } else break
        }
        return if (seen) n - 1 else -1
    }

    private fun parse(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        .newDocumentBuilder().parse(ByteArrayInputStream(bytes)).also { it.documentElement.normalize() }

    /** 最简单的 CSV：逗号分隔，双引号包起来的格子里可以有逗号和换行 */
    fun readCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val ch = s[i]
            when {
                quoted && ch == '"' && i + 1 < s.length && s[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == ',' -> { row += cell.toString(); cell.clear() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                    row += cell.toString(); cell.clear()
                    rows += row; row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}
