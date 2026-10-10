package com.bnet.vinhistory;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private EditText vin;
    private TextView result;
    private String report = "";
    private LinearLayout layout;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 32, 24, 24);
        TextView title = new TextView(this);
        title.setText("VIN HISTORY CHECKER");
        title.setTextSize(23);
        title.setTextColor(Color.rgb(20, 50, 80));
        layout.addView(title);
        vin = new EditText(this);
        vin.setHint("VIN (17 caractères)");
        vin.setSingleLine(true);
        vin.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.AllCaps(), new android.text.InputFilter.LengthFilter(17)});
        layout.addView(vin);
        addButton("DÉCODER VIN", v -> lookup());
        addButton("EXPORTER RAPPORT PDF", v -> exportPdf());
        addButton("ENCHÈRES COPART / IAAI", v -> open("https://www.google.com/search?q=" + Uri.encode(vin.getText().toString() + " Copart IAAI")));
        addButton("RAPPELS NHTSA", v -> open("https://www.nhtsa.gov/recalls?vin=" + vin.getText().toString()));
        addButton("VÉRIFICATION VOL NICB", v -> open("https://www.nicb.org/vincheck"));
        result = new TextView(this);
        result.setTextSize(16);
        result.setText("Saisissez un VIN. Décodage officiel NHTSA. Historique d'accidents et enchères : sources externes.");
        ScrollView scroll = new ScrollView(this);
        scroll.addView(result);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
    }
    private void addButton(String name, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(name);
        b.setOnClickListener(listener);
        layout.addView(b);
    }
    private void open(String address) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(address)));
    }
    private void lookup() {
        String id = vin.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
        if (!id.matches("[A-HJ-NPR-Z0-9]{17}")) {
            Toast.makeText(this, "VIN invalide : 17 caractères, sans I, O ou Q", Toast.LENGTH_LONG).show();
            return;
        }
        result.setText("Recherche en cours…");
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/" + id + "?format=json").openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                String json;
                try (java.io.InputStream input = connection.getInputStream()) {
                    json = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                JSONObject item = new JSONObject(json).getJSONArray("Results").getJSONObject(0);
                String text = "VIN : " + id + "\nMarque : " + field(item,"Make") + "\nModèle : " + field(item,"Model") +
                    "\nAnnée : " + field(item,"ModelYear") + "\nMoteur : " + field(item,"EngineModel") +
                    "\nCylindrée (L) : " + field(item,"DisplacementL") + "\nCarburant : " + field(item,"FuelTypePrimary") +
                    "\nPays d'assemblage : " + field(item,"PlantCountry") +
                    "\n\nSource : NHTSA vPIC. Ce décodage ne certifie pas les accidents, le kilométrage ou le vol.";
                report = text;
                runOnUiThread(() -> result.setText(text));
            } catch (Exception e) {
                runOnUiThread(() -> result.setText("Erreur réseau : " + e.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }
    private String field(JSONObject item, String key) {
        String value = item.optString(key, "").trim();
        return value.isEmpty() ? "Non renseigné" : value;
    }
    private void exportPdf() {
        if (report.isEmpty()) {
            Toast.makeText(this, "Décoder un VIN d'abord", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            PdfDocument pdf = new PdfDocument();
            PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, 1).create());
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setTextSize(12f);
            String[] lines = report.split("\n");
            for (int i = 0; i < lines.length; i++) page.getCanvas().drawText(lines[i], 30, 50 + i * 25, paint);
            pdf.finishPage(page);
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            File file = new File(dir, "VIN_Report_" + vin.getText().toString() + ".pdf");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) { pdf.writeTo(out); }
            pdf.close();
            Toast.makeText(this, "PDF enregistré : " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erreur PDF : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
