
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

        if (i == 0) {
            e.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            );
        }
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
            "Offline generator barkoda za račune i ponude",
            13, muted
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
                visible
                    ? "＋  Podaci platitelja (opcionalno)"
                    : "－  Sakrij podatke platitelja"
            );
        });

        loadFields();

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

    void saveFields() {
        SharedPreferences.Editor editor =
            getSharedPreferences("hub3a_fields", MODE_PRIVATE).edit();
        for (int i = 0; i < fields.length; i++) {
            editor.putString(
                "field_" + i,
                fields[i].getText().toString()
            );
        }
        editor.apply();
    }

    void loadFields() {
        SharedPreferences saved =
            getSharedPreferences("hub3a_fields", MODE_PRIVATE);
        for (int i = 0; i < fields.length; i++) {
            if (saved.contains("field_" + i)) {
                fields[i].setText(
                    saved.getString("field_" + i, "")
                );
            }
        }
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
            8,3,15,30,27,27,25,25,27,21,4,22,4,35
        };

        String allowed =
            "[0-9A-Za-zČĆĐŠŽčćđšž ,.:+?'()/\\-]*";

        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].length() > limits[i] ||
                !lines[i].matches(allowed))
                throw new IllegalArgumentException(
                    "Polje " + (i+1) +
                    " ima neispravan znak ili duljinu (max " +
                    limits[i] + ")."
                );
            b.append(lines[i]).append('\n');
        }
        return b.toString();
    }

    boolean ibanValid(String iban) {
        String t = iban.substring(4) + "1727" + iban.substring(2,4);
        int rem = 0;
        for (int i = 0; i < t.length(); i++)
            rem = (rem * 10 + (t.charAt(i) - '0')) % 97;
        return rem == 1;
    }

    Bitmap encode(String data) throws Exception {
        Map<EncodeHintType,Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, 4);
        hints.put(EncodeHintType.PDF417_COMPACT, false);
        hints.put(EncodeHintType.MARGIN, 16);
        hints.put(EncodeHintType.PDF417_COMPACTION, Compaction.BYTE);
        hints.put(
            EncodeHintType.PDF417_DIMENSIONS,
            new com.google.zxing.pdf417.encoder.Dimensions(9,9,3,90)
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
                    bits.get(x,y) ? Color.BLACK : Color.WHITE
                );
        return bitmap;
    }

    @Override
    protected void onActivityResult(
        int request, int result, Intent intent
    ) {
        super.onActivityResult(request, result, intent);

        if (request == 101 &&
            result == RESULT_OK &&
            intent != null) {

            try (OutputStream out =
                getContentResolver().openOutputStream(intent.getData())) {

                if (out == null ||
                    !current.compress(Bitmap.CompressFormat.PNG, 100, out))
                    throw new Exception("Spremanje nije uspjelo");

                Toast.makeText(
                    this, "PNG spremljen", Toast.LENGTH_LONG
                ).show();

            } catch (Exception ex) {
                Toast.makeText(
                    this, ex.getMessage(), Toast.LENGTH_LONG
                ).show();
            }
        }
    }
}
