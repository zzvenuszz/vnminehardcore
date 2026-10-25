package com.vnmine.hardcore.managers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Builds a bounded, ground-following navigation route and renders it as small particle chevrons. */
final class QuestWaypointRenderer {
    private static final int MAX_EXPANSIONS = 700;
    private static final int MAX_SEARCH_RADIUS = 42;
    private static final double FAR_TARGET_STEP = 28.0;
    private static final double ARROW_SPACING = 2.4;
    private static final long ROUTE_CACHE_MILLIS = 1_000L;
    private static final Map<RouteKey, CachedRoute> ROUTE_CACHE = new HashMap<>();

    private QuestWaypointRenderer() { }

    static boolean render(Player player, Location destination, double visibleDistance) {
        return render(player, destination, visibleDistance, true);
    }

    static boolean render(Player player, Location destination, double visibleDistance, boolean showTargetMarker) {
        Location playerLocation = player.getLocation();
        if (destination == null || destination.getWorld() == null
                || destination.getWorld() != playerLocation.getWorld()) return false;
        World world = playerLocation.getWorld();
        double dx = destination.getX() - playerLocation.getX();
        double dz = destination.getZ() - playerLocation.getZ();
        double horizontalDistance = Math.hypot(dx, dz);
        if (horizontalDistance < 1.5) {
            Surface ground = findSurfaceNear(world, destination.getBlockX(), destination.getBlockZ(),
                destination.getBlockY(), 2);
            if (ground != null && showTargetMarker) showGroundMarker(player, toLocation(world, ground));
            return ground != null;
        }

        int startX = playerLocation.getBlockX();
        int startY = playerLocation.getBlockY();
        int startZ = playerLocation.getBlockZ();
        long now = System.currentTimeMillis();
        RouteKey key = new RouteKey(player.getUniqueId(), world.getUID(), destination.getBlockX(),
            destination.getBlockY(), destination.getBlockZ());
        CachedRoute cached = ROUTE_CACHE.get(key);
        List<Surface> route;
        if (cached != null && cached.matches(startX, startY, startZ, now)) {
            route = cached.route();
        } else {
            Surface start = findSurfaceNear(world, startX, startZ, startY, 1);
            if (start == null) return false;
            double fraction = Math.min(1.0, FAR_TARGET_STEP / horizontalDistance);
            int intendedX = (int) Math.floor(playerLocation.getX() + dx * fraction);
            int intendedZ = (int) Math.floor(playerLocation.getZ() + dz * fraction);
            int intendedY = (int) Math.round(playerLocation.getY()
                + (destination.getY() - playerLocation.getY()) * fraction);
            Surface goal = findSurfaceNear(world, intendedX, intendedZ, intendedY, 3);
            if (goal == null) return false;
            route = findRoute(world, start, goal);
            if (!route.isEmpty()) {
                ROUTE_CACHE.entrySet().removeIf(entry -> now - entry.getValue().createdAt() >= ROUTE_CACHE_MILLIS);
                ROUTE_CACHE.put(key, new CachedRoute(startX, startY, startZ, now, route));
            }
        }
        if (route.size() < 2) return false;
        renderChevrons(player, world, route, visibleDistance);

        if (showTargetMarker && horizontalDistance <= visibleDistance) {
            Surface destinationGround = findSurfaceNear(world, destination.getBlockX(), destination.getBlockZ(),
                destination.getBlockY(), 2);
            if (destinationGround != null) showGroundMarker(player, toLocation(world, destinationGround));
        }
        return true;
    }

