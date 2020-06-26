package com.zeshanaslam.actionhealth.support;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.zeshanaslam.actionhealth.Main;
import org.bukkit.Location;
import org.bukkit.World;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class WorldGuardAPI {
    private final Main main;
    private Object worldGuard = null;
    private WorldGuardPlugin worldGuardPlugin = null;
    private Object regionContainer = null;
    private Method regionContainerGetMethod = null;
    private Method worldAdaptMethod = null;
    private Method regionManagerGetMethod = null;
    private Constructor<?> vectorConstructor = null;
    private Method vectorConstructorAsAMethodBecauseWhyNot = null;
    private boolean initialized = false;

    public WorldGuardAPI(Main main) {
        this.main = main;
        worldGuardPlugin = main.worldGuardPlugin;

        try {
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Method getInstanceMethod = worldGuardClass.getMethod("getInstance");
            worldGuard = getInstanceMethod.invoke(null);
        } catch (Exception e) {
        }
    }

    private void initialize() {
        if (!initialized) {
            initialized = true;
            // Super hacky reflection to deal with differences in WorldGuard 6 and 7+
            if (worldGuard != null) {
                try {
                    Method getPlatFormMethod = worldGuard.getClass().getMethod("getPlatform");
                    Object platform = getPlatFormMethod.invoke(worldGuard);
                    Method getRegionContainerMethod = platform.getClass().getMethod("getRegionContainer");
                    regionContainer = getRegionContainerMethod.invoke(platform);
                    Class<?> worldEditWorldClass = Class.forName("com.sk89q.worldedit.world.World");
                    Class<?> worldEditAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                    worldAdaptMethod = worldEditAdapterClass.getMethod("adapt", World.class);
                    regionContainerGetMethod = regionContainer.getClass().getMethod("get", worldEditWorldClass);
                } catch (Exception ex) {
                    regionContainer = null;
                    main.getLogger().log(Level.SEVERE, "Unable to hook into WG. SE: 1", ex);
                    main.worldGuardPlugin = null;
                    return;
                }
            } else {
                regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
                try {
                    regionContainerGetMethod = regionContainer.getClass().getMethod("get", World.class);
                } catch (Exception ex) {
                    main.getLogger().log(Level.SEVERE, "Unable to hook into WG. SE: 2", ex);
                    main.worldGuardPlugin = null;
                    regionContainer = null;
                    return;
                }
            }

            try {
                Class<?> vectorClass = Class.forName("com.sk89q.worldedit.Vector");
                vectorConstructor = vectorClass.getConstructor(Double.TYPE, Double.TYPE, Double.TYPE);
                regionManagerGetMethod = RegionManager.class.getMethod("getApplicableRegions", vectorClass);
            } catch (Exception ex) {
                try {
                    Class<?> vectorClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
                    vectorConstructorAsAMethodBecauseWhyNot = vectorClass.getMethod("at", Double.TYPE, Double.TYPE, Double.TYPE);
                    regionManagerGetMethod = RegionManager.class.getMethod("getApplicableRegions", vectorClass);
                } catch (Exception sodonewiththis) {
                    main.getLogger().log(Level.SEVERE, "Unable to hook into WG. SE: 3", ex);
                    main.worldGuardPlugin = null;
                    regionContainer = null;
                    return;
                }
            }

            if (regionContainer == null) {
                main.getLogger().log(Level.SEVERE, "Unable to hook into WG. SE: 2");
                main.worldGuardPlugin = null;
            }
        }
    }

    @Nullable
    private RegionManager getRegionManager(World world) {
        initialize();
        if (regionContainer == null || regionContainerGetMethod == null) return null;
        RegionManager regionManager = null;
        try {
            if (worldAdaptMethod != null) {
                Object worldEditWorld = worldAdaptMethod.invoke(null, world);
                regionManager = (RegionManager) regionContainerGetMethod.invoke(regionContainer, worldEditWorld);
            } else {
                regionManager = (RegionManager) regionContainerGetMethod.invoke(regionContainer, world);
            }
        } catch (Exception ex) {
            main.getLogger().log(Level.SEVERE, "Unable to run WG lookup. SE: 1");
        }
        return regionManager;
    }

    @Nullable
    private ApplicableRegionSet getRegionSet(Location location) {
        RegionManager regionManager = getRegionManager(location.getWorld());
        if (regionManager == null) return null;

        try {
            Object vector = vectorConstructorAsAMethodBecauseWhyNot == null
                    ? vectorConstructor.newInstance(location.getX(), location.getY(), location.getZ())
                    : vectorConstructorAsAMethodBecauseWhyNot.invoke(null, location.getX(), location.getY(), location.getZ());
            return (ApplicableRegionSet) regionManagerGetMethod.invoke(regionManager, vector);
        } catch (Exception ex) {
            main.getLogger().log(Level.SEVERE, "Unable to run WG lookup. SE: 2");
        }
        return null;
    }

    public List<String> getRegionNames(Location location) {
        ApplicableRegionSet applicableRegionSet = getRegionSet(location);
        return Objects.requireNonNull(applicableRegionSet).getRegions().stream()
                .map(ProtectedRegion::getId).collect(Collectors.toList());
    }
}
