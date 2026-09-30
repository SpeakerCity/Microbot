package net.runelite.client.plugins.microbot.corruptedgauntlet;

import net.runelite.api.Skill;
import java.util.EnumMap;
import java.util.Map;

/** Updated by the script executor. The overlay receives an immutable view of the totals. */
final class GauntletSession {
    private final Map<Skill, Integer> previous = new EnumMap<>(Skill.class);
    long xpGained, profit, started;
    int wins, attempts;
    void reset() { previous.clear(); xpGained = profit = started = 0; wins = attempts = 0; }
    void disconnected() { previous.clear(); }
    void sample(Map<Skill, Integer> current, long now) {
        if (started == 0) started = now;
        for (Map.Entry<Skill, Integer> entry : current.entrySet()) {
            if (entry.getKey() == Skill.OVERALL) continue;
            Integer before = previous.put(entry.getKey(), entry.getValue());
            if (before != null) xpGained += Math.max(0L, (long) entry.getValue() - before);
        }
    }
    long hourly(long value, long now) {
        return started == 0 ? 0 : (long) (value * 3_600_000.0 / Math.max(1000, now - started));
    }
}
