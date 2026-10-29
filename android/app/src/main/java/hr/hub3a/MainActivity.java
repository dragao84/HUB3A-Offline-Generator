package hr.hub3a;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
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

public class MainActivity extends Activity {
    String[] keys = {"Iznos EUR", "Primatelj", "Ulica primatelja", "Mjesto primatelja", "IBAN", "Model", "Poziv na broj", "Šifra namjene", "Opis plaćanja", "Platitelj", "Ulica platitelja", "Mjesto platitelja"};
    EditText[] fields = new EditText[keys.length];
    Bitmap current;
    @Override public void onCreate(Bundle state){ super.onCreate(state);
        ScrollView scroll=new ScrollView(this); LinearLayout col=new LinearLayout(this); col.setOrientation(1); col.setPadding(32,24,32,24); scroll.addView(col);
        TextView heading=new TextView(this);heading.setText("HUB-3A • PDF417 offline generator"); heading.setTextSize(21); col.addView(heading);
        for(int i=0;i<keys.length;i++){TextView label=new TextView(this);label.setText(keys[i]);col.addView(label); EditText e=new EditText(this);e.setSingleLine(true); if(i==5)e.setText("HR99"); fields[i]=e;col.addView(e);}
        Button generate=new Button(this);generate.setText("GENERAJ I SPREMI PNG");col.addView(generate);
        TextView note=new TextView(this);note.setText("Podaci ostaju na uređaju. Prije plaćanja provjerite očitane podatke u bankovnoj aplikaciji.");col.addView(note);
        generate.setOnClickListener(v->{ try { current=encode(payload()); Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("image/png"); intent.putExtra(Intent.EXTRA_TITLE,"HUB3A_barkod.png");startActivityForResult(intent,101); }catch(Exception ex){new AlertDialog.Builder(this).setTitle("Provjera podataka").setMessage(ex.getMessage()).setPositiveButton("U redu",null).show();}});
        setContentView(scroll);
    }
    String val(int index){return fields[index].getText().toString().trim();}
    String payload(){
        BigDecimal amt;
        try { amt=new BigDecimal(val(0).replace(",",".")); if(amt.signum()<=0 || amt.scale()>2)throw new Exception(); }catch(Exception ex){throw new IllegalArgumentException("Iznos mora biti pozitivan, najviše dvije decimale.");}
        String iban=val(4).replaceAll("\\s+","").toUpperCase(Locale.ROOT);
        if(!iban.matches("HR[0-9]{19}") || !ibanValid(iban))throw new IllegalArgumentException("Neispravan hrvatski IBAN.");
        String model=val(5).toUpperCase(Locale.ROOT);if(!model.matches("HR[0-9]{2}"))throw new IllegalArgumentException("Model mora biti HRxx.");
        if(val(1).isEmpty() || val(8).isEmpty())throw new IllegalArgumentException("Primatelj i opis plaćanja su obavezni.");
        String[] lines={"HRVHUB30","EUR",String.format(Locale.ROOT,"%015d",amt.movePointRight(2).longValueExact()),val(9),val(10),val(11),val(1),val(2),val(3),iban,model,val(6),val(7),val(8)};
        int[] limits={8,3,15,30,27,27,25,25,27,21,4,22,4,35};
        String allowed="[0-9A-Za-zČĆĐŠŽčćđšž ,.:+?'()/\\-]*";
        StringBuilder b=new StringBuilder();for(int i=0;i<lines.length;i++){if(lines[i].length()>limits[i] || !lines[i].matches(allowed))throw new IllegalArgumentException("Polje "+(i+1)+" ima neispravan znak ili duljinu (max "+limits[i]+").");b.append(lines[i]).append('\n');}
        return b.toString();
    }
    boolean ibanValid(String iban){String t=iban.substring(4)+"1727"+iban.substring(2,4);int rem=0;for(int i=0;i<t.length();i++)rem=(rem*10+(t.charAt(i)-'0'))%97;return rem==1;}
    Bitmap encode(String data)throws Exception{
        Map<EncodeHintType,Object> hints=new HashMap<>();hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");hints.put(EncodeHintType.ERROR_CORRECTION,4);hints.put(EncodeHintType.PDF417_COMPACT,false);hints.put(EncodeHintType.MARGIN,16);
        hints.put(EncodeHintType.PDF417_COMPACTION,Compaction.BYTE);
        hints.put(EncodeHintType.PDF417_DIMENSIONS,new com.google.zxing.pdf417.encoder.Dimensions(9,9,3,90));
        BitMatrix bits=new MultiFormatWriter().encode(data,BarcodeFormat.PDF_417,1100,550,hints);
        Bitmap bitmap=Bitmap.createBitmap(bits.getWidth(),bits.getHeight(),Bitmap.Config.ARGB_8888);
        for(int y=0;y<bits.getHeight();y++)for(int x=0;x<bits.getWidth();x++)bitmap.setPixel(x,y,bits.get(x,y)?Color.BLACK:Color.WHITE);
        return bitmap;
    }
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(request==101 && result==RESULT_OK && intent!=null){try(OutputStream out=getContentResolver().openOutputStream(intent.getData())){if(out==null || !current.compress(Bitmap.CompressFormat.PNG,100,out))throw new Exception("Spremanje nije uspjelo");Toast.makeText(this,"PNG spremljen",Toast.LENGTH_LONG).show();}catch(Exception ex){Toast.makeText(this,ex.getMessage(),Toast.LENGTH_LONG).show();}}}
}
