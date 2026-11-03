package net.wlodek.geopurge;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
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

public class MainActivity extends AppCompatActivity
        implements LocationListener {

    private static final int LOCATION_PERMISSION = 100;

    private static final String SOURCE_ID = "user-location";
    private static final String LAYER_ID = "user-location-layer";

    private MapView mapView;
    private MapLibreMap map;
    private LocationManager locationManager;

    private boolean firstLocation = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MapLibre.getInstance(this);

        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        locationManager = (LocationManager)
                getSystemService(LOCATION_SERVICE);

        mapView.getMapAsync(googleMap -> {

            map = googleMap;

            map.setCameraPosition(
                    new CameraPosition.Builder()
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

                        checkLocationPermission();
                    }
            );
        });
    }

    // Sprawdzenie uprawnień lokalizacji

    private void checkLocationPermission() {

        boolean fine = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean coarse = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
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

    // Uruchomienie lokalizacji

    private void startGps() {

        boolean fine = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean coarse = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (!fine && !coarse) return;

        try {

            String provider = fine
                    ? LocationManager.GPS_PROVIDER
                    : LocationManager.NETWORK_PROVIDER;

            locationManager.requestLocationUpdates(
                    provider,
                    3000,
                    5,
                    this
            );

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

    // Aktualizacja pozycji użytkownika

    @Override
    public void onLocationChanged(@NonNull Location location) {

        if (map == null) return;

        Style style = map.getStyle();

        if (style == null) return;

        GeoJsonSource source =
                style.getSourceAs(SOURCE_ID);

        if (source == null) return;

        Point point = Point.fromLngLat(
                location.getLongitude(),
                location.getLatitude()
        );

        source.setGeoJson(
                Feature.fromGeometry(point)
        );

        // Pierwsze ustawienie kamery na użytkownika

        if (firstLocation) {

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

    // Obsługa zgody na lokalizację

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
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

    // Cykl życia mapy

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {

        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }

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
    protected void onSaveInstanceState(
            @NonNull Bundle outState) {

        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}