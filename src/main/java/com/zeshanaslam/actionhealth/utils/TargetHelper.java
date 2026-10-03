package com.zeshanaslam.actionhealth.utils;

import com.zeshanaslam.actionhealth.Main;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * <p>Finds the entities that a player looks at.</p>
 */
public class TargetHelper {

    private Main main;

    public TargetHelper(Main main) {
        this.main = main;
    }

    /**
     * <p>Gets all entities the player is looking at within the range</p>
     * <p>Has a little bit of tolerance to make targeting easier</p>
     *
     * @param source living entity to get the targets of
     * @param range  maximum range to check
     * @return all entities in the player's vision line
     */
    public List<LivingEntity> getLivingTargets(LivingEntity source, double range) {
        return getLivingTargets(source, range, main.configStore.lookTolerance);
    }

    /**
     * <p>Gets all entities the player is looking at within the range using
     * the given tolerance.</p>
     *
     * @param source    living entity to get the targets of
     * @param range     maximum range to check
     * @param tolerance tolerance of the line calculation
     * @return all entities in the player's vision line
     */
    public List<LivingEntity> getLivingTargets(LivingEntity source, double range, double tolerance) {
        if (source == null) {
            return new ArrayList<>();
        }

        List<Entity> list = source.getNearbyEntities(range, range, range);
        List<LivingEntity> targets = new ArrayList<LivingEntity>();

        Vector facing = source.getLocation().getDirection();
        double fLengthSq = facing.lengthSquared();
        Location eye = source.getEyeLocation();
        Vector eyePosition = eye.toVector();
        Vector eyeDirection = eye.getDirection();

        for (Entity entity : list) {
            if (!(entity instanceof LivingEntity)) continue;

            // Looking at any part of the hitbox counts. This finds large mobs and custom models
            // (for example ModelEngine), where the player looks far above the entity's feet.
            if (Compat.rayTraceHitbox(entity, eyePosition, eyeDirection, range) != null) {
                targets.add((LivingEntity) entity);
                continue;
            }

            if (!isInFront(source, entity)) continue;

            Vector relative = entity.getLocation().subtract(source.getLocation()).toVector();
            double dot = relative.dot(facing);
            double rLengthSq = relative.lengthSquared();
            double cosSquared = (dot * dot) / (rLengthSq * fLengthSq);
            double sinSquared = 1 - cosSquared;
            double dSquared = rLengthSq * sinSquared;

            // If close enough to vision line, return the entity
            if (dSquared < tolerance) targets.add((LivingEntity) entity);
        }

        // Nearest first, so a large hitbox behind another entity does not take its place.
        Location location = source.getLocation();
        targets.sort(Comparator.comparingDouble(target -> target.getLocation().distanceSquared(location)));
        return targets;
    }

    /**
     * Checks if the entity is in front of the entity
     *
     * @param entity entity to check for
     * @param target target to check against
     * @return true if the target is in front of the entity
     */
    public boolean isInFront(Entity entity, Entity target) {
        if (entity.getWorld() != target.getWorld())
            return false;

        // Get the necessary vectors
        Vector facing = entity.getLocation().getDirection();
        Vector relative = target.getLocation().subtract(entity.getLocation()).toVector();

        // If the dot product is positive, the target is in front
        return facing.dot(relative) >= main.configStore.lookDot;
    }

    /**
     * Returns true if no solid block is on the line of sight of the player before the target.
     * The line of sight ends where it hits the hitbox of the target (1.13.2+). On older servers,
     * or if the line passes next to the target, it ends at the point nearest to the middle of the target.
     */
    public boolean canSee(LivingEntity from, LivingEntity target) {
        Location eye = from.getEyeLocation();
        Vector start = eye.toVector();
        Vector direction = eye.getDirection();

        double distance;
        Vector hit = Compat.rayTraceHitbox(target, start, direction, main.configStore.lookDistance);
        if (hit != null) {
            distance = hit.distance(start);
        } else {
            Vector feet = target.getLocation().toVector();
            Vector targetEye = target.getEyeLocation().toVector();
            Vector middle = new Vector((feet.getX() + targetEye.getX()) / 2, (feet.getY() + targetEye.getY()) / 2,
                    (feet.getZ() + targetEye.getZ()) / 2);
            distance = middle.subtract(start).dot(direction);
        }

        return !isBlocked(eye, distance);
    }

    /**
     * Returns true if a solid block is on the line of sight from the eye, closer than the distance.
     */
    private boolean isBlocked(Location eye, double distance) {
        // Also stops BlockIterator with 0, which means no limit: it would walk until it finds a block, loading chunks on the way.
        if (distance <= 0) return false;

        Vector start = eye.toVector();
        Vector direction = eye.getDirection();
        try {
            BlockIterator iterator = new BlockIterator(eye, 0, (int) Math.ceil(distance) + 1);
            while (iterator.hasNext()) {
                Block block = iterator.next();
                if (!block.getType().isOccluding()) continue;

                double entry = rayBoxEntry(start, direction, block.getX(), block.getY(), block.getZ(),
                        block.getX() + 1, block.getY() + 1, block.getZ() + 1);
                // The iterator returns blocks in order along the line, so the first solid block decides.
                if (entry >= 0) return entry < distance;
            }

            return false;
        } catch (RuntimeException e) {
            // BlockIterator throws "Start block missed" when the eye is outside the world, for example in the void.
            // Folia can also throw if the line reaches a region that another thread owns.
            return true;
        }
    }

    /**
     * Returns the distance along the ray to where it enters the box, 0 if the ray starts inside the
     * box, or -1 if the ray misses the box. The direction must have a length of 1.
     */
    static double rayBoxEntry(Vector origin, Vector direction, double minX, double minY, double minZ,
                              double maxX, double maxY, double maxZ) {
        double[] o = {origin.getX(), origin.getY(), origin.getZ()};
        double[] d = {direction.getX(), direction.getY(), direction.getZ()};
        double[] min = {minX, minY, minZ};
        double[] max = {maxX, maxY, maxZ};

        double near = 0;
        double far = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 1e-12) {
                // Parallel to this axis: the ray must already be between the two sides.
                if (o[axis] < min[axis] || o[axis] > max[axis]) return -1;
                continue;
            }

            double t1 = (min[axis] - o[axis]) / d[axis];
            double t2 = (max[axis] - o[axis]) / d[axis];
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            if (near > far) return -1;
        }

        return near;
    }
}
