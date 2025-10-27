package unila;

import arc.Core;
import arc.input.KeyCode;
import arc.struct.*;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.gen.*;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.production.Drill;
import static mindustry.Vars.*;

/**
 * Core manager class for AutoDrill functionality
 * Handles input, drill placement logic, and auto-scanning
 */
public class AutoDrillManager {
    private boolean enabled = false;
    
    // Cached data structures
    private final Seq<Tile> floodTiles = new Seq<>(512);
    private final Seq<DrillPlacement> placements = new Seq<>(128);
    private final IntSet visited = new IntSet(1024);
    private final IntSet placed = new IntSet(512);
    private final Queue<Tile> queue = new Queue<>();
    private final ObjectMap<Tile, OreCount> tileOreCache = new ObjectMap<>(256);
    
    // UI callback
    private Runnable onShowDrillOptions;
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void enable() {
        enabled = true;
        ui.showInfoToast("[green]AutoDrill ON[]", 2f);
    }
    
    public void disable() {
        enabled = false;
        ui.showInfoToast("[red]AutoDrill OFF[]", 2f);
    }
    
    public void toggle() {
        if (enabled) disable();
        else enable();
    }
    
    public void setOnShowDrillOptions(Runnable callback) {
        this.onShowDrillOptions = callback;
    }
    
    /**
     * Update loop called every frame
     */
    public void update() {
        handleKeyInput();
        
        if (enabled && state.isGame() && !state.isPaused() && !ui.chatfrag.shown()) {
            handleMouseInput();
        }
    }
    
    private void handleKeyInput() {
        if (Core.input.keyTap(Config.toggleKey)) {
            toggle();
        }
        
        if (Core.input.keyTap(Config.autoScanKey)) {
            performAutoScan();
        }
    }
    
    private void handleMouseInput() {
        if (Core.input.keyTap(KeyCode.mouseLeft) && !Core.scene.hasMouse()) {
            Tile selected = world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
            if (selected != null && selected.drop() != null && selected.block() == Blocks.air) {
                if (onShowDrillOptions != null) {
                    onShowDrillOptions.run();
                }
            }
        }
    }
    
    /**
     * Get the currently selected tile's ore data
     */
    public Tile getSelectedTile() {
        if (!Core.input.keyTap(KeyCode.mouseLeft) || Core.scene.hasMouse()) {
            return null;
        }
        
        Tile selected = world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
        if (selected != null && selected.drop() != null && selected.block() == Blocks.air) {
            return selected;
        }
        return null;
    }
    
    /**
     * Get flood-filled tiles for a given ore type
     */
    public Seq<Tile> getFloodTiles(Tile start, Item ore) {
        floodFill(start, ore);
        return new Seq<>(floodTiles);
    }
    
    /**
     * Flood fill algorithm to find connected ore tiles
     */
    private void floodFill(Tile start, Item ore) {
        floodTiles.clear();
        queue.clear();
        visited.clear();
        
        queue.addLast(start);
        visited.add(start.pos());
        
        while (queue.size > 0 && floodTiles.size < Config.maxTiles) {
            Tile current = queue.removeFirst();
            
            if (current.drop() == ore && Build.validPlace(Blocks.copperWall, player.team(), current.x, current.y, 0)) {
                floodTiles.add(current);
                
                // Check adjacent tiles
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        
                        Tile neighbor = current.nearby(dx, dy);
                        if (neighbor != null && !visited.contains(neighbor.pos())) {
                            visited.add(neighbor.pos());
                            queue.addLast(neighbor);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Place drills for the given ore type
     */
    public void placeDrills(Drill drill, Item ore) {
        if (floodTiles.isEmpty()) {
            ui.showInfoToast("No ore tiles available!", 2f);
            return;
        }
        
        // Cache ore counts
        placements.clear();
        placed.clear();
        tileOreCache.clear();
        
        for (Tile tile : floodTiles) {
            OreCount count = DrillUtility.countOre(tile, drill);
            if (count != null) {
                tileOreCache.put(tile, count);
            }
        }
        
        // Calculate optimal placements
        DrillOptimization.calculateOptimalPlacements(drill, ore, floodTiles, tileOreCache, placements, placed);
        
        if (placements.isEmpty()) {
            ui.showInfoToast("No valid placement spots!", 2f);
            return;
        }
        
        // Place drills
        for (DrillPlacement placement : placements) {
            player.unit().addBuild(new BuildPlan(placement.tile.x, placement.tile.y, 0, drill));
        }
        
        // Place supporting structures
        if (drill.liquidBoostIntensity > 0 && Config.placeWaterExtractors) {
            if (Config.usePipeInput) {
                InfrastructurePlacer.connectWithPipes(placements, drill);
            } else {
                InfrastructurePlacer.placeExtractors(placements, drill);
            }
        }
        
        if (Config.placePowerNodes) {
            InfrastructurePlacer.placePowerNodes(placements, drill);
        }
        
        ui.showInfoToast("[green]Placed " + placements.size + " drills![]", 2.5f);
        clearCaches();
    }
    
    /**
     * Perform auto-scan around player position
     */
    public void performAutoScan() {
        if (!Config.autoScanEnabled) {
            ui.showInfoToast("Auto-scan disabled. Enable in settings!", 2f);
            return;
        }
        
        if (!state.isGame() || player == null || player.unit() == null) {
            ui.showInfoToast("Cannot scan: Invalid game state", 2f);
            return;
        }
        
        int playerX = Tmp.p1.set(player.unit()).tileX();
        int playerY = Tmp.p1.set(player.unit()).tileY();
        
        Drill selectedDrill = DrillUtility.getDrillByTier(Config.autoScanDrillTier);
        if (selectedDrill == null) {
            ui.showInfoToast("Invalid drill tier selected", 2f);
            return;
        }
        
        ui.showInfoToast("[accent]Scanning area...", 2f);
        
        IntSet scannedTiles = new IntSet();
        int totalDrills = 0;
        int radiusSquared = Config.autoScanRadius * Config.autoScanRadius;
        
        for (int dx = -Config.autoScanRadius; dx <= Config.autoScanRadius; dx++) {
            for (int dy = -Config.autoScanRadius; dy <= Config.autoScanRadius; dy++) {
                if (dx * dx + dy * dy > radiusSquared) continue;
                
                Tile tile = world.tile(playerX + dx, playerY + dy);
                if (tile == null || scannedTiles.contains(tile.pos())) continue;
                
                Item ore = tile.drop();
                if (ore == null || tile.block() != Blocks.air) continue;
                if (selectedDrill.tier < ore.hardness) continue;
                
                // Flood fill this ore patch
                floodFill(tile, ore);
                
                // Mark all tiles as scanned
                for (Tile floodTile : floodTiles) {
                    scannedTiles.add(floodTile.pos());
                }
                
                if (floodTiles.size >= Config.minOres) {
                    // Place drills for this patch
                    placeDrills(selectedDrill, ore);
                    totalDrills += placements.size;
                }
            }
        }
        
        clearCaches();
        
        if (totalDrills > 0) {
            ui.showInfoToast("[green]Auto-scan complete! Placed " + totalDrills + " drills[]", 3f);
        } else {
            ui.showInfoToast("No suitable ore patches found in range", 2f);
        }
    }
    
    /**
     * Clear all cached data
     */
    public void clearCaches() {
        tileOreCache.clear();
        floodTiles.clear();
        placements.clear();
        visited.clear();
        placed.clear();
    }
}