package net.wlodek.geopurge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Uśrednia RSSI z ostatnich kilku sekund i wybiera najbliższy beacon
 * z histerezą (inny próg wejścia i wyjścia), żeby treść nie migała.
 */
public class BeaconTracker {

    private static final long WINDOW_MS = 3000;
    private static final double SWITCH_MARGIN_DB = 6;

    private final int enterRssi;
    private final int exitRssi;

    private final Map<Integer, ArrayDeque<long[]>> samples = new HashMap<>();

    private Integer current = null;
    private Integer bestKey = null;
    private double bestAvg = 0;

    public BeaconTracker(int enterRssi, int exitRssi) {
        this.enterRssi = enterRssi;
        this.exitRssi = exitRssi;
    }

    public void add(int key, int rssi, long now) {
        ArrayDeque<long[]> queue = samples.get(key);
        if (queue == null) {
            queue = new ArrayDeque<>();
            samples.put(key, queue);
        }
        queue.addLast(new long[]{now, rssi});
    }

    private Double average(int key, long now) {
        ArrayDeque<long[]> queue = samples.get(key);
        if (queue == null) return null;

        while (!queue.isEmpty() && now - queue.peekFirst()[0] > WINDOW_MS) {
            queue.pollFirst();
        }
        if (queue.isEmpty()) {
            samples.remove(key);
            return null;
        }

        double sum = 0;
        for (long[] s : queue) sum += s[1];
        return sum / queue.size();
    }

    /** Zwraca klucz aktualnego eksponatu albo null, gdy nic nie jest blisko. */
    public Integer evaluate(long now) {
        bestKey = null;
        bestAvg = -999;

        for (Integer key : new ArrayList<>(samples.keySet())) {
            Double avg = average(key, now);
            if (avg != null && avg > bestAvg) {
                bestAvg = avg;
                bestKey = key;
            }
        }

        if (current != null) {
            Double currentAvg = average(current, now);

            if (currentAvg == null || currentAvg < exitRssi) {
                current = null;
            } else if (bestKey != null
                    && !bestKey.equals(current)
                    && bestAvg >= enterRssi
                    && bestAvg >= currentAvg + SWITCH_MARGIN_DB) {
                current = bestKey;
            }
        }

        if (current == null && bestKey != null && bestAvg >= enterRssi) {
            current = bestKey;
        }

        return current;
    }

    public Integer getBestKey() {
        return bestKey;
    }

    public double getBestAverage() {
        return bestAvg;
    }
}