    static void forget(java.util.UUID playerId) {
        ROUTE_CACHE.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    private static List<Surface> findRoute(World world, Surface start, Surface goal) {
        Node startNode = new Node(start, 0.0, heuristic(start, goal), null);
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<Surface, Double> bestCost = new HashMap<>();
        open.add(startNode);
        bestCost.put(start, 0.0);
        Node closest = startNode;
        Node reached = null;
        int expanded = 0;
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

        while (!open.isEmpty() && expanded < MAX_EXPANSIONS) {
            Node current = open.poll();
            if (current.cost() > bestCost.getOrDefault(current.surface(), Double.POSITIVE_INFINITY)) continue;
            if (current.surface().equals(goal)) {
                reached = current;
                break;
            }
            expanded++;
            if (heuristic(current.surface(), goal) < heuristic(closest.surface(), goal)) closest = current;

            for (int[] offset : offsets) {
                int x = current.surface().x() + offset[0];
                int z = current.surface().z() + offset[1];
                if (Math.abs(x - start.x()) > MAX_SEARCH_RADIUS
                        || Math.abs(z - start.z()) > MAX_SEARCH_RADIUS) continue;
                Surface next = findSurface(world, x, z, current.surface().y());
                if (next == null || Math.abs(next.y() - current.surface().y()) > 1) continue;
                boolean diagonal = offset[0] != 0 && offset[1] != 0;
                if (diagonal && (findSurface(world, current.surface().x() + offset[0], current.surface().z(),
                                current.surface().y()) == null
                        || findSurface(world, current.surface().x(), current.surface().z() + offset[1],
                                current.surface().y()) == null)) continue;

                double stepCost = diagonal ? 1.4142 : 1.0;
                stepCost += Math.abs(next.y() - current.surface().y()) * 0.35;
                double nextCost = current.cost() + stepCost;
                if (nextCost >= bestCost.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                bestCost.put(next, nextCost);
                open.add(new Node(next, nextCost, nextCost + heuristic(next, goal), current));
            }
        }

        Node end = reached == null ? closest : reached;
        if (end == startNode || end.surface().equals(start)) return List.of();
        List<Surface> reversed = new ArrayList<>();
        for (Node node = end; node != null; node = node.parent()) reversed.add(node.surface());
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private static double heuristic(Surface from, Surface to) {
        int dx = Math.abs(to.x() - from.x());
        int dz = Math.abs(to.z() - from.z());
        double diagonal = Math.min(dx, dz);
        return diagonal * 1.4142 + Math.max(dx, dz) - diagonal + Math.abs(to.y() - from.y()) * 0.5;
    }

    private static Surface findSurfaceNear(World world, int x, int z, int referenceY, int radius) {
        Surface best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int distance = 0; distance <= radius; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                int dzAbs = distance - Math.abs(dx);
                int[] dzValues = dzAbs == 0 ? new int[]{0} : new int[]{-dzAbs, dzAbs};
                for (int dz : dzValues) {
                    Surface candidate = findSurface(world, x + dx, z + dz, referenceY);
                    if (candidate == null) continue;
                    int score = Math.abs(dx) + Math.abs(dz) + Math.abs(candidate.y() - referenceY) * 2;
                    if (score < bestScore) {
                        best = candidate;
                        bestScore = score;
                    }
                }
            }
            if (best != null && bestScore <= distance) break;
        }
        return best;
    }

    private static Surface findSurface(World world, int x, int z, int referenceY) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        int[] offsets = {0, 1, -1, 2, -2, -3};
        for (int offset : offsets) {
            int y = referenceY + offset;
            if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) continue;
            var feet = world.getBlockAt(x, y, z);
            var head = world.getBlockAt(x, y + 1, z);
            var floor = world.getBlockAt(x, y - 1, z);
            if (feet.isPassable() && head.isPassable() && !feet.isLiquid() && !floor.isLiquid()
                    && floor.getType().isSolid()) return new Surface(x, y, z);
        }
        return null;
    }

    private static void renderChevrons(Player player, World world, List<Surface> route, double maxDistance) {
        double travelled = 0.0;
        double nextArrowAt = ARROW_SPACING;
        for (int index = 1; index < route.size(); index++) {
            Surface previous = route.get(index - 1);
            Surface current = route.get(index);
            double dx = current.x() - previous.x();
            double dz = current.z() - previous.z();
            double segmentLength = Math.hypot(dx, dz);
            if (segmentLength == 0.0) continue;
            while (travelled + segmentLength >= nextArrowAt && nextArrowAt <= maxDistance) {
                double portion = (nextArrowAt - travelled) / segmentLength;
                double x = previous.x() + 0.5 + dx * portion;
                double z = previous.z() + 0.5 + dz * portion;
                double y = (portion < 0.5 ? previous.y() : current.y()) + 0.10;
                drawChevron(player, new Location(world, x, y, z), dx / segmentLength, dz / segmentLength);
                nextArrowAt += ARROW_SPACING;
            }
            travelled += segmentLength;
            if (travelled >= maxDistance) return;
        }
    }

    private static void drawChevron(Player player, Location center, double directionX, double directionZ) {
        double sideX = -directionZ * 0.24;
        double sideZ = directionX * 0.24;
        Location tip = center.clone().add(directionX * 0.25, 0, directionZ * 0.25);
        Location left = center.clone().subtract(directionX * 0.22, 0, directionZ * 0.22)
            .add(sideX, 0, sideZ);
        Location right = center.clone().subtract(directionX * 0.22, 0, directionZ * 0.22)
            .subtract(sideX, 0, sideZ);
        player.spawnParticle(org.bukkit.Particle.END_ROD, tip, 1, 0, 0, 0, 0);
        player.spawnParticle(org.bukkit.Particle.END_ROD, left, 1, 0, 0, 0, 0);
        player.spawnParticle(org.bukkit.Particle.END_ROD, right, 1, 0, 0, 0, 0);
    }

    private static void showGroundMarker(Player player, Location ground) {
        for (int index = 0; index < 8; index++) {
            double angle = Math.PI * 2.0 * index / 8.0;
            Location point = ground.clone().add(0.5 + Math.cos(angle) * 0.65, 0.10,
                0.5 + Math.sin(angle) * 0.65);
            player.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, point, 1, 0, 0, 0, 0);
        }
    }

    private static Location toLocation(World world, Surface surface) {
        return new Location(world, surface.x(), surface.y(), surface.z());
    }

    private record Surface(int x, int y, int z) { }
    private record Node(Surface surface, double cost, double score, Node parent) { }
    private record RouteKey(java.util.UUID playerId, java.util.UUID worldId, int targetX, int targetY, int targetZ) { }
    private record CachedRoute(int startX, int startY, int startZ, long createdAt, List<Surface> route) {
        private CachedRoute { route = List.copyOf(route); }
        private boolean matches(int x, int y, int z, long now) {
            int moveX = x - startX;
            int moveY = y - startY;
            int moveZ = z - startZ;
            return moveX * moveX + moveZ * moveZ < 4 && Math.abs(moveY) <= 1
                && now - createdAt < ROUTE_CACHE_MILLIS;
        }
    }
}
