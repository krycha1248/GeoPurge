
package net.wlodek.geopurge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class FilesDeletionActivity extends AppCompatActivity {

    private static final String PREFS = "geopurge_config";
    private static final String KEY_ZONES = "zones";

    private static final int REQUEST_LOCATION = 201;
    private static final int REQUEST_FILES = 202;
    private static final int REQUEST_FOLDER = 203;

    private static final int DARK = Color.rgb(32, 42, 53);
    private static final int TEXT = 0xFF263442;
    private static final int MUTED = 0xFF71808E;
    private static final int BACKGROUND = 0xFFF3F5F7;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int ACCENT = 0xFF2785D8;

    private LinearLayout zonesContainer;
    private final ArrayList<Zone> zones = new ArrayList<>();

    private int selectedZoneIndex = -1;

    private static class Zone {
        String name;
        int radius;
        double latitude;
        double longitude;
        boolean hasLocation;
        ArrayList<String> files = new ArrayList<>();

        Zone(String name, int radius) {
            this.name = name;
            this.radius = radius;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        configureSystemBars();
        loadZones();
        createScreen();
        refreshZones();
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

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private TextView makeText(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private Button makeButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(WHITE);
        button.setBackgroundTintList(
                ColorStateList.valueOf(ACCENT)
        );
        return button;
    }

    private void createScreen() {
        DrawerLayout drawer = new DrawerLayout(this);
        drawer.setBackgroundColor(DARK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        drawer.addView(root, new DrawerLayout.LayoutParams(
                DrawerLayout.LayoutParams.MATCH_PARENT,
                DrawerLayout.LayoutParams.MATCH_PARENT
        ));

        View statusBarSpacer = new View(this);
        statusBarSpacer.setBackgroundColor(DARK);

        root.addView(statusBarSpacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0
        ));
        applyTopInset(statusBarSpacer);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(DARK);
        toolbar.setPadding(dp(12), 0, dp(12), 0);

        TextView menuButton = makeText("☰", 26, WHITE);
        menuButton.setGravity(Gravity.CENTER);
        toolbar.addView(menuButton, new LinearLayout.LayoutParams(
                dp(48), LinearLayout.LayoutParams.MATCH_PARENT
        ));

        TextView appName = makeText("GeoPurge", 20, WHITE);
        appName.setTypeface(null, 1);
        toolbar.addView(appName);

        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
        ));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(12));
        content.setBackgroundColor(BACKGROUND);

        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
        ));

        TextView title = makeText("Files Deletion", 25, DARK);
        title.setTypeface(null, 1);
        content.addView(title);

        TextView description = makeText(
                "Manage your deletion zones and assigned files.",
                14, MUTED
        );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        descriptionParams.topMargin = dp(6);
        content.addView(description, descriptionParams);

        Button addButton = makeButton("+ Add new zone");

        LinearLayout.LayoutParams addParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
                );
        addParams.topMargin = dp(20);
        content.addView(addButton, addParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        zonesContainer = new LinearLayout(this);
        zonesContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(zonesContainer);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
                );
        scrollParams.topMargin = dp(12);
        content.addView(scrollView, scrollParams);

        LinearLayout navigationDrawer = new LinearLayout(this);
        navigationDrawer.setOrientation(LinearLayout.VERTICAL);
        navigationDrawer.setBackgroundColor(DARK);

        DrawerLayout.LayoutParams drawerParams =
                new DrawerLayout.LayoutParams(
                        dp(280),
                        DrawerLayout.LayoutParams.MATCH_PARENT
                );
        drawerParams.gravity = Gravity.START;

        View drawerStatusBarSpacer = new View(this);
        drawerStatusBarSpacer.setBackgroundColor(DARK);

        navigationDrawer.addView(
                drawerStatusBarSpacer,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0
                )
        );
        applyTopInset(drawerStatusBarSpacer);

        TextView drawerTitle = makeText("GeoPurge", 24, WHITE);
        drawerTitle.setTypeface(null, 1);
        drawerTitle.setGravity(Gravity.CENTER_VERTICAL);
        drawerTitle.setPadding(dp(20), 0, dp(20), 0);

        navigationDrawer.addView(drawerTitle,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(80)
                ));

        View separator = new View(this);
        separator.setBackgroundColor(0xFF45515E);
        navigationDrawer.addView(separator,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                ));

        TextView navMap = makeText("⌖   Map", 17, WHITE);
        navMap.setGravity(Gravity.CENTER_VERTICAL);
        navMap.setPadding(dp(24), 0, dp(24), 0);
        navigationDrawer.addView(navMap,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
                ));

        TextView navFiles = makeText("▤   Files Deletion", 17, WHITE);
        navFiles.setGravity(Gravity.CENTER_VERTICAL);
        navFiles.setPadding(dp(24), 0, dp(24), 0);
        navigationDrawer.addView(navFiles,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
                ));

        drawer.addView(navigationDrawer, drawerParams);

        applyBottomInset(root);
        applyBottomInset(navigationDrawer);

        setContentView(drawer);
        ViewCompat.requestApplyInsets(drawer);

        menuButton.setOnClickListener(v -> {
            if (drawer.isDrawerOpen(Gravity.START)) {
                drawer.closeDrawer(Gravity.START);
            } else {
                drawer.openDrawer(Gravity.START);
            }
        });

        navMap.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
        });

        navFiles.setOnClickListener(v ->
                drawer.closeDrawer(Gravity.START));

        addButton.setOnClickListener(v -> showZoneDialog(-1));
    }

    private void refreshZones() {
        zonesContainer.removeAllViews();

        if (zones.isEmpty()) {
            TextView empty = makeText(
                    "No zones configured yet.", 16, MUTED
            );
            empty.setPadding(dp(16), dp(24), dp(16), dp(24));
            zonesContainer.addView(empty);
            return;
        }

        for (int i = 0; i < zones.size(); i++) {
            final int index = i;
            Zone zone = zones.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(18), dp(16), dp(18), dp(16));
            card.setBackgroundColor(WHITE);

            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
            cardParams.bottomMargin = dp(12);
            card.setLayoutParams(cardParams);

            TextView name = makeText(zone.name, 19, TEXT);
            name.setTypeface(null, 1);
            card.addView(name);

            TextView radius = makeText(
                    "Radius: " + zone.radius + " m", 14, MUTED
            );
            addTop(card, radius, 8);

            String locationText = zone.hasLocation
                    ? String.format(
                    java.util.Locale.US,
                    "Location: %.6f, %.6f",
                    zone.latitude, zone.longitude
            )
                    : "Location not selected";

            TextView location = makeText(locationText, 13, MUTED);
            addTop(card, location, 6);

            TextView files = makeText(
                    "Assigned items: " + zone.files.size(), 14, MUTED
            );
            addTop(card, files, 6);

            Button chooseLocation = makeButton("Choose location on map");
            addTop(card, chooseLocation, 10);

            chooseLocation.setOnClickListener(v -> chooseLocation(index));

            Button addFiles = makeButton("Add files");
            addTop(card, addFiles, 6);
            addFiles.setOnClickListener(v -> chooseFiles(index));

            Button addFolder = makeButton("Add folder");
            addTop(card, addFolder, 6);
            addFolder.setOnClickListener(v -> chooseFolder(index));

            Button showFiles = makeButton("View assigned items");
            addTop(card, showFiles, 6);
            showFiles.setOnClickListener(v -> showAssignedItems(index));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.END);

            Button edit = new Button(this);
            edit.setText("Edit");
            actions.addView(edit);

            Button delete = new Button(this);
            delete.setText("Delete");
            delete.setTextColor(0xFFB83232);
            actions.addView(delete);

            addTop(card, actions, 8);

            edit.setOnClickListener(v -> showZoneDialog(index));
            delete.setOnClickListener(v -> confirmDelete(index));

            zonesContainer.addView(card);
        }
    }

    private void addTop(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        params.topMargin = dp(margin);
        parent.addView(child, params);
    }

    private void showZoneDialog(int index) {
        boolean editing = index >= 0;

        EditText nameInput = new EditText(this);
        nameInput.setHint("Zone name");

        EditText radiusInput = new EditText(this);
        radiusInput.setHint("Radius in meters");
        radiusInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        if (editing) {
            Zone zone = zones.get(index);
            nameInput.setText(zone.name);
            radiusInput.setText(String.valueOf(zone.radius));
        } else {
            radiusInput.setText("100");
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);
        form.addView(nameInput);
        form.addView(radiusInput);

        new AlertDialog.Builder(this)
                .setTitle(editing ? "Edit zone" : "New zone")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String radiusText = radiusInput.getText()
                            .toString().trim();

                    if (name.isEmpty() || radiusText.isEmpty()) {
                        Toast.makeText(
                                this, "Enter a name and radius.",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    try {
                        int radius = Integer.parseInt(radiusText);

                        if (radius < 1 || radius > 10000) {
                            Toast.makeText(
                                    this,
                                    "Radius must be between 1 and 10000 m.",
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }

                        if (editing) {
                            zones.get(index).name = name;
                            zones.get(index).radius = radius;
                        } else {
                            zones.add(new Zone(name, radius));
                        }

                        saveZones();
                        refreshZones();

                    } catch (NumberFormatException e) {
                        Toast.makeText(
                                this, "Invalid radius.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .show();
    }

    private void chooseLocation(int index) {
        selectedZoneIndex = index;

        Intent intent = new Intent(
                this, MainActivity.class
        );
        intent.putExtra(MainActivity.EXTRA_PICK_LOCATION, true);

        startActivityForResult(intent, REQUEST_LOCATION);
        overridePendingTransition(0, 0);
    }

    private void chooseFiles(int index) {
        selectedZoneIndex = index;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(intent, REQUEST_FILES);
    }

    private void chooseFolder(int index) {
        selectedZoneIndex = index;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(intent, REQUEST_FOLDER);
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }

        if (selectedZoneIndex < 0
                || selectedZoneIndex >= zones.size()) {
            return;
        }

        Zone zone = zones.get(selectedZoneIndex);

        if (requestCode == REQUEST_LOCATION) {
            zone.latitude = data.getDoubleExtra("latitude", 0);
            zone.longitude = data.getDoubleExtra("longitude", 0);
            zone.hasLocation = true;

            saveZones();
            refreshZones();

            Toast.makeText(
                    this, "Zone location saved.",
                    Toast.LENGTH_SHORT
            ).show();
        }

        if (requestCode == REQUEST_FILES) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();

                for (int i = 0; i < count; i++) {
                    Uri uri = data.getClipData()
                            .getItemAt(i).getUri();
                    saveUriPermission(data, uri);
                    addUri(zone, uri);
                }

            } else if (data.getData() != null) {
                Uri uri = data.getData();
                saveUriPermission(data, uri);
                addUri(zone, uri);
            }

            saveZones();
            refreshZones();

            Toast.makeText(
                    this, "Selected files assigned to zone.",
                    Toast.LENGTH_SHORT
            ).show();
        }

        if (requestCode == REQUEST_FOLDER) {
            Uri uri = data.getData();

            if (uri != null) {
                saveUriPermission(data, uri);
                addUri(zone, uri);
                saveZones();
                refreshZones();

                Toast.makeText(
                        this, "Folder assigned to zone.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    private void saveUriPermission(Intent data, Uri uri) {
        try {
            int flags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            getContentResolver().takePersistableUriPermission(
                    uri, flags
            );
        } catch (Exception ignored) {
            // Niektóre dostawcy dokumentów nie obsługują
            // trwałych uprawnień. URI nadal zapisujemy.
        }
    }

    private void addUri(Zone zone, Uri uri) {
        String value = uri.toString();

        if (!zone.files.contains(value)) {
            zone.files.add(value);
        }
    }

    private void showAssignedItems(int index) {
        Zone zone = zones.get(index);

        if (zone.files.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Assigned items")
                    .setMessage("No files or folders assigned.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] names = new String[zone.files.size()];

        for (int i = 0; i < zone.files.size(); i++) {
            names[i] = zone.files.get(i);
        }

        new AlertDialog.Builder(this)
                .setTitle("Assigned items")
                .setItems(names, (dialog, which) -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Remove item?")
                            .setMessage(
                                    "Remove this item from the zone?"
                            )
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Remove",
                                    (d, w) -> {
                                        zone.files.remove(which);
                                        saveZones();
                                        refreshZones();
                                    })
                            .show();
                })
                .setPositiveButton("Close", null)
                .show();
    }

    private void confirmDelete(int index) {
        new AlertDialog.Builder(this)
                .setTitle("Delete zone")
                .setMessage(
                        "Do you want to delete zone \""
                                + zones.get(index).name + "\"?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    zones.remove(index);
                    saveZones();
                    refreshZones();
                })
                .show();
    }

    private void saveZones() {
        JSONArray array = new JSONArray();

        try {
            for (Zone zone : zones) {
                JSONObject object = new JSONObject();

                object.put("name", zone.name);
                object.put("radius", zone.radius);
                object.put("hasLocation", zone.hasLocation);
                object.put("latitude", zone.latitude);
                object.put("longitude", zone.longitude);

                JSONArray filesArray = new JSONArray();
                for (String uri : zone.files) {
                    filesArray.put(uri);
                }

                object.put("files", filesArray);
                array.put(object);
            }

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_ZONES, array.toString())
                    .apply();

        } catch (Exception e) {
            Toast.makeText(
                    this, "Could not save zones.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void loadZones() {
        String json = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_ZONES, "[]");

        try {
            JSONArray array = new JSONArray(json);

            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);

                Zone zone = new Zone(
                        object.getString("name"),
                        object.getInt("radius")
                );

                zone.hasLocation =
                        object.optBoolean("hasLocation", false);

                zone.latitude =
                        object.optDouble("latitude", 0);

                zone.longitude =
                        object.optDouble("longitude", 0);

                JSONArray filesArray =
                        object.optJSONArray("files");

                if (filesArray != null) {
                    for (int j = 0; j < filesArray.length(); j++) {
                        zone.files.add(filesArray.getString(j));
                    }
                }

                zones.add(zone);
            }

        } catch (Exception e) {
            zones.clear();
        }
    }
}