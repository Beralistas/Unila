package unila;

import arc.math.geom.Point2;
import arc.struct.*;
import mindustry.content.Blocks;
import mindustry.gen.BuildPlan;
import mindustry.world.Block;
import mindustry.world.Edges;
import mindustry.world.Tile;
import mindustry.world.blocks.production.Drill;
import static mindustry.Vars.*;

/**
 * Handles placement of supporting infrastructure:
 * - Water extractors
 * - Power nodes  
 * - Pipes/conduits
 */
public class InfrastructurePlacer {
    
    /**
     * Place water extractors near each drill
     */
    public static void placeExtractors(Seq<DrillPlacement> drillPlacements, Drill drill) {
        Block extractor = Blocks.waterExtractor;
        IntSet occupiedTiles = new IntSet();
        int placedCount = 0;
        
        for (DrillPlacement placement : drillPlacements) {
            Tile bestSpot = findAdjacentSpot(placement.tile, drill.size, extractor, occupiedTiles);
            
            if (bestSpot != null) {
                player.unit().addBuild(new BuildPlan(bestSpot.x, bestSpot.y, 0, extractor));
                DrillUtility.markBlockArea(bestSpot, extractor.size, occupiedTiles);
                placedCount++;
            }
        }
        
        if (placedCount > 0) {
            ui.showInfoToast("+ " + placedCount + " water extractors", 1.5f);
        }
    }
    
    /**
     * Place power nodes near each drill
     */
    public static void placePowerNodes(Seq<DrillPlacement> drillPlacements, Drill drill) {
        Block powerNode = Blocks.powerNode;
        IntSet occupiedTiles = new IntSet();
        int placedCount = 0;
        
        for (DrillPlacement placement : drillPlacements) {
            Tile bestSpot = findAdjacentSpot(placement.tile, drill.size, powerNode, occupiedTiles);
            
            if (bestSpot != null) {
                player.unit().addBuild(new BuildPlan(bestSpot.x, bestSpot.y, 0, powerNode));
                DrillUtility.markBlockArea(bestSpot, powerNode.size, occupiedTiles);
                placedCount++;
            }
        }
        
        if (placedCount > 0) {
            ui.showInfoToast("+ " + placedCount + " power nodes", 1.5f);
        }
    }
    
    /**
     * Connect drills with pipes to a central water source
     */
    public static void connectWithPipes(Seq<DrillPlacement> drillPlacements, Drill drill) {
        if (drillPlacements.isEmpty()) return;
        
        Block pipe = Blocks.conduit;
        Block extractor = Blocks.waterExtractor;
        IntSet occupiedTiles = new IntSet();
        
        // Find center point
        int centerX = 0;
        int centerY = 0;
        for (DrillPlacement placement : drillPlacements) {
            centerX += placement.tile.x;
            centerY += placement.tile.y;
        }
        centerX /= drillPlacements.size;
        centerY /= drillPlacements.size;
        
        // Place water source at center
        Tile waterSource = DrillUtility.findNearestEmpty(centerX, centerY, extractor.size, occupiedTiles);
        if (waterSource == null) return;
        
        player.unit().addBuild(new BuildPlan(waterSource.x, waterSource.y, 0, extractor));
        DrillUtility.markBlockArea(waterSource, extractor.size, occupiedTiles);
        
        // Connect each drill to water source with pipes
        int pipeCount = 0;
        for (DrillPlacement placement : drillPlacements) {
            if (connectPipePath(waterSource, placement.tile, pipe, occupiedTiles)) {
                pipeCount++;
            }
        }
        
        if (pipeCount > 0) {
            ui.showInfoToast("+ " + pipeCount + " pipe connections", 1.5f);
        }
    }
    
    /**
     * Find adjacent spot for placing infrastructure
     */
    private static Tile findAdjacentSpot(Tile drillTile, int drillSize, Block block, IntSet occupiedTiles) {
        Point2[] edges = Edges.getEdges(drillSize);
        
        for (Point2 edge : edges) {
            Tile candidate = world.tile(drillTile.x + edge.x, drillTile.y + edge.y);
            
            if (candidate != null && DrillUtility.canPlaceBlock(candidate, block, occupiedTiles)) {
                return candidate;
            }
        }
        
        return null;
    }
    
    /**
     * Connect two points with pipes using Manhattan path
     */
    private static boolean connectPipePath(Tile from, Tile to, Block pipe, IntSet occupiedTiles) {
        int currentX = from.x;
        int currentY = from.y;
        int targetX = to.x;
        int targetY = to.y;
        boolean placedAny = false;
        
        while (currentX != targetX || currentY != targetY) {
            // Move towards target
            if (currentX < targetX) {
                currentX++;
            } else if (currentX > targetX) {
                currentX--;
            } else if (currentY < targetY) {
                currentY++;
            } else if (currentY > targetY) {
                currentY--;
            }
            
            Tile current = world.tile(currentX, currentY);
            if (current != null && !occupiedTiles.contains(current.pos())) {
                if (DrillUtility.canPlaceBlock(current, pipe, occupiedTiles)) {
                    player.unit().addBuild(new BuildPlan(currentX, currentY, 0, pipe));
                    occupiedTiles.add(current.pos());
                    placedAny = true;
                }
            }
        }
        
        return placedAny;
    }
}