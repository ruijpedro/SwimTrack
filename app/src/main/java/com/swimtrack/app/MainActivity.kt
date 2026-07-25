package com.swimtrack.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.Locale

class MainActivity : Activity() {

    data class SwimTime(
        val event: String,
        val pool: String,
        val time: String,
        val date: String = "-",
        val city: String = "-",
        val source: String = "Swimrankings"
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var content: LinearLayout
    private lateinit var tabAtleta: TextView
    private lateinit var tabImportar: TextView
    private lateinit var tabTempos: TextView
    private lateinit var tabTac: TextView
    private lateinit var tabEvolucao: TextView
    private lateinit var tabMais: TextView

    private val PICK_PDF = 9001
    private val bg = Color.rgb(18, 35, 70)
    private val card = Color.rgb(61, 82, 120)
    private val cardDark = Color.rgb(48, 70, 105)
    private val blue = Color.rgb(60, 170, 255)
    private val green = Color.rgb(30, 150, 75)
    private val red = Color.rgb(185, 55, 55)
    private val yellow = Color.rgb(255, 220, 45)
    private val white = Color.WHITE
    private val soft = Color.rgb(215, 228, 240)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        prefs = getSharedPreferences("swimtrack", MODE_PRIVATE)
        seedDefaults()
        buildUI()
    }

    private fun seedDefaults() {
        if (!prefs.contains("season_start")) prefs.edit().putString("season_start", "2026").apply()
        if (!prefs.contains("sex")) prefs.edit().putString("sex", "F").apply()
    }

