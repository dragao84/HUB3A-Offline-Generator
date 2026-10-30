
package hr.hub3a;

import android.app.Activity;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.pdf417.encoder.Compaction;
import java.util.*;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

public class MainActivity extends Activity {
    String[] keys = {
        "Iznos (EUR)", "Primatelj", "Ulica primatelja",
        "Pošta i mjesto primatelja", "IBAN primatelja",
        "Model", "Poziv na broj", "Šifra namjene",
        "Opis plaćanja", "Platitelj (opcionalno)",
        "Ulica platitelja", "Pošta i mjesto platitelja"
    };

    EditText[] fields = new EditText[keys.length];
    Bitmap current;
    JSONObject addressBook = new JSONObject();
    Spinner payerSpinner, recipientSpinner;
    boolean refreshing = false;

    static final String[] PAYER_KEYS = {
        "payer", "payer_street", "payer_city"
    };
    static final String[] RECIPIENT_KEYS = {
        "recipient", "recipient_street", "recipient_city",
        "iban", "model", "reference"
    };
    static final int[] PAYER_INDICES = {9, 10, 11};
    static final int[] RECIPIENT_INDICES = {1, 2, 3, 4, 5, 6};

    int navy = Color.rgb(21,36,60);
    int blue = Color.rgb(33,91,227);
    int muted = Color.rgb(98,113,138);
    int background = Color.rgb(244,247,251);

    int dp(float value) {
        return (int)(getResources().getDisplayMetrics().density * value + .5f);
    }

    TextView text(String label, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    void field(LinearLayout parent, int i) {
        TextView label = text(keys[i], 13, navy);
        label.setTypeface(null, 1);
        parent.addView(label);

        EditText e = new EditText(this);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setPadding(dp(12), dp(7), dp(12), dp(7));
        e.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(blue)
        );

        if (i == 0)
            e.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            );

        if (i == 5) e.setText("HR99");
        fields[i] = e;

