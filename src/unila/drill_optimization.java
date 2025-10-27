package unila;

import arc.struct.*;
import mindustry.type.Item;
import mindustry.world.Tile;
import mindustry.world.blocks.production.Drill;

/**
 * Optimization algorithms for drill placement
 * Handles finding the best positions to place drills for maximum efficiency
 */
public class DrillOptimization {
    
    /**
     * Calculate optimal drill placements for an ore field
     * Uses a greedy algorithm with quality-based iteration limit
     */
    public static void calculateOptimalPlacements(
            Drill drill, 
            Item targetOre,
            Seq<Tile> availableTiles,
            ObjectMap<Tile, OreCount> oreCache,
            Seq<DrillPlacement> outputPlacements,
            IntSet occupiedTiles
    ) {
        int drillSize = drill.size;
        int offset = -(drillSize - 1) / 2;
        
        // Score all possible placements
        Seq<ScoredPosition> scoredPositions = new Seq<>();
        
        for (Tile tile : availableTiles) {
            if (occupiedTiles.contains(tile.pos())) continue;
            
            OreCount oreData = oreCache.get(tile);
            if (oreData == null || oreData.ore != targetOre || oreData.count < Config.minOres) {
                continue;
            }
            
            if (DrillUtility.canPlaceDrill(tile, drillSize, occupiedTiles)) {
                scoredPositions.add(new ScoredPosition(tile, oreData.count));
            }
        }
        
        // Sort by score (descending)
        scoredPositions.sort(sp -> -sp.score);
        
        // Place drills greedily up to quality limit
        int placementLimit = Math.min(scoredPositions.size, Config.optimizationQuality * 100);
        
        for (int i = 0; i < placementLimit; i++) {
            ScoredPosition position = scoredPositions.get(i);
            
            // Double-check placement is still valid
            if (DrillUtility.canPlaceDrill(position.tile, drillSize, occupiedTiles)) {
                outputPlacements.add(new DrillPlacement(position.tile, position.score));
                DrillUtility.markDrillArea(position.tile, drillSize, occupiedTiles);
            }
        }
    }
    
    /**
     * Calculate coverage score for a potential drill placement
     * Higher scores indicate better placements (more ore coverage)
     */
    public static int calculateCoverageScore(Tile tile, Drill drill, Item targetOre) {
        int score = 0;
        Seq<Tile> covered = new Seq<>();
        
        for (Tile other : tile.getLinkedTilesAs(drill, covered)) {
            if (drill.canMine(other)) {
                Item ore = drill.getDrop(other);
                if (ore == targetOre) {
                    score++;
                }
            }
        }
        
        return score;
    }
    
    /**
     * Check if a drill placement overlaps with existing placements
     */
    public static boolean overlapsWithPlacements(
            Tile tile, 
            int drillSize, 
            Seq<DrillPlacement> existingPlacements
    ) {
        int offset = -(drillSize - 1) / 2;
        
        for (DrillPlacement existing : existingPlacements) {
            int exOffset = -(drillSize - 1) / 2;
            
            // Check if bounding boxes overlap
            if (Math.abs(tile.x - existing.tile.x) < drillSize && 
                Math.abs(tile.y - existing.tile.y) < drillSize) {
                return true;
            }
        }
        
        return false;
    }
}