    private fun buildUI() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 36, 24, 40)
            setBackgroundColor(bg)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(190, 190)
        }
        root.addView(icon)
        root.addView(label("SWIMTRACK", white, bg, 34f))
        root.addView(label("SWIMRANKINGS • TAC • EVOLUÇÃO", soft, bg, 14f))
        root.addView(label(summaryTop(), yellow, cardDark, 15f))
        buildTabs(root)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content)
        scroll.addView(root)
        setContentView(scroll)
        showAtleta()
    }

    private fun buildTabs(root: LinearLayout) {
        val horizontal = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 18, 0, 18) }
        tabAtleta = tab("ATLETA")
        tabImportar = tab("IMPORTAR")
        tabTempos = tab("TEMPOS")
        tabTac = tab("TAC")
        tabEvolucao = tab("EVOLUÇÃO")
        tabMais = tab("MAIS")
        listOf(tabAtleta, tabImportar, tabTempos, tabTac, tabEvolucao, tabMais).forEach {
            row.addView(it, LinearLayout.LayoutParams(190, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        horizontal.addView(row)
        root.addView(horizontal)
        tabAtleta.setOnClickListener { showAtleta() }
        tabImportar.setOnClickListener { showImportar() }
        tabTempos.setOnClickListener { showTempos() }
        tabTac.setOnClickListener { showTac("ALL") }
        tabEvolucao.setOnClickListener { showEvolucao() }
        tabMais.setOnClickListener { showMais() }
    }

    private fun clear(active: TextView) {
        content.removeAllViews()
        listOf(tabAtleta, tabImportar, tabTempos, tabTac, tabEvolucao, tabMais).forEach {
            it.setBackgroundColor(card); it.setTextColor(white)
        }
        active.setBackgroundColor(yellow); active.setTextColor(bg)
    }

    private fun showAtleta() {
        clear(tabAtleta)
        content.addView(section("ATLETA"))
        val year = get("year")
        val sex = get("sex").ifBlank { "F" }
        val season = get("season_start").toIntOrNull() ?: 2026
        content.addView(info("Nome", get("name").ifBlank { "Por importar" }))
        content.addView(info("Ano de nascimento", year.ifBlank { "Por importar" }))
        content.addView(info("Género", if (sex == "F") "Feminino" else "Masculino"))
        content.addView(info("Clube", get("club").ifBlank { "Por importar" }))
        content.addView(info("País", get("country").ifBlank { "Portugal" }))
        content.addView(info("Época", "$season/${(season + 1).toString().takeLast(2)}"))
        content.addView(info("Escalão", get("category_manual").ifBlank { calculateCategory(year.toIntOrNull(), season, sex) }))
        content.addView(info("Resumo", athleteSummary()))
        content.addView(button("✏️ EDITAR PERFIL") { showEditProfile() })
        content.addView(button("🌐 ABRIR SWIMRANKINGS") {
            val id = get("athlete_id").ifBlank { "5631298" }
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.swimrankings.net/index.php?page=athleteDetail&athleteId=$id")))
        })
    }

    private fun showEditProfile() {
        clear(tabAtleta)
        content.addView(section("EDITAR PERFIL"))
        val name = input("Nome", get("name"))
        val year = input("Ano de nascimento", get("year"))
        val club = input("Clube", get("club"))
        val country = input("País", get("country").ifBlank { "Portugal" })
        val sex = input("Género: F ou M", get("sex").ifBlank { "F" })
        val season = input("Ano inicial da época", get("season_start").ifBlank { "2026" })
        val category = input("Escalão manual (vazio = automático)", get("category_manual"))
        val athleteId = input("ID Swimrankings", get("athlete_id").ifBlank { "5631298" })
        listOf(name, year, club, country, sex, season, category, athleteId).forEach { content.addView(it) }
        content.addView(button("💾 GUARDAR PERFIL") {
            val normalizedSex = sex.text.toString().trim().uppercase(Locale.ROOT).let { if (it.startsWith("M")) "M" else "F" }
            prefs.edit()
                .putString("name", name.text.toString().trim())
                .putString("year", year.text.toString().trim())
                .putString("club", club.text.toString().trim())
                .putString("country", country.text.toString().trim())
                .putString("sex", normalizedSex)
                .putString("season_start", season.text.toString().trim())
                .putString("category_manual", category.text.toString().trim())
                .putString("athlete_id", athleteId.text.toString().trim())
                .apply()
            toast("Perfil guardado.")
            showAtleta()
        })
        content.addView(button("↩ VOLTAR") { showAtleta() })
    }

    private fun showImportar() {
        clear(tabImportar)
        content.addView(section("IMPORTAR PDF"))
        content.addView(info("PDF Swimrankings", "Lê automaticamente perfil, clube, piscina longa/curta e recordes pessoais ‘De sempre’."))
        content.addView(button("📄 IMPORTAR PDF SWIMRANKINGS") { openPdfPicker() })
        content.addView(button("➕ INSERIR TEMPO MANUAL") { showEditTime(null) })
        content.addView(button("🧪 COLAR TEXTO EXTRAÍDO") { showPasteText() })
    }

    private fun openPdfPicker() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/pdf"
        }, PICK_PDF)
    }

    private fun showPasteText() {
        clear(tabImportar)
        content.addView(section("COLAR TEXTO"))
        val box = inputMulti("Cola o texto extraído do PDF Swimrankings")
        content.addView(box)
        content.addView(button("📥 IMPORTAR") {
            val count = importSwimrankingsPdfText(box.text.toString())
            toast("Importados $count tempos.")
            showTempos()
        })
    }

    private fun showTempos() {
        clear(tabTempos)
        content.addView(section("RECORDES PESSOAIS"))
        val times = loadTimes()
        if (times.isEmpty()) {
            content.addView(info("Sem tempos", "Importa o PDF Swimrankings ou insere um tempo manualmente."))
            content.addView(button("➕ INSERIR TEMPO") { showEditTime(null) })
            return
        }
        showPool("PISCINA CURTA 25m", "25m", times)
        showPool("PISCINA LONGA 50m", "50m", times)
        content.addView(button("➕ INSERIR TEMPO MANUAL") { showEditTime(null) })
    }

    private fun showPool(title: String, pool: String, times: List<SwimTime>) {
        val filtered = times.filter { it.pool == pool }
        if (filtered.isEmpty()) return
        content.addView(section(title))
        listOf("Livres", "Costas", "Bruços", "Mariposa", "Estilos").forEach { stroke ->
            val rows = filtered.filter { it.event.contains(stroke, true) }.sortedBy { distanceOf(it.event) }
            if (rows.isNotEmpty()) {
                content.addView(subSection(stroke.uppercase(Locale.ROOT)))
                rows.forEach { item ->
                    val tac = findTac(item.event, item.pool)
                    val status = statusText(item.time, tac)
                    content.addView(infoColor(item.event, "Tempo: ${item.time}\nData: ${item.date}\nCidade: ${item.city}\n${if (tac.isBlank()) "TAC: por definir" else "TAC: $tac\n$status"}", if (tac.isNotBlank() && timeSeconds(item.time) <= timeSeconds(tac)) green else card))
                    content.addView(button("✏️ EDITAR ${item.event} ${item.pool}") { showEditTime(item) })
                }
            }
        }
    }

    private fun showEditTime(existing: SwimTime?) {
        clear(tabTempos)
        content.addView(section(if (existing == null) "INSERIR TEMPO" else "EDITAR TEMPO"))
        val event = input("Prova, ex.: 100 Livres", existing?.event ?: "")
        val pool = input("Piscina: 25m ou 50m", existing?.pool ?: "25m")
        val time = input("Tempo, ex.: 1:05.93", existing?.time ?: "")
        val date = input("Data", existing?.date ?: "-")
        val city = input("Cidade", existing?.city ?: "-")
        listOf(event, pool, time, date, city).forEach { content.addView(it) }
        content.addView(button("💾 GUARDAR TEMPO") {
            val normalizedEvent = normalizeEvent(event.text.toString())
            val normalizedPool = normalizePool(pool.text.toString())
            val normalizedTime = normalizeTime(time.text.toString())
            if (normalizedEvent.isBlank() || normalizedPool.isBlank() || normalizedTime.isBlank()) {
                toast("Preenche prova, piscina e tempo corretamente."); return@button
            }
            val list = loadTimes().toMutableList()
            if (existing != null) list.removeAll { it.event == existing.event && it.pool == existing.pool }
            list.removeAll { it.event == normalizedEvent && it.pool == normalizedPool }
            list.add(SwimTime(normalizedEvent, normalizedPool, normalizedTime, date.text.toString().trim().ifBlank { "-" }, city.text.toString().trim().ifBlank { "-" }, "Manual"))
            saveTimes(list)
            toast("Tempo guardado.")
            showTempos()
        })
        if (existing != null) content.addView(button("🗑 APAGAR TEMPO") {
            val list = loadTimes().filterNot { it.event == existing.event && it.pool == existing.pool }
            saveTimes(list); toast("Tempo apagado."); showTempos()
        })
        content.addView(button("↩ VOLTAR") { showTempos() })
    }

    private fun showTac(filter: String) {
        clear(tabTac)
        content.addView(section("TAC — JÚNIOR FEMININO 1.º ANO"))
        content.addView(info("Escalão ativo", get("category_manual").ifBlank { calculateCategory(get("year").toIntOrNull(), get("season_start").toIntOrNull() ?: 2026, get("sex")) }))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(smallButton("TODOS") { showTac("ALL") }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(smallButton("TEM TAC") { showTac("YES") }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(smallButton("FALTA") { showTac("NO") }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(row)
        val times = loadTimes()
        var qualified = 0
        var pending = 0
        times.forEach { item ->
            val tac = findTac(item.event, item.pool)
            val hasTac = tac.isNotBlank()
            val ok = hasTac && timeSeconds(item.time) <= timeSeconds(tac)
            if (ok) qualified++ else if (hasTac) pending++
            val visible = filter == "ALL" || (filter == "YES" && ok) || (filter == "NO" && hasTac && !ok)
            if (visible) {
                val text = if (!hasTac) "TAC por definir" else "Tempo: ${item.time}\nTAC: $tac\n${statusText(item.time, tac)}"
                content.addView(infoColor("${item.event} ${item.pool}", text, if (ok) green else red))
                content.addView(button("✏️ EDITAR TAC") { showEditTac(item.event, item.pool) })
            }
        }
        content.addView(section("RESUMO"))
        content.addView(info("Qualificada", qualified.toString()))
        content.addView(info("Por qualificar", pending.toString()))
        content.addView(button("➕ INSERIR TAC MANUAL") { showEditTac("", "25m") })
    }

    private fun showEditTac(eventValue: String, poolValue: String) {
        clear(tabTac)
        content.addView(section("EDITAR TAC"))
        val event = input("Prova", eventValue)
        val pool = input("Piscina", poolValue)
        val tac = input("TAC", if (eventValue.isBlank()) "" else findTac(eventValue, poolValue))
        listOf(event, pool, tac).forEach { content.addView(it) }
        content.addView(button("💾 GUARDAR TAC") {
            val e = normalizeEvent(event.text.toString()); val p = normalizePool(pool.text.toString()); val t = normalizeTime(tac.text.toString())
            if (e.isBlank() || p.isBlank() || t.isBlank()) { toast("Dados TAC inválidos."); return@button }
            val map = loadTacMap().toMutableMap(); map[tacKey(e, p)] = t; saveTacMap(map)
            toast("TAC guardado."); showTac("ALL")
        })
        content.addView(button("↩ VOLTAR") { showTac("ALL") })
    }

    private fun showEvolucao() {
        clear(tabEvolucao)
        content.addView(section("EVOLUÇÃO"))
        val current = loadTimes()
        val previous = loadPreviousTimes()
        if (previous.isEmpty()) {
            content.addView(info("Sem histórico", "Importa um novo PDF depois de já existirem tempos guardados. A app preservará a versão anterior."))
            return
        }
        current.forEach { now ->
            val old = previous.firstOrNull { it.event == now.event && it.pool == now.pool } ?: return@forEach
            val diff = timeSeconds(now.time) - timeSeconds(old.time)
            val text = if (diff < 0) "Melhoria: %.2f s".format(Locale.US, -diff) else if (diff > 0) "Mais lento: %.2f s".format(Locale.US, diff) else "Sem alteração"
            content.addView(infoColor("${now.event} ${now.pool}", "Anterior: ${old.time}\nAtual: ${now.time}\n$text", if (diff <= 0) green else red))
        }
    }

    private fun showMais() {
        clear(tabMais)
        content.addView(section("MAIS"))
        content.addView(button("📄 EXPORTAR RELATÓRIO PDF") { exportPdf() })
        content.addView(button("📤 PARTILHAR RESUMO") { shareSummary() })
        content.addView(button("🧹 LIMPAR TEMPOS") { confirm("Limpar todos os tempos?") { prefs.edit().remove("times").remove("previous_times").apply(); recreate() } })
        content.addView(button("🗑 LIMPAR TODOS OS DADOS") { confirm("Apagar perfil, tempos e TAC?") { prefs.edit().clear().apply(); recreate() } })
        content.addView(info("Informação", "Mantém o ícone e o grafismo atual. Os dados ficam guardados localmente no telemóvel."))
    }

    private fun importSwimrankingsPdfText(raw: String): Int {
        if (raw.isBlank()) return 0
        val text = raw.replace("\r\n", "\n").replace("\r", "\n")
        importProfile(text)
        val lines = text.lines().map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotBlank() }
        val results = mutableListOf<SwimTime>()
        var pool = ""
        var stroke = ""
        val styleRegex = "(Livres|Costas|Bruços|Brucos|Mariposa|Estilos)"
        val recordRegex = Regex("^(?:$styleRegex\\s+)?(50|100|200|400|800|1500)m\\s+(\\d{1,2}:\\d{2}[.,]\\d{2}|\\d{2}[.,]\\d{2})\\s+(\\d{1,2}\\s+[A-Za-zÀ-ÿ]{3}\\s+\\d{4})(?:\\s+(.+))?$", RegexOption.IGNORE_CASE)
        val styleOnlyRegex = Regex("^$styleRegex(?:\\s+(?:50|100|200|400|800|1500)m)?$", RegexOption.IGNORE_CASE)
        for (line in lines) {
            if (line.contains("Piscina longa", true)) { pool = "50m"; stroke = ""; continue }
            if (line.contains("Piscina curta", true)) { pool = "25m"; stroke = ""; continue }
            val match = recordRegex.find(line)
            if (match != null && pool.isNotBlank()) {
                val styleFromLine = match.groupValues[1]
                if (styleFromLine.isNotBlank()) stroke = normalizeStroke(styleFromLine)
                if (stroke.isBlank()) continue
                val distance = match.groupValues[2]
                val time = normalizeTime(match.groupValues[3])
                val date = match.groupValues[4]
                val city = match.groupValues[5].trim().ifBlank { "-" }
                if (time.isNotBlank()) results.add(SwimTime("$distance $stroke", pool, time, date, city, "Swimrankings PDF"))
                continue
            }
            val styleOnly = styleOnlyRegex.find(line)
            if (styleOnly != null) stroke = normalizeStroke(styleOnly.groupValues[1])
        }
        if (results.isNotEmpty()) {
            val old = loadTimes()
            if (old.isNotEmpty()) prefs.edit().putString("previous_times", encodeTimes(old)).apply()
            saveTimes(selectBest(results))
        }
        return results.distinctBy { "${it.pool}|${it.event}" }.size
    }

    private fun importProfile(text: String) {
        val profileLine = text.lines().map { it.trim().replace(Regex("\\s+"), " ") }.firstOrNull { it.contains("POR - Portugal", true) } ?: return
        val yearMatch = Regex("\\b(19|20)\\d{2}\\b").find(profileLine)
        val year = yearMatch?.value ?: get("year")
        var name = if (yearMatch != null) profileLine.substringBefore(year).trim() else get("name")
        name = name.removePrefix("Software").trim()
        val club = profileLine.substringAfter("POR - Portugal", "").trim()
        val inferredSex = if (normalizeAscii(name).contains("constanca")) "F" else get("sex").ifBlank { "F" }
        prefs.edit().putString("name", name).putString("year", year).putString("country", "Portugal").putString("club", club).putString("sex", inferredSex).apply()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PDF && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.openInputStream(uri).use { input ->
                    if (input == null) throw IllegalStateException("Não foi possível abrir o PDF")
                    val document = PDDocument.load(input)
                    val text = PDFTextStripper().getText(document)
                    document.close()
                    val count = importSwimrankingsPdfText(text)
                    toast(if (count > 0) "Importados $count tempos do PDF." else "Não foram encontrados recordes pessoais no formato Swimrankings.")
                    showTempos()
                }
            } catch (e: Exception) { toast("Erro ao importar PDF: ${e.message}") }
        }
    }

    private fun calculateCategory(year: Int?, seasonStart: Int, sex: String): String {
        if (year == null) return "Por definir"
        val age = seasonStart - year
        return if (sex.uppercase(Locale.ROOT).startsWith("F")) when (age) {
            13 -> "Infantil B Feminino"; 14 -> "Infantil A Feminino"; 15 -> "Juvenil B Feminino"; 16 -> "Juvenil A Feminino"; 17 -> "Júnior Feminino 1.º ano"; 18 -> "Júnior Feminino 2.º ano"; else -> if (age >= 19) "Sénior Feminino" else "Por definir"
        } else when (age) {
            14 -> "Infantil B Masculino"; 15 -> "Infantil A Masculino"; 16 -> "Juvenil B Masculino"; 17 -> "Juvenil A Masculino"; 18 -> "Júnior Masculino 1.º ano"; 19 -> "Júnior Masculino 2.º ano"; else -> if (age >= 20) "Sénior Masculino" else "Por definir"
        }
    }

    private fun seedOfficialTac(): MutableMap<String, String> {
        val m = mutableMapOf<String, String>()
        fun add(pool: String, event: String, time: String) { m[tacKey(event, pool)] = time }
        // Júnior Feminino 1.º ano — tabela fornecida pelo utilizador
        listOf(
            Triple("25m", "50 Livres", "28.71"), Triple("25m", "100 Livres", "1:01.62"), Triple("25m", "200 Livres", "2:12.93"), Triple("25m", "400 Livres", "4:36.72"), Triple("25m", "800 Livres", "9:18.62"), Triple("25m", "1500 Livres", "18:28.74"),
            Triple("25m", "50 Costas", "32.43"), Triple("25m", "100 Costas", "1:08.45"), Triple("25m", "200 Costas", "2:29.70"), Triple("25m", "50 Bruços", "36.23"), Triple("25m", "100 Bruços", "1:17.71"), Triple("25m", "200 Bruços", "2:48.61"),
            Triple("25m", "50 Mariposa", "30.62"), Triple("25m", "100 Mariposa", "1:07.50"), Triple("25m", "200 Mariposa", "2:30.99"), Triple("25m", "100 Estilos", "1:10.64"), Triple("25m", "200 Estilos", "2:30.08"), Triple("25m", "400 Estilos", "5:17.26")
        ).forEach { add(it.first, it.second, it.third) }
        return m
    }

    private fun loadTacMap(): Map<String, String> {
        val raw = get("tacs")
        if (raw.isBlank()) return seedOfficialTac()
        val map = mutableMapOf<String, String>()
        raw.split(";;").forEach { row -> val p = row.split("|"); if (p.size >= 2) map[p[0]] = p[1] }
        return map
    }

    private fun saveTacMap(map: Map<String, String>) { prefs.edit().putString("tacs", map.entries.joinToString(";;") { "${it.key}|${it.value}" }).apply() }
    private fun findTac(event: String, pool: String): String = loadTacMap()[tacKey(event, pool)] ?: ""
    private fun tacKey(event: String, pool: String): String = "${normalizePool(pool)}#${normalizeEvent(event)}"

    private fun saveTimes(times: List<SwimTime>) { prefs.edit().putString("times", encodeTimes(selectBest(times))).apply() }
    private fun loadTimes(): List<SwimTime> = decodeTimes(get("times"))
    private fun loadPreviousTimes(): List<SwimTime> = decodeTimes(get("previous_times"))
    private fun encodeTimes(times: List<SwimTime>): String = times.joinToString(";;") { listOf(it.event, it.pool, it.time, it.date, it.city, it.source).joinToString("|") { v -> v.replace("|", " ").replace(";;", " ") } }
    private fun decodeTimes(raw: String): List<SwimTime> = if (raw.isBlank()) emptyList() else raw.split(";;").mapNotNull { val p = it.split("|"); if (p.size >= 6) SwimTime(p[0], p[1], p[2], p[3], p[4], p[5]) else null }
    private fun selectBest(times: List<SwimTime>): List<SwimTime> = times.groupBy { "${it.pool}|${it.event}" }.mapNotNull { it.value.minByOrNull { t -> timeSeconds(t.time) } }.sortedWith(compareBy({ if (it.pool == "25m") 0 else 1 }, { strokeOrder(it.event) }, { distanceOf(it.event) }))

    private fun athleteSummary(): String = "${loadTimes().size} recordes importados.\n${closestTarget()}"
    private fun summaryTop(): String = "${get("name").ifBlank { "SwimTrack" }} • ${loadTimes().size} tempos"
    private fun closestTarget(): String {
        var best: Pair<SwimTime, Double>? = null
        loadTimes().forEach { t -> val tac = findTac(t.event, t.pool); if (tac.isNotBlank()) { val d = timeSeconds(t.time) - timeSeconds(tac); if (d > 0 && (best == null || d < best!!.second)) best = t to d } }
        return best?.let { "Mais próxima: ${it.first.event} ${it.first.pool}, faltam %.2f s".format(Locale.US, it.second) } ?: "Sem objetivo TAC por atingir."
    }

    private fun normalizeStroke(value: String): String = when {
        value.contains("liv", true) -> "Livres"; value.contains("cost", true) -> "Costas"; value.contains("bru", true) -> "Bruços"; value.contains("mar", true) -> "Mariposa"; value.contains("est", true) -> "Estilos"; else -> ""
    }
    private fun normalizeEvent(value: String): String { val d = Regex("\\d+").find(value)?.value ?: return ""; val s = normalizeStroke(value); return if (s.isBlank()) "" else "$d $s" }
    private fun normalizePool(value: String): String = when { value.contains("25") -> "25m"; value.contains("50") -> "50m"; else -> "" }
    private fun normalizeTime(value: String): String { var v = value.trim().replace(",", ".").replace(" ", ""); if (v.startsWith("00:")) v = v.removePrefix("00:"); return if (Regex("^\\d{1,2}(:\\d{2})?\\.\\d{2}$").matches(v)) v else "" }
    private fun normalizeAscii(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase(Locale.ROOT)
    private fun distanceOf(event: String): Int = Regex("\\d+").find(event)?.value?.toIntOrNull() ?: 9999
    private fun strokeOrder(event: String): Int = when { event.contains("Livres") -> 1; event.contains("Costas") -> 2; event.contains("Bruços") -> 3; event.contains("Mariposa") -> 4; event.contains("Estilos") -> 5; else -> 9 }
    private fun timeSeconds(value: String): Double = try { val v = value.replace(",", "."); if (v.contains(":")) { val p = v.split(":"); p[0].toDouble() * 60 + p[1].toDouble() } else v.toDouble() } catch (_: Exception) { Double.MAX_VALUE }
    private fun statusText(time: String, tac: String): String { if (tac.isBlank()) return "TAC por definir"; val d = timeSeconds(time) - timeSeconds(tac); return if (d <= 0) "✅ QUALIFICADA • margem %.2f s".format(Locale.US, -d) else "❌ Faltam %.2f s".format(Locale.US, d) }

    private fun exportPdf() {
        try {
            val pdf = PdfDocument(); val paint = Paint().apply { color = Color.BLACK; textSize = 11f }
            var pageNo = 1; var y = 45f
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
            var canvas = page.canvas
            fun line(text: String, bold: Boolean = false) {
                if (y > 805) { pdf.finishPage(page); pageNo++; page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create()); canvas = page.canvas; y = 45f }
                paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                canvas.drawText(text.take(95), 35f, y, paint); y += 17f
            }
            paint.textSize = 18f; line("SWIMTRACK — RECORDES E TAC", true); paint.textSize = 11f
            line("Atleta: ${get("name")}"); line("Clube: ${get("club")}"); line("Escalão: ${calculateCategory(get("year").toIntOrNull(), get("season_start").toIntOrNull() ?: 2026, get("sex"))}"); y += 8
            loadTimes().forEach { t -> line("${t.event} ${t.pool} | ${t.time} | TAC ${findTac(t.event, t.pool).ifBlank { "-" }} | ${statusText(t.time, findTac(t.event, t.pool))}") }
            pdf.finishPage(page)
            val file = File(cacheDir, "SwimTrack_Relatorio.pdf"); FileOutputStream(file).use { pdf.writeTo(it) }; pdf.close()
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            startActivity(Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
        } catch (e: Exception) { toast("Erro ao gerar PDF: ${e.message}") }
    }

    private fun shareSummary() {
        val text = buildString { append("🏊 SwimTrack\n${get("name")}\n${calculateCategory(get("year").toIntOrNull(), get("season_start").toIntOrNull() ?: 2026, get("sex"))}\n\n"); loadTimes().forEach { append("${it.event} ${it.pool}: ${it.time} — ${statusText(it.time, findTac(it.event, it.pool))}\n") } }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Partilhar"))
    }

    private fun get(key: String): String = prefs.getString(key, "") ?: ""
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun confirm(message: String, action: () -> Unit) { AlertDialog.Builder(this).setMessage(message).setPositiveButton("Sim") { _, _ -> action() }.setNegativeButton("Não", null).show() }

    private fun label(text: String, textColor: Int, background: Int, size: Float): TextView = TextView(this).apply { this.text = text; textSize = size; setTextColor(textColor); setBackgroundColor(background); gravity = Gravity.CENTER; setTypeface(Typeface.DEFAULT_BOLD); setPadding(14, 14, 14, 14) }
    private fun tab(text: String): TextView = label(text, white, card, 12f)
    private fun section(text: String): TextView = label(text, yellow, bg, 20f).apply { setPadding(8, 28, 8, 12) }
    private fun subSection(text: String): TextView = label(text, soft, bg, 16f).apply { gravity = Gravity.START }
    private fun input(hint: String, value: String = ""): EditText = EditText(this).apply { this.hint = hint; setText(value); textSize = 16f; setTextColor(white); setHintTextColor(soft); setBackgroundColor(card); setPadding(20, 16, 20, 16); layoutParams = marginParams(0, 0, 0, 14) }
    private fun inputMulti(hint: String): EditText = input(hint).apply { minLines = 10; gravity = Gravity.TOP }
    private fun button(text: String, action: () -> Unit): Button = Button(this).apply { this.text = text; textSize = 15f; setTypeface(Typeface.DEFAULT_BOLD); setTextColor(white); setBackgroundColor(blue); setOnClickListener { action() }; layoutParams = marginParams(0, 0, 0, 14) }
    private fun smallButton(text: String, action: () -> Unit): Button = Button(this).apply { this.text = text; textSize = 11f; setTextColor(white); setBackgroundColor(blue); setOnClickListener { action() } }
    private fun info(title: String, body: String): LinearLayout = infoColor(title, body, card)
    private fun infoColor(title: String, body: String, color: Int): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16); setBackgroundColor(color); addView(TextView(this@MainActivity).apply { text = title; textSize = 18f; setTextColor(white); setTypeface(Typeface.DEFAULT_BOLD) }); addView(TextView(this@MainActivity).apply { text = body.ifBlank { "Por definir" }; textSize = 15f; setTextColor(white); setPadding(0, 8, 0, 0) }); layoutParams = marginParams(0, 0, 0, 12) }
    private fun marginParams(l: Int, t: Int, r: Int, b: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(l, t, r, b) }
}
