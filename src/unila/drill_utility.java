package unila;

import arc.struct.*;
import mindustry.content.Blocks;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.production.Drill;
import static mindustry.Vars.*;

/**
 * Utility methods for drill placement operations
 */
public class DrillUtility {
    
    /**
     * Count ores that a drill can mine at a given tile
     * @return OreCount object with the most abundant ore and its count, or null if no ores
     */
    public static OreCount countOre(Tile tile, Drill drill) {
        ObjectIntMap<Item> oreCounts = new ObjectIntMap<>();
        Seq<Item> items = new Seq<>();
        
        // Get all tiles this drill would cover
        Seq<Tile> linkedTiles = new Seq<>();
        for (Tile other : tile.getLinkedTilesAs(drill, linkedTiles)) {
            if (drill.canMine(other)) {
                Item ore = drill.getDrop(other);
                if (ore != null) {
                    oreCounts.increment(ore, 0, 1);
                }
            }
        }
        
        if (oreCounts.size == 0) return null;
        
        // Find most abundant ore
        for (Item item : oreCounts.keys()) {
            items.add(item);
        }
        
        items.sort((i1, i2) -> Integer.compare(oreCounts.get(i2, 0), oreCounts.get(i1, 0)));
        
        Item bestOre = items.first();
        int count = oreCounts.get(bestOre, 0);
        
        return new OreCount(bestOre, count);
    }
    
    /**
     * Check if a drill can be placed at the given tile
     */
    public static boolean canPlaceDrill(Tile center, int drillSize, IntSet occupiedTiles) {
        int offset = -(drillSize - 1) / 2;
        
        for (int x = 0; x < drillSize; x++) {
            for (int y = 0; y < drillSize; y++) {
                Tile tile = world.tile(center.x + offset + x, center.y + offset + y);
                
                if (tile == null) return false;
                if (occupiedTiles.contains(tile.pos())) return false;
                if (!Building.validPlace(Blocks.copperWall, player.team(), tile.x, tile.y, 0)) return false;
            }
        }
        
        return true;
    }
    
    /**
     * Mark all tiles a drill occupies as used
     */
    public static void markDrillArea(Tile center, int drillSize, IntSet occupiedTiles) {
        int offset = -(drillSize - 1) / 2;
        
        for (int x = 0; x < drillSize; x++) {
            for (int y = 0; y < drillSize; y++) {
                Tile tile = world.tile(center.x + offset + x, center.y + offset + y);
                if (tile != null) {
                    occupiedTiles.add(tile.pos());
                }
            }
        }
    }
    
    /**
     * Check if a block can be placed at the given location
     */
    public static boolean canPlaceBlock(Tile center, Block block, IntSet occupiedTiles) {
        int size = block.size;
        int offset = -(size - 1) / 2;
        
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                Tile tile = world.tile(center.x + offset + x, center.y + offset + y);
                
                if (tile == null) return false;
                if (occupiedTiles.contains(tile.pos())) return false;
                if (!Building.validPlace(block, player.team(), tile.x, tile.y, 0)) return false;
            }
        }
        
        return true;
    }
    
    /**
     * Mark all tiles a block occupies as used
     */
    public static void markBlockArea(Tile center, int blockSize, IntSet occupiedTiles) {
        int offset = -(blockSize - 1) / 2;
        
        for (int x = 0; x < blockSize; x++) {
            for (int y = 0; y < blockSize; y++) {
                Tile tile = world.tile(center.x + offset + x, center.y + offset + y);
                if (tile != null) {
                    occupiedTiles.add(tile.pos());
                }
            }
        }
    }
    
    /**
     * Find nearest empty spot for placing a block
     */
    public static Tile findNearestEmpty(int centerX, int centerY, int blockSize, IntSet occupiedTiles) {
        for (int radius = 1; radius < 15; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.abs(dx) + Math.abs(dy) != radius) continue;
                    
                    Tile tile = world.tile(centerX + dx, centerY + dy);
                    if (tile != null && canPlaceBlock(tile, Blocks.waterExtractor, occupiedTiles)) {
                        return tile;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get drill block by tier level
     */
    public static Drill getDrillByTier(int tier) {
        switch (tier) {
            case 0: return (Drill) Blocks.mechanicalDrill;
            case 1: return (Drill) Blocks.pneumaticDrill;
            case 2: return (Drill) Blocks.laserDrill;
            case 3: return (Drill) Blocks.blastDrill;
            default: return null;
        }
    }
    
    /**
     * Get drill tier name for display
     */
    public static String getDrillTierName(int tier) {
        switch (tier) {
            case 0: return "Mechanical";
            case 1: return "Pneumatic";
            case 2: return "Laser";
            case 3: return "Blast";
            default: return "Unknown";
        }
    }
}