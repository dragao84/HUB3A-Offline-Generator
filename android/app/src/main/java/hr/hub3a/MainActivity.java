
package hr.hub3a;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import android.content.res.ColorStateList;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.pdf417.encoder.Compaction;
import com.google.zxing.pdf417.encoder.Dimensions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int PNG_REQUEST = 101;
    private static final int IMPORT_REQUEST = 102;
    private static final int EXPORT_REQUEST = 103;

    private final String[] labels = {
        "Iznos (EUR)",
        "Primatelj",
        "Ulica primatelja",
        "Pošta i mjesto primatelja",
        "IBAN primatelja",
        "Model",
        "Poziv na broj",
        "Šifra namjene",
        "Opis plaćanja",
        "Platitelj (opcionalno)",
        "Ulica platitelja",
        "Pošta i mjesto platitelja"
    };

    private static final String[] PAYER_KEYS = {
        "payer", "payer_street", "payer_city"
    };

    private static final String[] RECIPIENT_KEYS = {
        "recipient", "recipient_street",
        "recipient_city", "iban", "model", "reference"
    };

    private static final String[] ACCOUNT_KEYS = {
        "label", "recipient", "recipient_street",
        "recipient_city", "iban", "model", "reference"
    };

    private static final String[] TEMPLATE_KEYS = {
        "label", "recipient", "recipient_street",
        "recipient_city", "iban", "model",
        "reference", "purpose_code", "description"
    };

    private static final int[] PAYER_INDICES = {9, 10, 11};
    private static final int[] RECIPIENT_INDICES = {
        1, 2, 3, 4, 5, 6
    };
    private static final int[] ACCOUNT_INDICES = {
        -1, 1, 2, 3, 4, 5, 6
    };
    private static final int[] TEMPLATE_INDICES = {
        -1, 1, 2, 3, 4, 5, 6, 7, 8
    };

    private final EditText[] fields = new EditText[12];
    private final Map<String, Spinner> selectors = new HashMap<>();

    private JSONObject addressBook;
    private Bitmap currentBarcode;
    private boolean refreshing = false;

    private final int navy = Color.rgb(21, 36, 60);
    private final int blue = Color.rgb(33, 91, 227);
    private final int muted = Color.rgb(98, 113, 138);
    private final int background = Color.rgb(244, 247, 251);

    private int dp(float value) {
        return (int) (
            getResources().getDisplayMetrics().density * value + 0.5f
        );
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value, boolean primary) {
        Button result = new Button(this);
        result.setText(value);
        result.setAllCaps(false);
        result.setTextColor(primary ? Color.WHITE : blue);
        result.setBackgroundTintList(
            ColorStateList.valueOf(
                primary ? blue : Color.rgb(234, 240, 255)
            )
        );
        return result;
    }

    private void addField(LinearLayout parent, int index) {
        TextView label = text(labels[index], 13, navy);
        label.setTypeface(null, 1);
        parent.addView(label);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setPadding(dp(12), dp(7), dp(12), dp(7));
        input.setBackgroundTintList(ColorStateList.valueOf(blue));

        if (index == 0) {
            input.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_FLAG_DECIMAL
            );
        }

        if (index == 5) input.setText("HR99");

        fields[index] = input;

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, dp(55));
        params.bottomMargin = dp(14);
        parent.addView(input, params);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(navy);
        getWindow().setNavigationBarColor(background);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(background);
        scroll.setFillViewport(true);

        LinearLayout page = column();
        page.setPadding(dp(18), dp(18), dp(18), dp(30));
        scroll.addView(page);

        TextView title = text("HUB-3A / PDF417", 25, navy);
        title.setTypeface(null, 1);
        page.addView(title);

        TextView subtitle = text(
            "Offline generator barkoda za račune i ponude",
            13, muted
        );
        page.addView(subtitle);

        LinearLayout paymentCard = column();
        paymentCard.setPadding(
            dp(16), dp(18), dp(16), dp(12)
        );
        paymentCard.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams paymentParams =
            new LinearLayout.LayoutParams(-1, -2);
        paymentParams.topMargin = dp(17);
        page.addView(paymentCard, paymentParams);

        TextView paymentTitle = text("Podaci za plaćanje", 19, navy);
        paymentTitle.setTypeface(null, 1);
        paymentCard.addView(paymentTitle);

        for (int i = 0; i < 9; i++) {
            addField(paymentCard, i);
        }

        Button togglePayer = button(
            "＋ Podaci platitelja (opcionalno)", false
        );
        page.addView(togglePayer);

        LinearLayout payerCard = column();
        payerCard.setPadding(
            dp(16), dp(12), dp(16), dp(12)
        );
        payerCard.setBackgroundColor(Color.WHITE);

        for (int i = 9; i < 12; i++) {
            addField(payerCard, i);
        }

        payerCard.setVisibility(View.GONE);
        page.addView(payerCard);

        togglePayer.setOnClickListener(v -> {
            boolean visible =
                payerCard.getVisibility() == View.VISIBLE;
            payerCard.setVisibility(
                visible ? View.GONE : View.VISIBLE
            );
            togglePayer.setText(
                visible
                    ? "＋ Podaci platitelja (opcionalno)"
                    : "－ Sakrij podatke platitelja"
            );
        });

        loadFields();
        loadContacts();

        LinearLayout addressCard = column();
        addressCard.setPadding(
            dp(16), dp(18), dp(16), dp(16)
        );
        addressCard.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams addressParams =
            new LinearLayout.LayoutParams(-1, -2);
        addressParams.topMargin = dp(16);

        page.addView(addressCard, 2, addressParams);

        TextView addressTitle = text(
            "Adresari, računi i predlošci", 19, navy
        );
        addressTitle.setTypeface(null, 1);
        addressCard.addView(addressTitle);

        addContactRow(
            addressCard, "Primatelji", "recipients",
            RECIPIENT_KEYS, RECIPIENT_INDICES
        );

        addContactRow(
            addressCard, "Platitelji", "payers",
            PAYER_KEYS, PAYER_INDICES
        );

        addContactRow(
            addressCard, "Bankovni računi", "accounts",
            ACCOUNT_KEYS, ACCOUNT_INDICES
        );

        addContactRow(
            addressCard, "Predlošci plaćanja", "templates",
            TEMPLATE_KEYS, TEMPLATE_INDICES
        );

        refreshMenus();

        LinearLayout transfer = new LinearLayout(this);
        transfer.setOrientation(LinearLayout.HORIZONTAL);
        addressCard.addView(transfer);

        Button export = button("IZVEZI JSON", false);
        Button imp = button("UVEZI JSON", false);

        transfer.addView(
            export,
            new LinearLayout.LayoutParams(0, dp(50), 1)
        );
        transfer.addView(
            imp,
            new LinearLayout.LayoutParams(0, dp(50), 1)
        );

        export.setOnClickListener(v -> {
            Intent intent =
                new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(
                Intent.EXTRA_TITLE, "HUB3A_adresar.json"
            );
            startActivityForResult(intent, EXPORT_REQUEST);
        });

        imp.setOnClickListener(v -> {
            Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, IMPORT_REQUEST);
        });

        Button generate = button(
            "SPREMI PDF417 KAO PNG", true
        );
        LinearLayout.LayoutParams generateParams =
            new LinearLayout.LayoutParams(-1, dp(58));
        generateParams.topMargin = dp(18);
        page.addView(generate, generateParams);

        Button remember = button(
            "ZAPAMTI PODATKE", false
        );
        page.addView(remember);

        remember.setOnClickListener(v -> {
            saveFields();
            Toast.makeText(
                this,
                "Podaci su spremljeni",
                Toast.LENGTH_LONG
            ).show();
        });

        generate.setOnClickListener(v -> {
            try {
                currentBarcode = encode(payload());

                Intent intent =
                    new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/png");
                intent.putExtra(
                    Intent.EXTRA_TITLE, "HUB3A_barkod.png"
                );
                startActivityForResult(intent, PNG_REQUEST);

            } catch (Exception error) {
                showError(
                    "Provjera podataka", error.getMessage()
                );
            }
        });

        TextView note = text(
            "Podaci se obrađuju lokalno. Prije plaćanja provjerite IBAN i ostale podatke.",
            12, muted
        );
        page.addView(note);

        setContentView(scroll);
    }

    private String[] sectionKeys(String section) {
        switch (section) {
            case "payers": return PAYER_KEYS;
            case "recipients": return RECIPIENT_KEYS;
            case "accounts": return ACCOUNT_KEYS;
            default: return TEMPLATE_KEYS;
        }
    }

    private JSONArray items(String section) {
        JSONArray result = addressBook.optJSONArray(section);
        return result == null ? new JSONArray() : result;
    }

    private void loadContacts() {
        try {
            String raw = getSharedPreferences(
                "hub3a_contacts", MODE_PRIVATE
            ).getString("data", "");

            addressBook = raw.isEmpty()
                ? new JSONObject()
                : new JSONObject(raw);

            if (addressBook.optInt("version", 1) != 1) {
                throw new Exception("Nepodržana verzija adresara");
            }

            addressBook.put("version", 1);

            for (String section : new String[]{
                "payers", "recipients", "accounts", "templates"
            }) {
                if (addressBook.optJSONArray(section) == null) {
                    addressBook.put(section, new JSONArray());
                }
            }
        } catch (Exception ex) {
            addressBook = new JSONObject();
            try {
                addressBook.put("version", 1);
                for (String section : new String[]{
                    "payers", "recipients", "accounts", "templates"
                }) {
                    addressBook.put(section, new JSONArray());
                }
            } catch (Exception ignored) {}

            showError("Adresar", ex.getMessage());
        }
    }

    private void persistContacts() {
        getSharedPreferences(
            "hub3a_contacts", MODE_PRIVATE
        ).edit()
         .putString("data", addressBook.toString())
         .apply();
    }

    private void refreshMenus() {
        refreshing = true;

        for (String section : new String[]{
            "recipients", "payers", "accounts", "templates"
        }) {
            Spinner spinner = selectors.get(section);
            if (spinner == null) continue;

            ArrayList<String> names = new ArrayList<>();
            names.add("— Odaberi —");

            String nameKey =
                section.equals("payers") ? "payer"
                : section.equals("recipients") ? "recipient"
                : "label";

            JSONArray records = items(section);

            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.optJSONObject(i);
                names.add(
                    (i + 1) + ". " +
                    (record == null ? "" :
                     record.optString(nameKey, ""))
                );
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                names
            );
            adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            );
            spinner.setAdapter(adapter);
        }

        refreshing = false;
    }

    private void addContactRow(
        LinearLayout parent,
        String caption,
        String section,
        String[] keys,
        int[] indices
    ) {
        TextView heading = text(caption, 14, navy);
        heading.setTypeface(null, 1);
        heading.setPadding(0, dp(12), 0, dp(4));
        parent.addView(heading);

        Spinner spinner = new Spinner(this);
        parent.addView(spinner);
        selectors.put(section, spinner);

        spinner.setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onNothingSelected(
                    AdapterView<?> parent
                ) {}

                @Override
                public void onItemSelected(
                    AdapterView<?> parent,
                    View view, int position, long id
                ) {
                    if (refreshing || position <= 0) return;

                    JSONObject record = items(section)
                        .optJSONObject(position - 1);

                    if (record == null) return;

                    for (int i = 0; i < keys.length; i++) {
                        if (indices[i] >= 0) {
                            fields[indices[i]].setText(
                                record.optString(keys[i], "")
                            );
                        }
                    }
                }
            }
        );

        LinearLayout actions = new LinearLayout(this);
        parent.addView(actions);

        Button save = button("Dodaj / Ažuriraj", false);
        Button delete = button("Obriši", false);

        actions.addView(
            save, new LinearLayout.LayoutParams(0, dp(50), 2)
        );
        actions.addView(
            delete, new LinearLayout.LayoutParams(0, dp(50), 1)
        );

        save.setOnClickListener(v -> {
            try {
                JSONObject entry = new JSONObject();

                for (int i = 0; i < keys.length; i++) {
                    if (indices[i] >= 0) {
                        entry.put(
                            keys[i], val(indices[i])
                        );
                    }
                }

                if (indices[0] == -1) {
                    int selected =
                        spinner.getSelectedItemPosition() - 1;

                    JSONObject existing =
                        selected >= 0
                        ? items(section).optJSONObject(selected)
                        : null;

                    String previous =
                        existing == null ? ""
                        : existing.optString("label", "");

                    EditText input = new EditText(this);
                    input.setSingleLine(true);
                    input.setText(previous);

                    new AlertDialog.Builder(this)
                        .setTitle("Naziv računa ili predloška")
                        .setView(input)
                        .setPositiveButton("Spremi", (dialog, which) -> {
                            try {
                                String name =
                                    input.getText().toString().trim();

                                if (name.isEmpty()) {
                                    throw new Exception(
                                        "Unesite naziv."
                                    );
                                }

                                if (
                                    section.equals("accounts") &&
                                    entry.optString("iban").isEmpty()
                                ) {
                                    throw new Exception(
                                        "Unesite IBAN."
                                    );
                                }

                                entry.put("label", name);
                                storeContact(
                                    section, spinner, entry
                                );
                            } catch (Exception ex) {
                                showError(
                                    "Adresar", ex.getMessage()
                                );
                            }
                        })
                        .setNegativeButton("Odustani", null)
                        .show();
                } else {
                    if (val(indices[0]).isEmpty()) {
                        throw new Exception(
                            "Unesite naziv."
                        );
                    }

                    storeContact(section, spinner, entry);
                }
            } catch (Exception ex) {
                showError("Adresar", ex.getMessage());
            }
        });

        delete.setOnClickListener(v -> {
            int index = spinner.getSelectedItemPosition() - 1;
            if (index < 0) return;

            new AlertDialog.Builder(this)
                .setTitle("Obrisati odabrani zapis?")
                .setPositiveButton("Obriši", (dialog, which) -> {
                    try {
                        JSONArray records = items(section);
                        records.remove(index);
                        addressBook.put(section, records);
                        persistContacts();
                        refreshMenus();
                    } catch (Exception ex) {
                        showError(
                            "Adresar", ex.getMessage()
                        );
                    }
                })
                .setNegativeButton("Odustani", null)
                .show();
        });
    }

    private void storeContact(
        String section, Spinner spinner, JSONObject entry
    ) throws Exception {
        JSONArray records = items(section);
        int index = spinner.getSelectedItemPosition() - 1;

        if (index >= 0 && index < records.length()) {
            records.put(index, entry);
        } else {
            records.put(entry);
        }

        addressBook.put(section, records);
        persistContacts();
        refreshMenus();

        Toast.makeText(
            this, "Zapis spremljen", Toast.LENGTH_SHORT
        ).show();
    }

    private JSONObject validateImport(JSONObject source)
        throws Exception {

        if (source.optInt("version", -1) != 1) {
            throw new Exception(
                "Nepodržana verzija adresara."
            );
        }

        JSONObject cleaned = new JSONObject();
        cleaned.put("version", 1);

        for (String section : new String[]{
            "payers", "recipients", "accounts", "templates"
        }) {
            JSONArray records = source.optJSONArray(section);

            if (
                records == null &&
                (section.equals("accounts") ||
                 section.equals("templates"))
            ) {
                records = new JSONArray();
            }

            if (records == null || records.length() > 1000) {
                throw new Exception(
                    "Neispravan popis: " + section
                );
            }

            JSONArray output = new JSONArray();
            String[] keys = sectionKeys(section);

            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.optJSONObject(i);

                if (record == null) {
                    throw new Exception(
                        "Neispravan zapis."
                    );
                }

                JSONObject copy = new JSONObject();

                for (String key : keys) {
                    Object value = record.opt(key);

                    if (
                        value != null &&
                        value != JSONObject.NULL &&
                        !(value instanceof String)
                    ) {
                        throw new Exception(
                            "Neispravno polje: " + key
                        );
                    }

                    String data =
                        record.optString(key, "");

                    if (data.length() > 300) {
                        throw new Exception(
                            "Predugo polje."
                        );
                    }

                    copy.put(key, data);
                }

                if (
                    copy.optString(keys[0], "").trim().isEmpty()
                ) {
                    throw new Exception(
                        "Zapis bez naziva."
                    );
                }

                output.put(copy);
            }

            cleaned.put(section, output);
        }

        return cleaned;
    }

    private void saveFields() {
        SharedPreferences.Editor editor =
            getSharedPreferences(
                "hub3a_fields", MODE_PRIVATE
            ).edit();

        for (int i = 0; i < fields.length; i++) {
            editor.putString(
                "field_" + i,
                fields[i].getText().toString()
            );
        }
        editor.apply();
    }

    private void loadFields() {
        SharedPreferences saved = getSharedPreferences(
            "hub3a_fields", MODE_PRIVATE
        );

        for (int i = 0; i < fields.length; i++) {
            if (saved.contains("field_" + i)) {
                fields[i].setText(
                    saved.getString("field_" + i, "")
                );
            }
        }
    }

    private String val(int index) {
        return fields[index].getText().toString().trim();
    }

    private boolean validIban(String iban) {
        if (!iban.matches("HR[0-9]{19}")) {
            return false;
        }

        String reorganized =
            iban.substring(4) + "1727" + iban.substring(2, 4);

        int remainder = 0;
        for (int i = 0; i < reorganized.length(); i++) {
            remainder = (
                remainder * 10 +
                (reorganized.charAt(i) - '0')
            ) % 97;
        }

        return remainder == 1;
    }

    private String payload() {
        BigDecimal amount;

        try {
            amount = new BigDecimal(
                val(0).replace(",", ".")
            );

            if (amount.signum() <= 0 || amount.scale() > 2) {
                throw new Exception();
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                "Neispravan iznos."
            );
        }

        String iban = val(4)
            .replaceAll("\\s+", "")
            .toUpperCase(Locale.ROOT);

        if (!validIban(iban)) {
            throw new IllegalArgumentException(
                "Neispravan hrvatski IBAN."
            );
        }

        String model = val(5).toUpperCase(Locale.ROOT);

        if (!model.matches("HR[0-9]{2}")) {
            throw new IllegalArgumentException(
                "Neispravan model."
            );
        }

        if (val(1).isEmpty() || val(8).isEmpty()) {
            throw new IllegalArgumentException(
                "Unesite primatelja i opis."
            );
        }

        String[] lines = {
            "HRVHUB30",
            "EUR",
            String.format(
                Locale.ROOT, "%015d",
                amount.movePointRight(2).longValueExact()
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

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            if (
                lines[i].length() > limits[i] ||
                !lines[i].matches(allowed)
            ) {
                throw new IllegalArgumentException(
                    "Neispravno polje " + (i + 1)
                );
            }

            result.append(lines[i]).append('\n');
        }

        return result.toString();
    }

    private Bitmap encode(String value) throws Exception {
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
            new Dimensions(9, 9, 3, 90)
        );

        BitMatrix matrix = new MultiFormatWriter().encode(
            value, BarcodeFormat.PDF_417,
            1100, 550, hints
        );

        Bitmap image = Bitmap.createBitmap(
            matrix.getWidth(),
            matrix.getHeight(),
            Bitmap.Config.ARGB_8888
        );

        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                image.setPixel(
                    x, y,
                    matrix.get(x, y)
                        ? Color.BLACK : Color.WHITE
                );
            }
        }

        return image;
    }

    private void showError(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("U redu", null)
            .show();
    }

    @Override
    protected void onActivityResult(
        int request, int result, Intent intent
    ) {
        super.onActivityResult(request, result, intent);

        if (result != RESULT_OK || intent == null) {
            return;
        }

        if (request == IMPORT_REQUEST) {
            try (
                InputStream input =
                    getContentResolver().openInputStream(
                        intent.getData()
                    );
                ByteArrayOutputStream buffer =
                    new ByteArrayOutputStream()
            ) {
                if (input == null) {
                    throw new Exception(
                        "Datoteka nije dostupna."
                    );
                }

                byte[] chunk = new byte[4096];
                int length;

                while ((length = input.read(chunk)) != -1) {
                    if (buffer.size() + length > 1048576) {
                        throw new Exception(
                            "Datoteka je prevelika."
                        );
                    }
                    buffer.write(chunk, 0, length);
                }

                JSONObject imported = validateImport(
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
                        "Zamijeniti sve postojeće adresare, račune i predloške?"
                    )
                    .setPositiveButton(
                        "Zamijeni",
                        (dialog, which) -> {
                            addressBook = imported;
                            persistContacts();
                            refreshMenus();
                        }
                    )
                    .setNegativeButton("Odustani", null)
                    .show();

            } catch (Exception ex) {
                showError(
                    "Uvoz adresara", ex.getMessage()
                );
            }

            return;
        }

        if (request == EXPORT_REQUEST) {
            try (
                OutputStream output =
                    getContentResolver().openOutputStream(
                        intent.getData()
                    )
            ) {
                if (output == null) {
                    throw new Exception(
                        "Spremanje nije uspjelo."
                    );
                }

                output.write(
                    addressBook.toString(2)
                        .getBytes(StandardCharsets.UTF_8)
                );

                Toast.makeText(
                    this, "Adresar izvezen",
                    Toast.LENGTH_LONG
                ).show();

            } catch (Exception ex) {
                showError(
                    "Izvoz adresara", ex.getMessage()
                );
            }

            return;
        }

        if (request == PNG_REQUEST) {
            try (
                OutputStream output =
                    getContentResolver().openOutputStream(
                        intent.getData()
                    )
            ) {
                if (
                    output == null ||
                    currentBarcode == null ||
                    !currentBarcode.compress(
                        Bitmap.CompressFormat.PNG,
                        100, output
                    )
                ) {
                    throw new Exception(
                        "Spremanje PNG-a nije uspjelo."
                    );
                }

                Toast.makeText(
                    this, "PNG spremljen",
                    Toast.LENGTH_LONG
                ).show();

            } catch (Exception ex) {
                showError(
                    "Spremanje barkoda", ex.getMessage()
                );
            }
        }
    }
}
