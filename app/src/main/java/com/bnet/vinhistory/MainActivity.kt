package com.bnet.vinhistory

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.pdf.PdfDocument
import android.graphics.Color
import android.os.Environment
import android.widget.*
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var vin: EditText
    private lateinit var output: TextView
    private var report = ""
    private val executor = Executors.newSingleThreadExecutor()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 30, 24, 24) }
        root.addView(TextView(this).apply { text = "VIN HISTORY CHECKER"; textSize = 24f; setTextColor(Color.rgb(20,40,70)) })
        vin = EditText(this).apply { hint = "VIN : 17 caractères"; isSingleLine = true; filters = arrayOf(android.text.InputFilter.AllCaps(), android.text.InputFilter.LengthFilter(17)) }
        root.addView(vin)
        output = TextView(this).apply { text = "Décodage VIN via NHTSA. Les historiques d'accidents et d'enchères nécessitent des fournisseurs externes."; textSize = 16f }
        fun button(label: String, action: () -> Unit) { root.addView(Button(this).apply { text = label; setOnClickListener { action() } }) }
        button("RECHERCHER VIN") { lookup() }
        button("HISTORIQUE LOCAL") { output.text = getPreferences(0).getString("history", "Aucun VIN recherché") }
        button("EXPORTER PDF") { exportPdf() }
        button("RECHERCHE ENCHÈRES") { open("https://www.google.com/search?q=" + vin.text.toString() + "+Copart+IAAI") }
        button("VÉRIFIER RAPPELS") { open("https://www.nhtsa.gov/recalls?vin=" + vin.text.toString()) }
        button("VÉRIFIER VOL (NICB)") { open("https://www.nicb.org/vincheck") }
        button("SOURCES D'HISTORIQUE") { open("https://www.carfax.com/") }
        root.addView(ScrollView(this).apply { addView(output) }, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }
    private fun open(url: String) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    private fun vinCheckDigitValid(v: String): Boolean {
        if (v.length != 17 || !v[8].isDigit() && v[8] != 'X') return false
        val weights = intArrayOf(8,7,6,5,4,3,2,10,0,9,8,7,6,5,4,3,2)
        val map = "0123456789.ABCDEFGH..JKLMN.P.R..STUVWXYZ"
        var sum = 0
        for (i in v.indices) {
            val ch = v[i]
            val value = if (ch.isDigit()) ch.digitToInt() else when(ch) {
                'A','J' -> 1; 'B','K','S' -> 2; 'C','L','T' -> 3; 'D','M','U' -> 4;
                'E','N','V' -> 5; 'F','W' -> 6; 'G','P','X' -> 7; 'H','Y' -> 8; 'R','Z' -> 9;
                else -> return false
            }
            sum += value * weights[i]
        }
        val expected = sum % 11
        return v[8] == (if (expected == 10) 'X' else ('0' + expected))
    }
    private fun lookup() {
        val id = vin.text.toString().trim().uppercase()
        if (!id.matches(Regex("[A-HJ-NPR-Z0-9]{17}"))) { Toast.makeText(this, "VIN invalide : 17 caractères, sans I, O, Q", Toast.LENGTH_LONG).show(); return }
        output.text = "Recherche en cours…"
        executor.execute {
            try {
                val connection = URL("https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/" + id + "?format=json").openConnection() as HttpURLConnection
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val item = JSONObject(json).getJSONArray("Results").getJSONObject(0)
                fun field(key: String) = item.optString(key).ifBlank { "Non renseigné" }
                val text = "VIN : $id\nMarque : ${field("Make")}\nModèle : ${field("Model")}\nAnnée : ${field("ModelYear")}\nMoteur : ${field("EngineModel")}\nCylindrée (L) : ${field("DisplacementL")}\nCarburant : ${field("FuelTypePrimary")}\nPays : ${field("PlantCountry")}\n\nSource : NHTSA vPIC. Ce décodage ne prouve pas l'absence d'accidents, de vol ou de fraude kilométrique."
                report = text
                val prefs = getPreferences(0)
                prefs.edit().putString("history", (id + "\n" + prefs.getString("history", "")).take(5000)).apply()
                runOnUiThread { output.text = text }
            } catch (e: Exception) { runOnUiThread { output.text = "Erreur de connexion : ${e.message}" } }
        }
    }
    private fun exportPdf() {
        if (report.isBlank()) { Toast.makeText(this, "Recherchez un VIN d'abord", Toast.LENGTH_SHORT).show(); return }
        try {
            val pdf = PdfDocument()
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val paint = android.graphics.Paint().apply { textSize = 12f; color = Color.BLACK }
            report.split("\n").forEachIndexed { index, line -> page.canvas.drawText(line.take(85), 30f, 50f + index * 24f, paint) }
            pdf.finishPage(page)
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
            val file = File(dir, "VIN_Report_${vin.text}.pdf")
            file.outputStream().use { pdf.writeTo(it) }
            pdf.close()
            Toast.makeText(this, "PDF : ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this, "Erreur PDF : ${e.message}", Toast.LENGTH_LONG).show() }
    }
}
