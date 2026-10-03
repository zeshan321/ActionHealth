package com.zeshanaslam.actionhealth.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Schedules tasks on Bukkit, Spigot and Paper, and on Folia.
 * <p>
 * Folia has no main thread. Each region of the world has its own thread, and an entity can only
 * be used on the thread of its region. On Folia, this class uses the global region scheduler for
 * repeating tasks and the entity scheduler for work on an entity. The Folia API is called through
 * reflection, because the plugin compiles against the 1.8.8 API.
 */
public final class Scheduler {

    private static final boolean FOLIA = Reflect.findClass("io.papermc.paper.threadedregions.RegionizedServer") != null;

    private static final Method GET_GLOBAL_SCHEDULER = FOLIA ? Reflect.findMethod(Bukkit.class, "getGlobalRegionScheduler") : null;
    private static final Method GLOBAL_RUN_AT_FIXED_RATE = FOLIA ? Reflect.findMethod(
            Reflect.findClass("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler"),
            "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class) : null;

    private static final Method IS_OWNED_BY_CURRENT_REGION = FOLIA
            ? Reflect.findMethod(Bukkit.class, "isOwnedByCurrentRegion", Entity.class) : null;

    private static final Method GET_ENTITY_SCHEDULER = FOLIA ? Reflect.findMethod(Entity.class, "getScheduler") : null;
    private static final Class<?> ENTITY_SCHEDULER = FOLIA
            ? Reflect.findClass("io.papermc.paper.threadedregions.scheduler.EntityScheduler") : null;
    private static final Method ENTITY_RUN = Reflect.findMethod(ENTITY_SCHEDULER, "run", Plugin.class, Consumer.class, Runnable.class);
    private static final Method ENTITY_RUN_DELAYED = Reflect.findMethod(ENTITY_SCHEDULER, "runDelayed",
            Plugin.class, Consumer.class, Runnable.class, long.class);

    private static final Method CANCEL = Reflect.findMethod(
            Reflect.findClass("io.papermc.paper.threadedregions.scheduler.ScheduledTask"), "cancel");

    private Scheduler() {
    }

    /**
     * A scheduled task that can be cancelled.
     */
    public interface Task {
        void cancel();
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    /**
     * Runs the task every period ticks, starting now. The task runs on the main thread, or on the
     * global region thread on Folia. It must not use entities directly: use {@link #runFor} for that.
     */
    public static Task runTimer(Plugin plugin, Runnable task, long period) {
        // Both schedulers need a period of at least 1 tick. Folia also needs a delay of at least 1.
        period = Math.max(1, period);
        if (!FOLIA) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, 0, period);
            return bukkitTask::cancel;
        }

        Consumer<Object> consumer = scheduled -> task.run();
        try {
            return wrap(GLOBAL_RUN_AT_FIXED_RATE.invoke(GET_GLOBAL_SCHEDULER.invoke(null), plugin, consumer, 1L, period));
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Could not schedule a task on Folia", e);
        }
    }

    /**
     * Runs the task on the thread that owns the entity. Outside Folia, callers are already on the
     * main thread, so the task runs now. On Folia, the task runs now if the current thread owns the
     * entity. Otherwise it runs soon on the region thread of the entity. If the entity is removed
     * before that, the task does not run.
     */
    public static void runFor(Plugin plugin, Entity entity, Runnable task) {
        if (!FOLIA || isOwnedByCurrentThread(entity)) {
            task.run();
            return;
        }

        Consumer<Object> consumer = scheduled -> task.run();
        try {
            ENTITY_RUN.invoke(GET_ENTITY_SCHEDULER.invoke(entity), plugin, consumer, null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Could not schedule a task on Folia", e);
        }
    }

    /**
     * Runs the task after delay ticks, on the main thread, or on the region thread of the entity on Folia.
     */
    public static Task runLaterFor(Plugin plugin, Entity entity, Runnable task, long delay) {
        delay = Math.max(1, delay);
        if (!FOLIA) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            return bukkitTask::cancel;
        }

        Consumer<Object> consumer = scheduled -> task.run();
        try {
            return wrap(ENTITY_RUN_DELAYED.invoke(GET_ENTITY_SCHEDULER.invoke(entity), plugin, consumer, null, delay));
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Could not schedule a task on Folia", e);
        }
    }

    private static boolean isOwnedByCurrentThread(Entity entity) {
        if (IS_OWNED_BY_CURRENT_REGION == null) return false;
        try {
            return (boolean) IS_OWNED_BY_CURRENT_REGION.invoke(null, entity);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    // The entity scheduler returns null if the entity was already removed.
    private static Task wrap(Object scheduledTask) {
        return () -> {
            if (scheduledTask == null) return;
            try {
                CANCEL.invoke(scheduledTask);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        };
    }
}
