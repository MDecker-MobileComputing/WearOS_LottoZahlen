package de.mide.wear.lotto;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Vector;


/**
 * WearOS-App, die einen Tipp-Vorschlag für Lotto "6 aus 49" erzeugt.
 * Es werden echte Zufallszahlen vom "ANU Quantum Random Numbers Server"
 * verwendet, siehe http://qrng.anu.edu.au/API/api-demo.php
 * <br>
 *
 * This file is licensed under the terms of the BSD 3-Clause License.
 */
public class MainActivity extends WearableActivity
                          implements View.OnClickListener {

    /** Kennzeichen für Log-Nachrichten von dieser App. */
    public static final String TAG4LOGGING = "LottoZahlen";

    /** Höchste Zahl, die angekreuzt werden kann. */
    protected static final int MAX_ZAHL_LOTTO = 49;

    /** Anzahl der Lotto-Zahlen, die angekreuzt werden können. */
    protected static final int ANZ_LOTTO_ZAHLEN = 6;

    /** URL zum Aufruf der Web-API so dass 49 Zufallszahlen zurückgeliefert werden. */
    protected static final String WEB_API_URL = "https://qrng.anu.edu.au/API/jsonI.php?length=" + MAX_ZAHL_LOTTO + "&type=uint8";


    /**
     * TextView-Element zur Anzeige der Lotto-Zahlen oder einer Fehlermeldung, und zum
     * Auslösen eines neuen Lade-Vorgangs.
     */
    protected TextView _textView = null;

    /** Flag, um mehrere gleichzeitige Ladevorgänge zu verhindern. */
    boolean _ladevorgangLaueft = false;


    /**
     * Lifecycle-Methode, lädt Layout-Datei
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        _textView = findViewById( R.id.textView );
        _textView.setOnClickListener( this );

        setAmbientEnabled(); // Enables Always-on
    }

    /**
     * Event-Handler-Methode für das TextView-Element.
     *
     * @param view  TextView-Element, welche das Event ausgelöst hat.
     */
    @Override
    public void onClick(View view) {

        if (_ladevorgangLaueft == true) {
            Log.i(TAG4LOGGING, "Es läuft schon ein Ladevorgang.");
            return;
        }

        _ladevorgangLaueft = true;

        _textView.setText("Loading numbers ...");

        new MeinAsyncTask().execute();
    }


    /* *************************************** */
    /* ********* Start innere Klasse ********* */
    /* *************************************** */

    /**
     * Klasse kapselt Web-API-Aufruf (HTTP-Request) und parsen der JSON-Datei
     * in einem Worker-Thread.
     */
    protected class MeinAsyncTask extends AsyncTask<Void, Void, Integer[]> {

        /**
         * Member variable with error message, to be filled when something went wrong
         * during method {@link MeinAsyncTask#doInBackground(Void...)}.
         */
        protected String __errorMessage = "";

        /**
         * Diese Methode muss überschrieben werden, weil sie in der Oberklasse als
         * {@code abstract} deklariert ist (sonst erhält man keine instanzierbare Klasse).
         *  Die Methode wird (wie der Name andeutet) in einem Hintergrund-Thread
         *  (Worker-Thread) ausgeführt.
         *
         * @param voids  Die Methode bekommt keine Parameter übergeben.
         *
         * @return  Array mit den sechs ausgewählten Lotto-Zahlen; im Fehlerfall wird
         *          ein leerer Array zurückgegeben.
         */
        @Override
        public Integer[] doInBackground(Void... voids) {

            int[]  zufallsZahlenArray = null;
            String jsonAntwort        = null;

            try {
                jsonAntwort = rufeWebApi();
            }
            catch (Exception ex) {
                __errorMessage = "Error during HTTP request: " + ex.getMessage();
                Log.e(TAG4LOGGING, __errorMessage, ex);
                return new Integer[]{};
            }

            try {
                zufallsZahlenArray = jsonDateiParsen( jsonAntwort );
            }
            catch (Exception ex) {
                __errorMessage = "Error during parsing response from Web API: " + ex.getMessage();
                Log.e(TAG4LOGGING, __errorMessage, ex);
                return new Integer[]{};
            }

            return lottoZahlenAuswaehlen( zufallsZahlenArray );
        }


        /**
         * Methode zur Darstellung des Ergebnisses.
         *
         * @param lottoZahlenArray  Array mit den 6 Lotto-Zahlen.
         */
        @Override
        protected void onPostExecute(Integer[] lottoZahlenArray) {

            if (lottoZahlenArray == null || lottoZahlenArray.length == 0) {

                _textView.setText( __errorMessage );

            } else {

                StringBuffer sb = new StringBuffer("Lotto numbers:\n\n");

                sb.append( lottoZahlenArray[0] ).append( ", " );
                sb.append( lottoZahlenArray[1] ).append( ", " );
                sb.append( lottoZahlenArray[2] ).append( "\n" );

                sb.append( lottoZahlenArray[3] ).append( ", " );
                sb.append( lottoZahlenArray[4] ).append( ", " );
                sb.append( lottoZahlenArray[5] );

                _textView.setText( sb.toString() );
            }

            _ladevorgangLaueft = false;
        }

    };
    /* *************************************** */
    /* ********* Ende innere Klasse  ********* */
    /* *************************************** */

    /**
     * Methode führt den eigentlichen Web-API-Request über HTTP durch.
     * <br>
     * Damit die App einen Internet-Zugriff durchführen darf muss in der Manifest-Datei
     * die Permission {@code android.permission.INTERNET} deklariert sein.
     *
     * @return  JSON-Dokument mit Antwort von Web-API.
     *
     * @throws Exception  Fehler während HTTP-Request.
     */
    protected String rufeWebApi() throws Exception {

        URL               url         = null;
        HttpURLConnection conn        = null;
        String            jsonAntwort = "";

        url  = new URL(WEB_API_URL);
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET"); // Eigentlich nicht nötig, weil "GET" Default-Wert ist.

        if ( conn.getResponseCode() != HttpURLConnection.HTTP_OK ) {

            String errorMessage = "HTTP error: " + conn.getResponseMessage();
            throw new Exception( errorMessage );

        } else {

            InputStream is        = conn.getInputStream();
            InputStreamReader ris = new InputStreamReader(is);
            BufferedReader reader = new BufferedReader(ris);

            // JSON-Dokument zeilenweise einlesen
            String zeile = "";
            while ( (zeile = reader.readLine()) != null) {
                jsonAntwort += zeile;
            }
        }

        Log.i(TAG4LOGGING, "JSON-Antwort: " + jsonAntwort);

        return jsonAntwort;
    }


    /**
     * Methoden zum Parsen der JSON-Datei, die die Web-API als Antwort geschickt hat.
     *
     * @param jsonDatei  JSON-Datei, die die Web-API als Antwort zurückgegeben hat.
     *
     * @return  Array der Zufallszahlen von der Web-API.
     *
     * @throws JSONException  Fehler beim Parsen, z.B. fehlendes Attribut.
     */
    protected int[] jsonDateiParsen(String jsonDatei) throws JSONException {

        JSONObject jsonObjekt = new JSONObject( jsonDatei );

        boolean erfolg = jsonObjekt.getBoolean("success");
        if (!erfolg) {
            throw new JSONException("Web-API response was success=false.");
        }

        int anzahl = jsonObjekt.getInt("length");
        if (anzahl != MAX_ZAHL_LOTTO) {
            throw new JSONException("Web-API response contained " + anzahl + " instead of " + MAX_ZAHL_LOTTO + " random numbers.");
        }

        JSONArray jsonArray = jsonObjekt.getJSONArray("data");

        int jsonArrayLaenge = jsonArray.length();
        if (jsonArrayLaenge != MAX_ZAHL_LOTTO) {
            throw new JSONException("JSON-Array contained " + jsonArrayLaenge + " instead of " + MAX_ZAHL_LOTTO + " number.");
        }

        int[] ergebnisArray = new int[MAX_ZAHL_LOTTO];
        for (int i = 0; i < jsonArrayLaenge; i++) {
            ergebnisArray[i] = jsonArray.getInt(i);
        }

        Log.i(TAG4LOGGING, "Zufallszahlen von Web-API wurden aus JSON-Datei ausgelesen.");

        return ergebnisArray;
    }

    /* **************************************** */
    /* ********* Start innere Klassen ********* */
    /* **************************************** */

    /**
     * Klasse um ein Zahlen Paar bestehend aus einer Zufallszahl von der Web-API und 
     * einer Lotto-Zahl (1-49) zu repräsentieren.
     */
    protected class ZahlenPaar {

        int zufallszahl = -1;
        int lottozahl   = -1;
    };

    /**
     * Comparator-Objekt zum Sortieren eines Arrays mit Elementen der KLasse {@link ZahlenPaar}.
     * Die Elemente werden anhand der Komponent {@link ZahlenPaar#zufallszahl} verglichen,
     * also anhand der von der Web-API zurückgelieferten Zahlen.
     */
    protected class ZahlenPaarComparator implements Comparator<ZahlenPaar> {

        /**
         * Methode zum Vergleich von zwei Elementen der Klasse {@link ZahlenPaar}.
         *
         * @param zp1  Erstes {@link ZahlenPaar}-Objekt für Vergleich.
         *
         * @param zp2  Zweites {@link ZahlenPaar}-Objekt für Vergleich.
         *
         * @return  Negativer Wert gdw. das erste Argument kleiner als das zweite Argument ist;
         *          wenn beide gleich groß sind, dann wird 0 zurückgegeben;
         *          wenn das erste Argument größer als das zweite Argument ist, dann wird eine
         *          postive Zahl zurückgegeben.
         */
        @Override
        public int compare(ZahlenPaar zp1, ZahlenPaar zp2) {

            return zp1.zufallszahl - zp2.zufallszahl;
        }

    };
    /* **************************************** */
    /* ********* Ende innere Klassen ********* */
    /* **************************************** */


    /**
     * Diese Methode wählt anhand der von der Web-API zurückgelieferten Zufallszahlen
     * die sechs auf dem Lotto-Schein anzukreuzenden Zahlen aus.
     *
     * @param zufallsZahlenVonWebApi  Array mit den Zufallszahlen von der Web-API
     *
     * @return  Die sechs Zufallszahle, die anzuzeigen sind.
     */
    protected Integer[] lottoZahlenAuswaehlen(int[] zufallsZahlenVonWebApi) {

        // Die Lotto-Zahlen 1..49 und die Zufallszahlen als Zahlen-Paaren in einen Array kopieren.
        ZahlenPaar[] zahlenPaarArray = new ZahlenPaar[MAX_ZAHL_LOTTO];
        for (int i = 0; i < MAX_ZAHL_LOTTO; i++) {
            ZahlenPaar paar    = new ZahlenPaar();
            paar.zufallszahl   = zufallsZahlenVonWebApi[i];
            paar.lottozahl     = i+1;
            zahlenPaarArray[i] = paar;
        }


        // "Durchmischen" des Arrays, indem nach den Zufallszahlen sortiert wird.
        // Dadurch werden die aufsteigend eingefügten Lotto-Zahlen 1..49 durcheinandergewürfelt.
        Arrays.sort( zahlenPaarArray, new ZahlenPaarComparator() );


        // Die ersten sechs Lotto-Zahlen werden in den Ergebnis-Array kopiert.
        Integer[] resultLottoZahlen = new Integer[ANZ_LOTTO_ZAHLEN];
        for (int i = 0; i < ANZ_LOTTO_ZAHLEN; i++) {
            ZahlenPaar paar      = zahlenPaarArray[i];
            resultLottoZahlen[i] = paar.lottozahl;
        }
        
        Arrays.sort( resultLottoZahlen );

        return resultLottoZahlen;
    }

}
