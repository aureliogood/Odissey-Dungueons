package com.odisseymc.odisseydungueons.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a wave of enemies within a dungeon.
 */
public class WaveConfig {

    private final int waveNumber;
    private final List<MobConfig> mobs;
    private final int delaySeconds;
    private final String announcement;

    public WaveConfig(int waveNumber, List<MobConfig> mobs, int delaySeconds, String announcement) {
        this.waveNumber = waveNumber;
        this.mobs = mobs != null ? mobs : new ArrayList<>();
        this.delaySeconds = Math.max(0, delaySeconds);
        this.announcement = announcement != null ? announcement : "&6&lOLEADA &e" + waveNumber;
    }

    public WaveConfig(int waveNumber, int delaySeconds, String announcement) {
        this(waveNumber, new ArrayList<>(), delaySeconds, announcement);
    }

    public int getWaveNumber() {
        return waveNumber;
    }

    public List<MobConfig> getMobs() {
        return mobs;
    }

    public int getDelaySeconds() {
        return delaySeconds;
    }

    public String getAnnouncement() {
        return announcement;
    }

    public int getTotalExpectedMobCount() {
        int sum = 0;
        for (MobConfig mob : mobs) {
            sum += mob.getAmount();
        }
        return sum;
    }
}
