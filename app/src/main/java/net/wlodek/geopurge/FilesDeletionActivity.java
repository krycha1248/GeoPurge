
package net.wlodek.geopurge;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
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

    private LinearLayout zonesContainer;

    private final ArrayList<Zone> zones = new ArrayList<>();

    private static final String PREFS = "geopurge_config";
    private static final String KEY_ZONES = "zones";

    private static final int DARK = Color.rgb(32, 42, 53);
    private static final int TEXT = 0xFF263442;
    private static final int MUTED = 0xFF71808E;
    private static final int BACKGROUND = 0xFFF3F5F7;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int ACCENT = 0xFF2785D8;

    private static class Zone {

        String name;
        int radius;

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
                window,
                window.getDecorView()
        ).setAppearanceLightStatusBars(false);

        WindowCompat.getInsetsController(
                window,
                window.getDecorView()
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

                    ViewGroup.LayoutParams params =
                            view.getLayoutParams();

                    params.height = statusInsets.top;
                    view.setLayoutParams(params);

                    return insets;
                }
        );
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

    private void createScreen() {

        DrawerLayout drawer = new DrawerLayout(this);
        drawer.setBackgroundColor(DARK);

        // GŁÓWNY ROOT

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        DrawerLayout.LayoutParams rootParams =
                new DrawerLayout.LayoutParams(
                        DrawerLayout.LayoutParams.MATCH_PARENT,
                        DrawerLayout.LayoutParams.MATCH_PARENT
                );

        drawer.addView(root, rootParams);

        // STATUS BAR

        View statusBarSpacer = new View(this);
        statusBarSpacer.setBackgroundColor(DARK);

        root.addView(
                statusBarSpacer,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0
                )
        );

        applyTopInset(statusBarSpacer);

        // TOOLBAR

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(DARK);
        toolbar.setPadding(dp(12), 0, dp(12), 0);

        toolbar.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(56)
                )
        );

        TextView menuButton = makeText("☰", 26, WHITE);
        menuButton.setGravity(Gravity.CENTER);
        menuButton.setClickable(true);
        menuButton.setFocusable(true);

        toolbar.addView(
                menuButton,
                new LinearLayout.LayoutParams(
                        dp(48),
                        LinearLayout.LayoutParams.MATCH_PARENT
                )
        );

        TextView appName = makeText("GeoPurge", 20, WHITE);
        appName.setTypeface(null, 1);

        toolbar.addView(appName);

        root.addView(toolbar);

        // ZAWARTOŚĆ

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(12));
        content.setBackgroundColor(BACKGROUND);

        root.addView(
                content,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        TextView title = makeText("Files Deletion", 25, DARK);
        title.setTypeface(null, 1);

        content.addView(title);

        TextView description = makeText(
                "Manage your deletion zones and assigned files.",
                14,
                MUTED
        );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        descriptionParams.topMargin = dp(6);

        content.addView(description, descriptionParams);

        // PRZYCISK DODAWANIA

        Button addButton = new Button(this);
        addButton.setText("+ Add new zone");
        addButton.setTextColor(WHITE);
        addButton.setBackgroundTintList(
                ColorStateList.valueOf(ACCENT)
        );

        LinearLayout.LayoutParams addParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                );

        addParams.topMargin = dp(20);

        content.addView(addButton, addParams);

        // LISTA STREF

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                );

        scrollParams.topMargin = dp(12);

        zonesContainer = new LinearLayout(this);
        zonesContainer.setOrientation(LinearLayout.VERTICAL);

        scrollView.addView(zonesContainer);

        content.addView(scrollView, scrollParams);

        // MENU BOCZNE

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
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0
                )
        );

        applyTopInset(drawerStatusBarSpacer);

        TextView drawerTitle = makeText("GeoPurge", 24, WHITE);
        drawerTitle.setTypeface(null, 1);
        drawerTitle.setGravity(Gravity.CENTER_VERTICAL);
        drawerTitle.setPadding(dp(20), 0, dp(20), 0);

        navigationDrawer.addView(
                drawerTitle,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(80)
                )
        );

        View separator = new View(this);
        separator.setBackgroundColor(0xFF45515E);

        navigationDrawer.addView(
                separator,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1)
                )
        );

        TextView navMap = makeText("⌖   Map", 17, WHITE);
        navMap.setGravity(Gravity.CENTER_VERTICAL);
        navMap.setPadding(dp(24), 0, dp(24), 0);

        navigationDrawer.addView(
                navMap,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(56)
                )
        );

        TextView navFiles = makeText(
                "▤   Files Deletion",
                17,
                WHITE
        );

        navFiles.setGravity(Gravity.CENTER_VERTICAL);
        navFiles.setPadding(dp(24), 0, dp(24), 0);

        navigationDrawer.addView(
                navFiles,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(56)
                )
        );

        drawer.addView(navigationDrawer, drawerParams);

        // INSETY

        applyBottomInset(root);
        applyBottomInset(navigationDrawer);

        setContentView(drawer);

        ViewCompat.requestApplyInsets(drawer);

        // HAMBURGER

        menuButton.setOnClickListener(v -> {

            if (drawer.isDrawerOpen(Gravity.START)) {
                drawer.closeDrawer(Gravity.START);
            } else {
                drawer.openDrawer(Gravity.START);
            }
        });

        // POWRÓT DO ISTNIEJĄCEJ MAPY

        navMap.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
        });

        // AKTUALNY EKRAN

        navFiles.setOnClickListener(v ->
                drawer.closeDrawer(Gravity.START)
        );

        addButton.setOnClickListener(v ->
                showZoneDialog(-1)
        );
    }

    // WYŚWIETLANIE STREF

    private void refreshZones() {

        zonesContainer.removeAllViews();

        if (zones.isEmpty()) {

            LinearLayout emptyCard = new LinearLayout(this);
            emptyCard.setOrientation(LinearLayout.VERTICAL);
            emptyCard.setPadding(
                    dp(20), dp(24), dp(20), dp(24)
            );
            emptyCard.setBackgroundColor(WHITE);

            TextView empty = makeText(
                    "No zones configured yet.",
                    16,
                    MUTED
            );

            emptyCard.addView(empty);
            zonesContainer.addView(emptyCard);

            return;
        }

        for (int i = 0; i < zones.size(); i++) {

            final int index = i;
            Zone zone = zones.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(
                    dp(18), dp(16), dp(18), dp(16)
            );
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
                    "Radius: " + zone.radius + " m",
                    14,
                    MUTED
            );

            LinearLayout.LayoutParams radiusParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            radiusParams.topMargin = dp(8);

            card.addView(radius, radiusParams);

            TextView files = makeText(
                    "No files assigned",
                    14,
                    MUTED
            );

            LinearLayout.LayoutParams filesParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            filesParams.topMargin = dp(6);

            card.addView(files, filesParams);

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

            LinearLayout.LayoutParams actionsParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            actionsParams.topMargin = dp(8);

            card.addView(actions, actionsParams);

            edit.setOnClickListener(v ->
                    showZoneDialog(index)
            );

            delete.setOnClickListener(v ->
                    confirmDelete(index)
            );

            zonesContainer.addView(card);
        }
    }

    // DODAWANIE I EDYCJA STREF

    private void showZoneDialog(int index) {

        boolean editing = index >= 0;

        EditText nameInput = new EditText(this);
        nameInput.setHint("Zone name");

        EditText radiusInput = new EditText(this);
        radiusInput.setHint("Radius in meters");
        radiusInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        if (editing) {

            nameInput.setText(zones.get(index).name);

            radiusInput.setText(
                    String.valueOf(zones.get(index).radius)
            );

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

                    String name = nameInput.getText()
                            .toString().trim();

                    String radiusText = radiusInput.getText()
                            .toString().trim();

                    if (name.isEmpty() || radiusText.isEmpty()) {

                        Toast.makeText(
                                this,
                                "Enter a name and radius.",
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
                                this,
                                "Invalid radius.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .show();
    }

    // USUWANIE STREF

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

    // ZAPISYWANIE STREF

    private void saveZones() {

        JSONArray array = new JSONArray();

        try {

            for (Zone zone : zones) {

                JSONObject object = new JSONObject();

                object.put("name", zone.name);
                object.put("radius", zone.radius);

                array.put(object);
            }

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_ZONES, array.toString())
                    .apply();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Could not save zones.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // WCZYTYWANIE STREF

    private void loadZones() {

        String json = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_ZONES, "[]");

        try {

            JSONArray array = new JSONArray(json);

            for (int i = 0; i < array.length(); i++) {

                JSONObject object = array.getJSONObject(i);

                zones.add(new Zone(
                        object.getString("name"),
                        object.getInt("radius")
                ));
            }

        } catch (Exception e) {

            zones.clear();
        }
    }
}
