package com.zeshanaslam.actionhealth.support;

import com.zeshanaslam.actionhealth.utils.Reflect;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Finds the WorldGuard regions at a location through reflection, so no WorldGuard version is needed
 * to compile. WorldGuard 6 (1.8 to 1.12) and WorldGuard 7 (1.13+) have different APIs:
 * <ul>
 *     <li>7: {@code WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world))
 *     .getApplicableRegions(BukkitAdapter.asBlockVector(location))}</li>
 *     <li>6: {@code WorldGuardPlugin.inst().getRegionManager(world).getApplicableRegions(location)}</li>
 * </ul>
 */
public class WorldGuardSupport {

    private static final Class<?> REGION_MANAGER = Reflect.findClass("com.sk89q.worldguard.protection.managers.RegionManager");
    private static final Method GET_ID = Reflect.findMethod(
            Reflect.findClass("com.sk89q.worldguard.protection.regions.ProtectedRegion"), "getId");

    private final Logger logger;
    // Volatile, because Folia checks regions on many threads. The lookups are read only after load().
    private volatile boolean loaded;
    private volatile boolean failureLogged;

    // WorldGuard 7
    private Object regionContainer;
    private Method containerGet;
    private Method adaptWorld;
    private Method asBlockVector;
    private Method getRegionsAtVector;

    // WorldGuard 6
    private Object worldGuardPlugin;
    private Method getRegionManager;
    private Method getRegionsAtLocation;

    public WorldGuardSupport(Logger logger) {
        this.logger = logger;
    }

    /**
     * Returns the IDs of the regions at the location, or an empty list if WorldGuard can not tell.
     */
    public List<String> getRegionIds(Location location) {
        try {
            if (!loaded) ensureLoaded();

            Iterable<?> regions = getRegions(location);
            if (regions == null || GET_ID == null) return Collections.emptyList();

            List<String> ids = new ArrayList<>();
            for (Object region : regions) {
                ids.add((String) GET_ID.invoke(region));
            }
            return ids;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!failureLogged) {
                failureLogged = true;
                logger.log(Level.WARNING, "Could not read the WorldGuard regions. 'Disabled regions' does not work. "
                        + "Please report this at https://github.com/zeshan321/ActionHealth/issues", e);
            }
            return Collections.emptyList();
        }
    }

    private synchronized void ensureLoaded() throws ReflectiveOperationException {
        if (loaded) return;
        try {
            load();
        } finally {
            // Also after a failure: the warning is logged once, and later calls return no regions.
            loaded = true;
        }
    }

    private void load() throws ReflectiveOperationException {
        Class<?> worldGuard = Reflect.findClass("com.sk89q.worldguard.WorldGuard");
        if (worldGuard != null) {
            Object platform = Reflect.call(worldGuard.getMethod("getInstance").invoke(null), "getPlatform");
            regionContainer = Reflect.call(platform, "getRegionContainer");
            Class<?> bukkitAdapter = Reflect.findClass("com.sk89q.worldedit.bukkit.BukkitAdapter");
            adaptWorld = Reflect.findMethod(bukkitAdapter, "adapt", World.class);
            asBlockVector = Reflect.findMethod(bukkitAdapter, "asBlockVector", Location.class);
            containerGet = Reflect.findMethod(Reflect.findClass("com.sk89q.worldguard.protection.regions.RegionContainer"),
                    "get", Reflect.findClass("com.sk89q.worldedit.world.World"));
            getRegionsAtVector = Reflect.findMethod(REGION_MANAGER, "getApplicableRegions",
                    Reflect.findClass("com.sk89q.worldedit.math.BlockVector3"));
            if (regionContainer == null || adaptWorld == null || asBlockVector == null || containerGet == null || getRegionsAtVector == null) {
                throw new IllegalStateException("WorldGuard 7 API not found");
            }
            return;
        }

        Class<?> plugin = Reflect.findClass("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
        Method inst = Reflect.findMethod(plugin, "inst");
        getRegionManager = Reflect.findMethod(plugin, "getRegionManager", World.class);
        getRegionsAtLocation = Reflect.findMethod(REGION_MANAGER, "getApplicableRegions", Location.class);
        if (inst == null || getRegionManager == null || getRegionsAtLocation == null) {
            throw new IllegalStateException("WorldGuard 6 API not found");
        }
        worldGuardPlugin = inst.invoke(null);
    }

    private Iterable<?> getRegions(Location location) throws ReflectiveOperationException {
        if (location.getWorld() == null) return null;

        Object regions;
        if (regionContainer != null) {
            Object manager = containerGet.invoke(regionContainer, adaptWorld.invoke(null, location.getWorld()));
            // Null if regions are turned off in this world.
            if (manager == null) return null;
            regions = getRegionsAtVector.invoke(manager, asBlockVector.invoke(null, location));
        } else {
            Object manager = getRegionManager.invoke(worldGuardPlugin, location.getWorld());
            if (manager == null) return null;
            regions = getRegionsAtLocation.invoke(manager, location);
        }

        return regions instanceof Iterable ? (Iterable<?>) regions : null;
    }
}
