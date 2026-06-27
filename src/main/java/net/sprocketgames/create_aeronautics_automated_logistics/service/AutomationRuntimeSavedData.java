package net.sprocketgames.create_aeronautics_automated_logistics.service;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockingRuntime;

public class AutomationRuntimeSavedData extends SavedData {
    private static final String DATA_NAME = "create_aeronautics_automated_logistics_runtime";
    private static final String PLAYBACK = "Playback";
    private static final String SCHEDULES = "Schedules";
    private static final String ACTIVE_PLAYBACKS = "activePlaybacks";
    private static final String ACTIVE_SCHEDULES = "activeSchedules";

    private CompoundTag playbackTag = new CompoundTag();
    private CompoundTag scheduleTag = new CompoundTag();
    private int lastLoggedPlaybackCount = -1;
    private int lastLoggedScheduleCount = -1;

    public static SavedData.Factory<AutomationRuntimeSavedData> factory() {
        return new SavedData.Factory<>(AutomationRuntimeSavedData::new, AutomationRuntimeSavedData::load);
    }

    public static AutomationRuntimeSavedData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(factory(), DATA_NAME);
    }

    public static void capture(MinecraftServer server) {
        capture(server, false);
    }

    public static void captureForShutdown(MinecraftServer server) {
        capture(server, true);
    }

    private static void capture(MinecraftServer server, boolean forceLog) {
        AutomationRuntimeSavedData data = get(server);
        CompoundTag nextPlaybackTag = AutomatedLogisticsServices.PLAYBACK.saveRuntime();
        CompoundTag nextScheduleTag = AutomatedLogisticsServices.SCHEDULES.saveRuntime();
        boolean changed = !nextPlaybackTag.equals(data.playbackTag) || !nextScheduleTag.equals(data.scheduleTag);
        data.playbackTag = nextPlaybackTag;
        data.scheduleTag = nextScheduleTag;
        if (changed) {
            data.setDirty();
        }
        data.logSnapshot(forceLog ? "captured before shutdown" : "captured", changed || forceLog);
    }

    private static AutomationRuntimeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        AutomationRuntimeSavedData data = new AutomationRuntimeSavedData();
        data.playbackTag = tag.getCompound(PLAYBACK).copy();
        data.scheduleTag = tag.getCompound(SCHEDULES).copy();
        data.logSnapshot("loaded from disk", true);
        return data;
    }

    public void apply(MinecraftServer server) {
        logSnapshot("applying to server", true);
        DockingRuntime.resetRuntimeState("automation_runtime_apply");
        AutomatedLogisticsServices.SCHEDULES.loadRuntime(server, scheduleTag);
        AutomatedLogisticsServices.PLAYBACK.loadRuntime(server, playbackTag);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        logSnapshot("writing to disk", true);
        tag.put(PLAYBACK, playbackTag.copy());
        tag.put(SCHEDULES, scheduleTag.copy());
        return tag;
    }

    private void logSnapshot(String action, boolean force) {
        int playbackCount = listSize(playbackTag, ACTIVE_PLAYBACKS);
        int scheduleCount = listSize(scheduleTag, ACTIVE_SCHEDULES);
        if (!force && playbackCount == lastLoggedPlaybackCount && scheduleCount == lastLoggedScheduleCount) {
            return;
        }
        lastLoggedPlaybackCount = playbackCount;
        lastLoggedScheduleCount = scheduleCount;
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Automation runtime {}: {} active playback(s), {} active schedule(s)",
                action,
                playbackCount,
                scheduleCount
        );
    }

    private static int listSize(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key, Tag.TAG_LIST)) {
            return 0;
        }
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        return list.size();
    }
}
