package net.wlodek.geopurge;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.location.LocationManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.location.LocationManagerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;
    private static final int APPLE_COMPANY_ID = 0x004C;
    private static final long TICK_MS = 1000;
    private static final int DARK = Color.rgb(32, 42, 53);

    private ExhibitRepository repository;
    private BeaconTracker tracker;
    private BluetoothLeScanner scanner;

    private boolean scanning = false;
    private boolean askedForPermissions = false;
    private Integer openKey = null;       // eksponat otwarty w oknie
    private Integer dismissedKey = null;  // zamknięty X, czeka aż odejdziesz
    private Runnable buttonAction;

    private TextView statusText;
    private TextView debugText;
    private Button actionButton;

    private View overlay;
    private ScrollView overlayScroll;
    private ImageView overlayImage;
    private TextView overlayTitle;
    private TextView overlayDescription;
    private OnBackPressedCallback backCallback;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            evaluate();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private final ScanCallback callback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            handleResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult r : results) handleResult(r);
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            showProblem(
                    "Skanowanie nie powiodło się (kod " + errorCode + ").",
                    "Spróbuj ponownie",
                    MainActivity.this::startScanningIfReady
            );
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        configureSystemBars();
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        applyTopInset(findViewById(R.id.statusBarSpacer));
        applyTopInset(findViewById(R.id.overlayStatusSpacer));
        applyBottomInset(findViewById(R.id.mainRoot));
        applyBottomInset(findViewById(R.id.exhibitOverlay));

        statusText = findViewById(R.id.statusText);
        debugText = findViewById(R.id.debugText);
        actionButton = findViewById(R.id.actionButton);

        overlay = findViewById(R.id.exhibitOverlay);
        overlayScroll = findViewById(R.id.overlayScroll);
        overlayImage = findViewById(R.id.overlayImage);
        overlayTitle = findViewById(R.id.overlayTitle);
        overlayDescription = findViewById(R.id.overlayDescription);

        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                closeOverlay();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);

        findViewById(R.id.closeButton).setOnClickListener(
                v -> closeOverlay());

        actionButton.setOnClickListener(v -> {
            if (buttonAction != null) buttonAction.run();
        });

        try {
            repository = ExhibitRepository.load(this);
            tracker = new BeaconTracker(
                    repository.enterRssi, repository.exitRssi
            );
        } catch (Exception e) {
            showProblem(
                    "Błąd w pliku exhibits.json: " + e.getMessage(),
                    null, null
            );
            return;
        }
    }

    // ------------------------------------------------------------
    //  Cykl życia
    // ------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        startScanningIfReady();
    }

    @Override
    protected void onPause() {
        stopScan();
        super.onPause();
    }

    // ------------------------------------------------------------
    //  Warunki skanowania (uprawnienia, Bluetooth, lokalizacja)
    // ------------------------------------------------------------

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    private List<String> missingPermissions() {
        List<String> missing = new ArrayList<>();
        for (String p : requiredPermissions()) {
            if (ActivityCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }
        return missing;
    }

    private boolean permanentlyDenied(List<String> missing) {
        if (!askedForPermissions) return false;
        for (String p : missing) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, p)) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions(List<String> missing) {
        askedForPermissions = true;
        ActivityCompat.requestPermissions(
                this, missing.toArray(new String[0]), REQUEST_PERMISSIONS
        );
    }

    private void startScanningIfReady() {
        if (repository == null || scanning) return;

        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter =
                manager == null ? null : manager.getAdapter();

        if (adapter == null || !getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            showProblem(
                    "To urządzenie nie obsługuje Bluetooth Low Energy.",
                    null, null
            );
            return;
        }

        List<String> missing = missingPermissions();
        if (!missing.isEmpty()) {
            if (permanentlyDenied(missing)) {
                showProblem(
                        "Brak uprawnień do wykrywania eksponatów. "
                                + "Włącz je w ustawieniach aplikacji.",
                        "Otwórz ustawienia aplikacji",
                        () -> startActivity(new Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", getPackageName(), null)
                        ))
                );
            } else {
                showProblem(
                        "Aplikacja potrzebuje zgody na wyszukiwanie "
                                + "urządzeń w pobliżu, aby wykryć eksponaty.",
                        "Przyznaj uprawnienia",
                        () -> requestPermissions(missingPermissions())
                );
                if (!askedForPermissions) requestPermissions(missing);
            }
            return;
        }

        if (!adapter.isEnabled()) {
            showProblem(
                    "Bluetooth jest wyłączony.",
                    "Ustawienia Bluetooth",
                    () -> startActivity(
                            new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            );
            return;
        }

        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null || !LocationManagerCompat.isLocationEnabled(lm)) {
            showProblem(
                    "Włącz lokalizację w telefonie. Android wymaga jej "
                            + "do skanowania beaconów BLE.",
                    "Ustawienia lokalizacji",
                    () -> startActivity(new Intent(
                            Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            );
            return;
        }

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            showProblem(
                    "Nie można uruchomić skanera Bluetooth.",
                    "Spróbuj ponownie",
                    this::startScanningIfReady
            );
            return;
        }

        startScan();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            startScanningIfReady();
        }
    }

    // ------------------------------------------------------------
    //  Skanowanie iBeacon
    // ------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private void startScan() {
        if (scanning) return;

        // Filtr sprzętowy: producent Apple + prefiks 0x02 0x15 + nasz UUID.
        byte[] data = new byte[18];
        data[0] = 0x02;
        data[1] = 0x15;
        System.arraycopy(repository.uuidBytes, 0, data, 2, 16);

        byte[] mask = new byte[18];
        Arrays.fill(mask, (byte) 0xFF);

        ScanFilter filter = new ScanFilter.Builder()
                .setManufacturerData(APPLE_COMPANY_ID, data, mask)
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        try {
            scanner.startScan(
                    Collections.singletonList(filter), settings, callback
            );
            scanning = true;
            hideProblem();
            statusText.setText("Szukam eksponatów w pobliżu…");
            handler.removeCallbacks(tick);
            handler.post(tick);
        } catch (SecurityException | IllegalStateException e) {
            showProblem(
                    "Nie udało się uruchomić skanowania: " + e.getMessage(),
                    "Spróbuj ponownie",
                    this::startScanningIfReady
            );
        }
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        handler.removeCallbacks(tick);
        if (scanner != null && scanning) {
            try {
                scanner.stopScan(callback);
            } catch (SecurityException | IllegalStateException ignored) {
                // Bluetooth mógł zostać wyłączony w trakcie skanowania.
            }
        }
        scanning = false;
    }

    private void handleResult(ScanResult result) {
        ScanRecord record = result.getScanRecord();
        if (record == null) return;

        byte[] d = record.getManufacturerSpecificData(APPLE_COMPANY_ID);
        if (d == null || d.length < 22) return;
        if (d[0] != 0x02 || d[1] != 0x15) return;

        if (!Arrays.equals(Arrays.copyOfRange(d, 2, 18),
                repository.uuidBytes)) {
            return;
        }

        int major = ((d[18] & 0xFF) << 8) | (d[19] & 0xFF);
        int minor = ((d[20] & 0xFF) << 8) | (d[21] & 0xFF);

        tracker.add(
                ExhibitRepository.key(major, minor),
                result.getRssi(),
                SystemClock.elapsedRealtime()
        );
    }

    // ------------------------------------------------------------
    //  Logika wyboru eksponatu i UI
    // ------------------------------------------------------------

    private void evaluate() {
        Integer key = tracker.evaluate(SystemClock.elapsedRealtime());

        Integer best = tracker.getBestKey();
        if (best == null) {
            debugText.setText("Brak beaconów w zasięgu");
        } else {
            debugText.setText(String.format(
                    Locale.US,
                    "Najsilniejszy beacon: %d / %d  (%.0f dBm)",
                    best >>> 16, best & 0xFFFF, tracker.getBestAverage()
            ));
        }

        // Zamknięty eksponat otworzy się ponownie dopiero po tym,
        // jak odejdziesz (sygnał spadnie poniżej exitRssi).
        if (key == null || !key.equals(dismissedKey)) {
            dismissedKey = null;
        }

        if (openKey == null && key != null && !key.equals(dismissedKey)) {
            Exhibit exhibit = repository.find(key);
            if (exhibit != null) {
                openOverlay(key, exhibit);
            }
        }
    }

    private void openOverlay(int key, Exhibit exhibit) {
        openKey = key;

        overlayTitle.setText(exhibit.title);
        overlayDescription.setText(exhibit.description);
        showImage(exhibit.image);

        overlayScroll.scrollTo(0, 0);
        overlay.setVisibility(View.VISIBLE);
        backCallback.setEnabled(true);
    }

    private void closeOverlay() {
        overlay.setVisibility(View.GONE);
        backCallback.setEnabled(false);
        dismissedKey = openKey;
        openKey = null;
    }

    private void showImage(String name) {
        if (name == null || name.isEmpty()) {
            overlayImage.setVisibility(View.GONE);
            return;
        }

        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getAssets().open("images/" + name)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }

            int target = 1080;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= target) sample *= 2;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;

            Bitmap bitmap;
            try (InputStream in = getAssets().open("images/" + name)) {
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }

            if (bitmap == null) {
                overlayImage.setVisibility(View.GONE);
            } else {
                overlayImage.setImageBitmap(bitmap);
                overlayImage.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            overlayImage.setVisibility(View.GONE);
        }
    }

    private void showProblem(String message, String buttonLabel,
                             Runnable action) {
        statusText.setText(message);
        buttonAction = action;

        if (buttonLabel != null && action != null) {
            actionButton.setText(buttonLabel);
            actionButton.setVisibility(View.VISIBLE);
        } else {
            actionButton.setVisibility(View.GONE);
        }
    }

    private void hideProblem() {
        buttonAction = null;
        actionButton.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------
    //  Paski systemowe (jak w oryginalnym projekcie)
    // ------------------------------------------------------------

    private void configureSystemBars() {
        Window window = getWindow();

        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(DARK);
        window.setNavigationBarColor(DARK);

        WindowCompat.getInsetsController(
                window, window.getDecorView()
        ).setAppearanceLightStatusBars(false);

        WindowCompat.getInsetsController(
                window, window.getDecorView()
        ).setAppearanceLightNavigationBars(false);
    }

    private void applyTopInset(View spacer) {
        ViewCompat.setOnApplyWindowInsetsListener(
                spacer,
                (view, insets) -> {
                    Insets statusInsets = insets.getInsets(
                            WindowInsetsCompat.Type.statusBars()
                                    | WindowInsetsCompat.Type.displayCutout()
                    );

                    ViewGroup.LayoutParams params = view.getLayoutParams();
                    params.height = statusInsets.top;
                    view.setLayoutParams(params);

                    return insets;
                }
        );

        ViewCompat.requestApplyInsets(spacer);
    }

    private void applyBottomInset(View view) {
        final int initialBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(
                view,
                (v, insets) -> {
                    Insets navigationInsets = insets.getInsets(
                            WindowInsetsCompat.Type.navigationBars()
                    );

                    v.setPadding(
                            v.getPaddingLeft(),
                            v.getPaddingTop(),
                            v.getPaddingRight(),
                            initialBottom + navigationInsets.bottom
                    );

                    return insets;
                }
        );

        ViewCompat.requestApplyInsets(view);
    }
}
