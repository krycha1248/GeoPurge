
package net.wlodek.geopurge;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.Point;

import static org.maplibre.android.style.layers.PropertyFactory.circleColor;
import static org.maplibre.android.style.layers.PropertyFactory.circleRadius;
import static org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor;
import static org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth;

public class MainActivity extends AppCompatActivity implements LocationListener {

    public static final String EXTRA_PICK_LOCATION = "pick_zone_location";

    private static final int LOCATION_PERMISSION = 100;
    private static final String SOURCE_ID = "user-location";
    private static final String LAYER_ID = "user-location-layer";
    private static final int DARK = Color.rgb(32, 42, 53);

    private MapView mapView;
    private MapLibreMap map;
    private LocationManager locationManager;

    private boolean firstLocation = true;
    private boolean mapReady = false;
    private boolean locationUpdatesStarted = false;
    private boolean pickingLocation = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        configureSystemBars();
        MapLibre.getInstance(this);
        setContentView(R.layout.activity_main);

        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);

        applyTopInset(findViewById(R.id.statusBarSpacer));
        applyTopInset(findViewById(R.id.drawerStatusBarSpacer));
        applyBottomInset(findViewById(R.id.mainRoot));

        TextView menuButton = findViewById(R.id.menuButton);
        TextView navMap = findViewById(R.id.navMap);
        TextView navFilesDeletion = findViewById(R.id.navFilesDeletion);

        menuButton.setOnClickListener(v ->
                drawerLayout.openDrawer(Gravity.START));

        navMap.setOnClickListener(v ->
                drawerLayout.closeDrawer(Gravity.START));

        navFilesDeletion.setOnClickListener(v -> {
            drawerLayout.closeDrawer(Gravity.START);
            startActivity(new Intent(
                    MainActivity.this,
                    FilesDeletionActivity.class
            ));
            overridePendingTransition(0, 0);
        });

        pickingLocation = getIntent().getBooleanExtra(
                EXTRA_PICK_LOCATION, false
        );

        if (pickingLocation) {
            TextView title = findViewById(R.id.menuButton);
            title.setContentDescription("Dotknij mapy, aby wybrać lokalizację");
            Toast.makeText(
                    this,
                    "Dotknij mapy, aby wybrać lokalizację strefy",
                    Toast.LENGTH_LONG
            ).show();
        }

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        locationManager = (LocationManager)
                getSystemService(LOCATION_SERVICE);

        mapView.getMapAsync(mapLibreMap -> {
            map = mapLibreMap;

            map.setCameraPosition(
                    new org.maplibre.android.camera.CameraPosition.Builder()
                            .target(new LatLng(52.0, 19.0))
                            .zoom(6)
                            .build()
            );

            map.setStyle(
                    new Style.Builder().fromUri(
                            "https://tiles.openfreemap.org/styles/liberty"
                    ),
                    style -> {
                        GeoJsonSource source = new GeoJsonSource(
                                SOURCE_ID,
                                Feature.fromGeometry(
                                        Point.fromLngLat(19.0, 52.0)
                                )
                        );

                        style.addSource(source);

                        CircleLayer layer =
                                new CircleLayer(LAYER_ID, SOURCE_ID);

                        layer.withProperties(
                                circleRadius(8f),
                                circleColor("#1769FF"),
                                circleStrokeColor("#FFFFFF"),
                                circleStrokeWidth(2f)
                        );

                        style.addLayer(layer);

                        mapReady = true;

                        map.addOnMapClickListener(point -> {
                            if (pickingLocation) {
                                Intent result = new Intent();
                                result.putExtra("latitude", point.getLatitude());
                                result.putExtra("longitude", point.getLongitude());

                                setResult(Activity.RESULT_OK, result);
                                finish();
                                overridePendingTransition(0, 0);
                                return true;
                            }

                            return false;
                        });

                        if (!pickingLocation) {
                            checkLocationPermission();
                        }
                    }
            );
        });
    }

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

    private void checkLocationPermission() {
        boolean fine = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean coarse = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (fine || coarse) {
            startGps();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION
            );
        }
    }

    private void startGps() {
        if (!mapReady || locationManager == null || pickingLocation) {
            return;
        }

        boolean fine = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean coarse = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (!fine && !coarse) return;
        if (locationUpdatesStarted) return;

        try {
            String provider = fine
                    ? LocationManager.GPS_PROVIDER
                    : LocationManager.NETWORK_PROVIDER;

            locationManager.requestLocationUpdates(
                    provider, 3000, 5, this
            );

            locationUpdatesStarted = true;

            Location lastLocation =
                    locationManager.getLastKnownLocation(provider);

            if (lastLocation != null) {
                onLocationChanged(lastLocation);
            }

        } catch (SecurityException e) {
            Toast.makeText(
                    this,
                    "Nie można pobrać lokalizacji",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void stopGps() {
        if (locationManager != null && locationUpdatesStarted) {
            locationManager.removeUpdates(this);
            locationUpdatesStarted = false;
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (map == null) return;

        Style style = map.getStyle();
        if (style == null) return;

        GeoJsonSource source = style.getSourceAs(SOURCE_ID);
        if (source == null) return;

        Point point = Point.fromLngLat(
                location.getLongitude(),
                location.getLatitude()
        );

        source.setGeoJson(Feature.fromGeometry(point));

        if (firstLocation && !pickingLocation) {
            map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                            new LatLng(
                                    location.getLatitude(),
                                    location.getLongitude()
                            ),
                            16
                    )
            );

            firstLocation = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults
        );

        if (requestCode == LOCATION_PERMISSION) {
            boolean granted = false;

            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }

            if (granted) {
                startGps();
            } else {
                Toast.makeText(
                        this,
                        "Brak dostępu do lokalizacji",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();

        if (mapReady && !pickingLocation) {
            startGps();
        }
    }

    @Override
    protected void onPause() {
        stopGps();
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        mapView.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mapView.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}