        LinearLayout.LayoutParams ep =
            new LinearLayout.LayoutParams(-1, dp(55));
        ep.bottomMargin = dp(15);
        parent.addView(e, ep);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(navy);
        getWindow().setNavigationBarColor(background);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);

        LinearLayout page = column();
        page.setPadding(dp(19), dp(18), dp(19), dp(28));
        scroll.addView(page);

        TextView heading = text("HUB-3A  /  PDF417", 25, navy);
        heading.setTypeface(null, 1);
        page.addView(heading);

        TextView tagline = text(
            "Offline generator barkoda za račune i ponude", 13, muted
        );
        LinearLayout.LayoutParams tp =
            new LinearLayout.LayoutParams(-1, -2);
        tp.bottomMargin = dp(21);
        page.addView(tagline, tp);

        LinearLayout card = column();
        card.setPadding(dp(17), dp(17), dp(17), dp(12));
        card.setBackgroundColor(Color.WHITE);
        page.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = text("Podaci za plaćanje", 19, navy);
        sub.setTypeface(null, 1);
        LinearLayout.LayoutParams sp =
            new LinearLayout.LayoutParams(-1, -2);
        sp.bottomMargin = dp(16);
        card.addView(sub, sp);

        for (int i = 0; i < 9; i++) field(card, i);

        Button toggle = new Button(this);
        toggle.setAllCaps(false);
        toggle.setText("＋  Podaci platitelja (opcionalno)");
        toggle.setTextColor(blue);
        toggle.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                Color.rgb(234,240,255)
            )
        );

        LinearLayout.LayoutParams top =
            new LinearLayout.LayoutParams(-1, dp(52));
        top.topMargin = dp(14);
        page.addView(toggle, top);

        LinearLayout extra = column();
        extra.setPadding(dp(17), dp(16), dp(17), dp(10));
        extra.setBackgroundColor(Color.WHITE);
        extra.setVisibility(View.GONE);

        for (int i = 9; i < 12; i++) field(extra, i);
        page.addView(extra);

        toggle.setOnClickListener(v -> {
            boolean visible = extra.getVisibility() == View.VISIBLE;
            extra.setVisibility(visible ? View.GONE : View.VISIBLE);
            toggle.setText(
                visible ? "＋  Podaci platitelja (opcionalno)"
                        : "－  Sakrij podatke platitelja"
            );
        });

        loadFields();
        readContacts();

        LinearLayout book = column();
        book.setPadding(dp(17), dp(16), dp(17), dp(16));
        book.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams bp =
            new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(14);
        page.addView(book, 2, bp);

        TextView bookTitle = text(
            "Adresari platitelja i primatelja", 19, navy
        );
        bookTitle.setTypeface(null, 1);
        book.addView(bookTitle);

        recipientSpinner = addContactRow(
            book, "Primatelji", "recipients",
            RECIPIENT_KEYS, RECIPIENT_INDICES
        );
        payerSpinner = addContactRow(
            book, "Platitelji", "payers",
            PAYER_KEYS, PAYER_INDICES
        );
        refreshContactMenus();

        LinearLayout transfer = new LinearLayout(this);
        transfer.setOrientation(LinearLayout.HORIZONTAL);
        book.addView(transfer);

        Button export = contactButton("IZVEZI JSON");
        Button imp = contactButton("UVEZI JSON");
        transfer.addView(
            export, new LinearLayout.LayoutParams(0, dp(48), 1)
        );
        transfer.addView(
            imp, new LinearLayout.LayoutParams(0, dp(48), 1)
        );

        export.setOnClickListener(v -> {
            Intent in = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            in.addCategory(Intent.CATEGORY_OPENABLE);
            in.setType("application/json");
            in.putExtra(Intent.EXTRA_TITLE, "HUB3A_adresar.json");
            startActivityForResult(in, 103);
        });

        imp.setOnClickListener(v -> {
            Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            in.addCategory(Intent.CATEGORY_OPENABLE);
            in.setType("application/json");
            startActivityForResult(in, 102);
        });

        Button generate = new Button(this);
        generate.setText("SPREMI PDF417 KAO PNG");
        generate.setTextColor(Color.WHITE);
        generate.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(blue)
        );

        LinearLayout.LayoutParams gp =
            new LinearLayout.LayoutParams(-1, dp(57));
        gp.topMargin = dp(20);
        page.addView(generate, gp);

        Button remember = new Button(this);
        remember.setText("ZAPAMTI PODATKE");
        remember.setTextColor(blue);
        remember.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                Color.rgb(234,240,255)
            )
        );

        LinearLayout.LayoutParams rp =
            new LinearLayout.LayoutParams(-1, dp(52));
        rp.topMargin = dp(9);
        page.addView(remember, rp);

        remember.setOnClickListener(v -> {
            saveFields();
            Toast.makeText(
                this, "Podaci su spremljeni na uređaju",
                Toast.LENGTH_LONG
            ).show();
        });

        TextView note = text(
            "OFFLINE  •  Podaci ostaju na uređaju. Prije plaćanja provjerite očitane podatke.",
            12, muted
        );
        LinearLayout.LayoutParams np =
            new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(17);
        page.addView(note, np);

        generate.setOnClickListener(v -> {
            try {
                current = encode(payload());
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/png");
                intent.putExtra(Intent.EXTRA_TITLE, "HUB3A_barkod.png");
                startActivityForResult(intent, 101);
            } catch (Exception ex) {
                new AlertDialog.Builder(this)
                    .setTitle("Provjera podataka")
                    .setMessage(ex.getMessage())
                    .setPositiveButton("U redu", null)
                    .show();
            }
        });

        setContentView(scroll);
    }

    Button contactButton(String label) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(blue);
        b.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                Color.rgb(234,240,255)
            )
        );
        return b;
    }

    JSONArray items(String section) {
        JSONArray arr = addressBook.optJSONArray(section);
        return arr == null ? new JSONArray() : arr;
    }

    void readContacts() {
        try {
            String raw = getSharedPreferences(
                "hub3a_contacts", MODE_PRIVATE
            ).getString("data", "");

            addressBook = raw.isEmpty()
                ? new JSONObject() : new JSONObject(raw);

            if (addressBook.optInt("version", 1) != 1)
                throw new Exception("Nepodržana verzija adresara");

            if (addressBook.optJSONArray("payers") == null)
                addressBook.put("payers", new JSONArray());

            if (addressBook.optJSONArray("recipients") == null)
                addressBook.put("recipients", new JSONArray());

            addressBook.put("version", 1);
        } catch (Exception ex) {
            addressBook = new JSONObject();
            try {
                addressBook.put("version", 1);
                addressBook.put("payers", new JSONArray());
                addressBook.put("recipients", new JSONArray());
            } catch (Exception ignored) {}

            new AlertDialog.Builder(this)
                .setMessage(
                    "Adresar nije moguće učitati: " + ex.getMessage()
                )
                .setPositiveButton("U redu", null)
                .show();
        }
    }

    void persistContacts() {
        getSharedPreferences("hub3a_contacts", MODE_PRIVATE)
            .edit()
            .putString("data", addressBook.toString())
            .apply();
    }

    void refreshContactMenus() {
        refreshing = true;
        for (String section : new String[]{"recipients", "payers"}) {
            Spinner spinner = section.equals("payers")
                ? payerSpinner : recipientSpinner;

            ArrayList<String> names = new ArrayList<>();
            names.add("— Odaberi —");

            JSONArray arr = items(section);
            String key = section.equals("payers")
                ? "payer" : "recipient";

            for (int i = 0; i < arr.length(); i++) {
                names.add(
                    (i + 1) + ". " +
                    arr.optJSONObject(i).optString(key)
                );
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, names
            );
            adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            );
            spinner.setAdapter(adapter);
        }
        refreshing = false;
    }

    Spinner addContactRow(
        LinearLayout parent,
        String caption,
        String section,
        String[] keys,
        int[] indices
    ) {
        TextView label = text(caption, 14, navy);
        label.setPadding(0, dp(12), 0, dp(3));
        parent.addView(label);

        Spinner spinner = new Spinner(this);
        parent.addView(spinner);

        spinner.setOnItemSelectedListener(
            new android.widget.AdapterView.OnItemSelectedListener() {
                public void onNothingSelected(
                    android.widget.AdapterView<?> p
                ) {}

                public void onItemSelected(
                    android.widget.AdapterView<?> p,
                    View v, int position, long id
                ) {
                    if (!refreshing && position > 0) {
                        JSONObject obj = items(section)
                            .optJSONObject(position - 1);

                        if (obj != null)
                            for (int i = 0; i < keys.length; i++)
                                fields[indices[i]].setText(
                                    obj.optString(keys[i], "")
                                );
                    }
                }
            }
        );

        LinearLayout buttons = new LinearLayout(this);
        parent.addView(buttons);

        Button save = contactButton("Dodaj / Ažuriraj");
        Button del = contactButton("Obriši");

        buttons.addView(
            save, new LinearLayout.LayoutParams(0, dp(48), 2)
        );
        buttons.addView(
            del, new LinearLayout.LayoutParams(0, dp(48), 1)
        );

        save.setOnClickListener(v -> {
            try {
                JSONObject entry = new JSONObject();

                for (int i = 0; i < keys.length; i++)
                    entry.put(keys[i], val(indices[i]));

                if (val(indices[0]).isEmpty())
                    throw new Exception(
                        "Unesite naziv prije spremanja."
                    );

                JSONArray arr = items(section);
                int index = spinner.getSelectedItemPosition() - 1;

                if (index >= 0 && index < arr.length())
                    arr.put(index, entry);
                else
                    arr.put(entry);

                addressBook.put(section, arr);
                persistContacts();
                refreshContactMenus();

                Toast.makeText(
                    this, "Adresar spremljen", Toast.LENGTH_SHORT
                ).show();

            } catch (Exception ex) {
                error(ex.getMessage());
            }
        });

        del.setOnClickListener(v -> {
            int index = spinner.getSelectedItemPosition() - 1;
            if (index < 0) return;

            new AlertDialog.Builder(this)
                .setTitle("Obrisati zapis?")
                .setPositiveButton("Obriši", (dialog, which) -> {
                    try {
                        JSONArray arr = items(section);
                        arr.remove(index);
                        addressBook.put(section, arr);
                        persistContacts();
                        refreshContactMenus();
                    } catch (Exception ex) {
                        error(ex.getMessage());
                    }
                })
                .setNegativeButton("Odustani", null)
                .show();
        });

        return spinner;
    }

    void error(String message) {
        new AlertDialog.Builder(this)
            .setTitle("Adresar")
            .setMessage(message)
            .setPositiveButton("U redu", null)
            .show();
    }

    JSONObject validatedContacts(JSONObject source) throws Exception {
        if (source.optInt("version", -1) != 1)
            throw new Exception("Nepodržana verzija adresara");

        JSONObject clean = new JSONObject();
        clean.put("version", 1);

        for (String section : new String[]{"payers", "recipients"}) {
            JSONArray arr = source.optJSONArray(section);

            if (arr == null || arr.length() > 1000)
                throw new Exception("Neispravan popis: " + section);

            JSONArray out = new JSONArray();

            String[] keys = section.equals("payers")
                ? PAYER_KEYS : RECIPIENT_KEYS;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.optJSONObject(i);
                if (row == null)
                    throw new Exception("Neispravan zapis");

                JSONObject copy = new JSONObject();

                for (String key : keys) {
                    Object value = row.opt(key);

                    if (value != null && !(value instanceof String))
                        throw new Exception(
                            "Neispravno polje: " + key
                        );

                    String s = row.optString(key, "");
                    if (s.length() > 300)
                        throw new Exception("Predugo polje");

                    copy.put(key, s);
                }

                if (copy.optString(keys[0]).trim().isEmpty())
                    throw new Exception("Zapis bez naziva");

                out.put(copy);
            }

            clean.put(section, out);
        }

        return clean;
    }

    void saveFields() {
        SharedPreferences.Editor editor =
            getSharedPreferences(
                "hub3a_fields", MODE_PRIVATE
            ).edit();

        for (int i = 0; i < fields.length; i++)
            editor.putString(
                "field_" + i,
                fields[i].getText().toString()
            );

        editor.apply();
    }

    void loadFields() {
        SharedPreferences saved =
            getSharedPreferences("hub3a_fields", MODE_PRIVATE);

        for (int i = 0; i < fields.length; i++)
            if (saved.contains("field_" + i))
                fields[i].setText(
                    saved.getString("field_" + i, "")
                );
    }

    String val(int index) {
        return fields[index].getText().toString().trim();
    }

    String payload() {
        BigDecimal amt;

        try {
            amt = new BigDecimal(val(0).replace(",", "."));
            if (amt.signum() <= 0 || amt.scale() > 2)
                throw new Exception();
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                "Iznos mora biti pozitivan, najviše dvije decimale."
            );
        }

        String iban = val(4).replaceAll("\\s+", "")
                            .toUpperCase(Locale.ROOT);

        if (!iban.matches("HR[0-9]{19}") || !ibanValid(iban))
            throw new IllegalArgumentException(
                "Neispravan hrvatski IBAN."
            );

        String model = val(5).toUpperCase(Locale.ROOT);
        if (!model.matches("HR[0-9]{2}"))
            throw new IllegalArgumentException(
                "Model mora biti HRxx."
            );

        if (val(1).isEmpty() || val(8).isEmpty())
            throw new IllegalArgumentException(
                "Primatelj i opis plaćanja su obavezni."
            );

        String[] lines = {
            "HRVHUB30", "EUR",
            String.format(
                Locale.ROOT, "%015d",
                amt.movePointRight(2).longValueExact()
            ),
            val(9), val(10), val(11),
            val(1), val(2), val(3),
            iban, model, val(6), val(7), val(8)
        };

        int[] limits = {
            8, 3, 15, 30, 27, 27, 25,
            25, 27, 21, 4, 22, 4, 35
        };

        String allowed =
            "[0-9A-Za-zČĆĐŠŽčćđšž ,.:+?'()/\\-]*";

        StringBuilder b = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].length() > limits[i] ||
                !lines[i].matches(allowed))
                throw new IllegalArgumentException(
                    "Polje " + (i + 1) +
                    " ima neispravan znak ili duljinu (max " +
                    limits[i] + ")."
                );

            b.append(lines[i]).append('\n');
        }

        return b.toString();
    }

    boolean ibanValid(String iban) {
        String t = iban.substring(4) + "1727" + iban.substring(2, 4);
        int rem = 0;

        for (int i = 0; i < t.length(); i++)
            rem = (rem * 10 + (t.charAt(i) - '0')) % 97;

        return rem == 1;
    }

    Bitmap encode(String data) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();

        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, 4);
        hints.put(EncodeHintType.PDF417_COMPACT, false);
        hints.put(EncodeHintType.MARGIN, 16);
        hints.put(
            EncodeHintType.PDF417_COMPACTION,
            Compaction.BYTE
        );
        hints.put(
            EncodeHintType.PDF417_DIMENSIONS,
            new com.google.zxing.pdf417.encoder.Dimensions(
                9, 9, 3, 90
            )
        );

        BitMatrix bits = new MultiFormatWriter().encode(
            data, BarcodeFormat.PDF_417, 1100, 550, hints
        );

        Bitmap bitmap = Bitmap.createBitmap(
            bits.getWidth(), bits.getHeight(),
            Bitmap.Config.ARGB_8888
        );

        for (int y = 0; y < bits.getHeight(); y++)
            for (int x = 0; x < bits.getWidth(); x++)
                bitmap.setPixel(
                    x, y,
                    bits.get(x, y) ? Color.BLACK : Color.WHITE
                );

        return bitmap;
    }

    @Override
    protected void onActivityResult(
        int request, int result, Intent intent
    ) {
        super.onActivityResult(request, result, intent);

        if (result == RESULT_OK &&
            intent != null && request == 102) {

            try (
                InputStream input =
                    getContentResolver().openInputStream(intent.getData());
                ByteArrayOutputStream buffer =
                    new ByteArrayOutputStream()
            ) {
                if (input == null)
                    throw new Exception("Datoteka nije dostupna");

                byte[] chunk = new byte[4096];
                int n;

                while ((n = input.read(chunk)) != -1) {
                    if (buffer.size() + n > 1048576)
                        throw new Exception("Datoteka je prevelika");
                    buffer.write(chunk, 0, n);
                }

                JSONObject imported = validatedContacts(
                    new JSONObject(
                        new String(
                            buffer.toByteArray(),
                            StandardCharsets.UTF_8
                        )
                    )
                );

                new AlertDialog.Builder(this)
                    .setTitle("Uvoz adresara")
                    .setMessage(
                        "Zamijeniti postojeće lokalne adresare uvezenima?"
                    )
                    .setPositiveButton(
                        "Zamijeni",
                        (dialog, which) -> {
                            addressBook = imported;
                            persistContacts();
                            refreshContactMenus();
                        }
                    )
                    .setNegativeButton("Odustani", null)
                    .show();

            } catch (Exception ex) {
                error(ex.getMessage());
            }
            return;
        }

        if (result == RESULT_OK &&
            intent != null && request == 103) {

            try (
                OutputStream out =
                    getContentResolver().openOutputStream(intent.getData())
            ) {
                if (out == null)
                    throw new Exception("Nije moguće spremiti adresar");

                out.write(
                    addressBook.toString(2)
                        .getBytes(StandardCharsets.UTF_8)
                );

                Toast.makeText(
                    this, "Adresar izvezen",
                    Toast.LENGTH_LONG
                ).show();

            } catch (Exception ex) {
                error(ex.getMessage());
            }
            return;
        }

        if (request == 101 &&
            result == RESULT_OK &&
            intent != null) {

            try (
                OutputStream out =
                    getContentResolver().openOutputStream(intent.getData())
            ) {
                if (out == null ||
                    !current.compress(
                        Bitmap.CompressFormat.PNG, 100, out
                    ))
                    throw new Exception("Spremanje nije uspjelo");

                Toast.makeText(
                    this, "PNG spremljen",
                    Toast.LENGTH_LONG
                ).show();

            } catch (Exception ex) {
                Toast.makeText(
                    this, ex.getMessage(),
                    Toast.LENGTH_LONG
                ).show();
            }
        }
    }
}
