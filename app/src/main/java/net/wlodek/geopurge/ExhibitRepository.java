package net.wlodek.geopurge;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Wczytuje assets/exhibits.json: UUID beaconów, progi sygnału i eksponaty. */
public class ExhibitRepository {

    public final byte[] uuidBytes;
    public final int enterRssi;
    public final int exitRssi;

    private final Map<Integer, Exhibit> byKey = new HashMap<>();

    private ExhibitRepository(byte[] uuidBytes, int enterRssi, int exitRssi) {
        this.uuidBytes = uuidBytes;
        this.enterRssi = enterRssi;
        this.exitRssi = exitRssi;
    }

    public static int key(int major, int minor) {
        return ((major & 0xFFFF) << 16) | (minor & 0xFFFF);
    }

    public Exhibit find(int key) {
        return byKey.get(key);
    }

    public static ExhibitRepository load(Context context) throws Exception {
        String text;
        try (InputStream in = context.getAssets().open("exhibits.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        }

        JSONObject root = new JSONObject(text);

        String hex = root.getString("uuid").replace("-", "").trim();
        if (hex.length() != 32) {
            throw new IllegalArgumentException("Nieprawidłowy UUID");
        }
        byte[] uuid = new byte[16];
        for (int i = 0; i < 16; i++) {
            uuid[i] = (byte) Integer.parseInt(
                    hex.substring(i * 2, i * 2 + 2), 16);
        }

        ExhibitRepository repo = new ExhibitRepository(
                uuid,
                root.optInt("enterRssi", -65),
                root.optInt("exitRssi", -75)
        );

        JSONArray array = root.getJSONArray("exhibits");
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            Exhibit e = new Exhibit(
                    o.getInt("major"),
                    o.getInt("minor"),
                    o.getString("title"),
                    o.optString("description", ""),
                    o.optString("image", "")
            );
            repo.byKey.put(key(e.major, e.minor), e);
        }

        return repo;
    }
}
