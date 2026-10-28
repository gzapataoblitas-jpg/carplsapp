package cl.carplus.registro;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Carplus · Registro de clientes.
 * Envuelve la página web (assets/index.html) en un WebView. La app NO abre hasta que
 * el teléfono tenga concedidos el permiso de cámara y de ubicación y la ubicación esté activada.
 */
public class MainActivity extends Activity {

    private static final int REQ_PERMS = 77;
    // Origen https ficticio: da "contexto seguro" a Web Crypto, cámara y geolocalización,
    // y mantiene el almacenamiento local (cola de envíos) estable entre aperturas.
    private static final String BASE_URL = "https://app.carplus.local/";

    private WebView web;
    private LinearLayout gate;
    private TextView gateMsg;
    private Button gateBtn;
    private boolean webLoaded = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#1F377D"));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildGate();
        setContentView(gate);
        checkRequirements();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkRequirements();   // al volver de Ajustes se vuelve a comprobar
    }

    /* ---------- Pantalla de bloqueo ---------- */
    private void buildGate() {
        gate = new LinearLayout(this);
        gate.setOrientation(LinearLayout.VERTICAL);
        gate.setGravity(Gravity.CENTER);
        gate.setBackgroundColor(Color.parseColor("#EEF1F7"));
        int p = (int) (28 * getResources().getDisplayMetrics().density);
        gate.setPadding(p, p, p, p);

        TextView t = new TextView(this);
        t.setText("Carplus");
        t.setTextSize(28);
        t.setTextColor(Color.parseColor("#20397F"));
        t.setGravity(Gravity.CENTER);
        gate.addView(t);

        gateMsg = new TextView(this);
        gateMsg.setTextSize(16);
        gateMsg.setTextColor(Color.parseColor("#142048"));
        gateMsg.setGravity(Gravity.CENTER);
        gateMsg.setPadding(0, p / 2, 0, p / 2);
        gate.addView(gateMsg);

        gateBtn = new Button(this);
        gateBtn.setAllCaps(false);
        gateBtn.setBackgroundColor(Color.parseColor("#F5C842"));
        gateBtn.setTextColor(Color.parseColor("#142048"));
        gate.addView(gateBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private boolean has(String perm) {
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean locationOn() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        try {
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) { return false; }
    }

    private void checkRequirements() {
        boolean cam = has(Manifest.permission.CAMERA);
        boolean loc = has(Manifest.permission.ACCESS_FINE_LOCATION);

        if (!cam || !loc) {
            showGate("Para usar la app necesitas permitir el acceso a la cámara y a la ubicación del teléfono.",
                    "Conceder permisos", v -> {
                        // Si Android ya no muestra el diálogo (denegado dos veces), lleva a Ajustes
                        boolean canAsk = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                                || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                                || firstAsk;
                        if (canAsk) { firstAsk = false; askPermissions(); } else openAppSettings();
                    });
            if (firstAsk) { firstAsk = false; askPermissions(); }
            return;
        }
        if (!locationOn()) {
            showGate("Activa la ubicación (GPS) del teléfono para continuar.",
                    "Abrir ajustes de ubicación", v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
            return;
        }
        openWeb();
    }

    private boolean firstAsk = true;

    private void askPermissions() {
        requestPermissions(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_PERMS);
    }

    private void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        checkRequirements();
    }

    private void showGate(String msg, String btn, View.OnClickListener l) {
        if (web != null) { web.setVisibility(View.GONE); }
        gateMsg.setText(msg);
        gateBtn.setText(btn);
        gateBtn.setOnClickListener(l);
        if (webLoaded && web != null) { setContentView(gate); }
    }

    /* ---------- WebView ---------- */
    private String readAsset(String name) throws Exception {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private void openWeb() {
        if (web != null) {            // ya cargada: solo volver a mostrarla
            web.setVisibility(View.VISIBLE);
            if (web.getParent() == null) setContentView(web);
            return;
        }
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportZoom(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return true;   // la app no navega a otros sitios
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (has(Manifest.permission.CAMERA)) request.grant(request.getResources());
                    else request.deny();
                });
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, has(Manifest.permission.ACCESS_FINE_LOCATION), false);
            }
        });

        try {
            web.loadDataWithBaseURL(BASE_URL, readAsset("index.html"), "text/html", "UTF-8", null);
        } catch (Exception e) {
            showGate("No se pudo abrir la aplicación.", "Reintentar", v -> recreate());
            return;
        }
        webLoaded = true;
        setContentView(web);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);   // no cierra la sesión ni pierde datos al presionar "atrás"
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
