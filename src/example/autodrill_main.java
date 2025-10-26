package autodrill;

import arc.*;
import arc.graphics.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.liquid.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.entities.units.*;

import static mindustry.Vars.*;

public class AutoDrill extends Mod {
    // Configuration
    public static boolean enabled = false;
    public static KeyCode toggleKey = KeyCode.h;
    public static KeyCode autoScanKey = KeyCode.j;
    public static int maxTiles = 1000;
    public static int minOres = 2;
    public static boolean placeWaterExtractors = true;
    public static boolean placePowerNodes = true;
    public static boolean usePipeInput = false;
    public static boolean displayToggleButton = true;
    public static int optimizationQuality = 3;
    
    // Auto-scan settings
    public static boolean autoScanEnabled = false;
    public static int autoScanRadius = 20;
    public static int autoScanDrillTier = 2; // 0=mechanical, 1=pneumatic, 2=laser, 3=blast
    
    // UI
    private Table drillDialog;
    private Button toggleButton;
    private BaseDialog settingsDialog;
    
    // Cache for performance
    private final Seq<Tile> floodTiles = new Seq<>(512);
    private final Seq<DrillPlacement> placements = new Seq<>(128);
    private final IntSet visited = new IntSet(1024);
    private final IntSet placed = new IntSet(512);
    private final Queue<Tile> queue = new Queue<>();
    private final ObjectMap<Tile, ObjectIntMap.Entry<Item>> tileOreCache = new ObjectMap<>(256);
    
    public AutoDrill() {
        Log.info("AutoDrill V8 Enhanced loaded!");
        
        Events.on(ClientLoadEvent.class, e -> {
            Time.runTask(10f, () -> {
                loadSettings();
                addToggleButton();
                createSettingsDialog();
            });
        });
        
        Events.on(WorldLoadEvent.class, e -> {
            enabled = false;
            clearCaches();
        });
    }
    
    @Override
    public void init() {
        Events.run(Trigger.update, this::update);
    }
    
    private void update() {
        if(Core.input.keyTap(toggleKey)) {
            toggleMod();
        }
        
        if(Core.input.keyTap(autoScanKey)) {
            if(autoScanEnabled) {
                performAutoScan();
            } else {
                ui.showInfoToast("Auto-scan is disabled. Enable in settings!", 2f);
            }
        }
        
        if(enabled && state.isGame() && !state.isPaused() && !ui.chatfrag.shown()) {
            handleInput();
        }
    }
    
    private void addToggleButton() {
        if(!displayToggleButton) return;
        
        ui.hudGroup.fill(cont -> {
            cont.name = "autodrill-toggle";
            cont.bottom().right();
            toggleButton = cont.button("AutoDrill", Styles.togglet, this::toggleMod)
                .size(120f, 45f)
                .margin(8f)
                .padRight(10f)
                .padBottom(60f)
                .update(b -> b.setChecked(enabled))
                .get();
            
            // Add settings button
            cont.button(Icon.settings, Styles.clearTransi, () -> {
                settingsDialog.show();
            }).size(40f).padRight(140f).padBottom(60f);
        });
    }
    
    private void createSettingsDialog() {
        settingsDialog = new BaseDialog("AutoDrill Settings");
        settingsDialog.cont.pane(t -> {
            t.defaults().size(400f, 60f).pad(4f);
            
            // === BASIC SETTINGS ===
            t.add("[accent]Basic Settings[]").left().row();
            t.image().color(Color.accent).fillX().height(2f).pad(4f).row();
            
            // Toggle Key
            t.table(key -> {
                key.left();
                key.add("Activation Key: ").left();
                key.button(toggleKey.toString(), Styles.flatTogglet, () -> {
                    ui.showTextInput("Set Activation Key", "Enter key name (e.g., h, k, etc.)", 10, toggleKey.toString(), text -> {
                        try {
                            toggleKey = KeyCode.byName(text.toLowerCase());
                            saveSettings();
                            ui.showInfoToast("Activation key set to: " + toggleKey, 2f);
                        } catch(Exception e) {
                            ui.showInfoToast("Invalid key name!", 2f);
                        }
                    });
                }).width(100f);
            }).row();
            
            // Display Toggle Button
            t.check("Display Toggle Button", displayToggleButton, v -> {
                displayToggleButton = v;
                saveSettings();
                ui.showInfoToast("Restart required to apply", 2f);
            }).left().padTop(10f).row();
            
            // Max Tiles Slider
            t.table(max -> {
                max.left();
                max.add("Max Tiles: ").left();
                Label maxLabel = max.add(maxTiles + "").width(60f).left().get();
                max.row();
                max.slider(100, 5000, 100, maxTiles, v -> {
                    maxTiles = (int)v;
                    maxLabel.setText((int)v + "");
                    saveSettings();
                }).width(300f).padTop(4f);
            }).row();
            
            // Minimum Ores Slider
            t.table(min -> {
                min.left();
                min.add("Minimum Ores per Drill: ").left();
                Label minLabel = min.add(minOres + "").width(60f).left().get();
                min.row();
                min.slider(1, 10, 1, minOres, v -> {
                    minOres = (int)v;
                    minLabel.setText((int)v + "");
                    saveSettings();
                }).width(300f).padTop(4f);
            }).row();
            
            // Optimization Quality Slider
            t.table(opt -> {
                opt.left();
                opt.add("Optimization Quality: ").left();
                Label optLabel = opt.add(optimizationQuality + "").width(60f).left().get();
                opt.row();
                opt.slider(1, 10, 1, optimizationQuality, v -> {
                    optimizationQuality = (int)v;
                    optLabel.setText((int)v + "");
                    saveSettings();
                }).width(300f).padTop(4f);
            }).row();
            
            // Place Water Extractors
            t.check("Place Water Extractors", placeWaterExtractors, v -> {
                placeWaterExtractors = v;
                saveSettings();
            }).left().padTop(10f).row();
            
            // Place Power Nodes
            t.check("Place Power Nodes", placePowerNodes, v -> {
                placePowerNodes = v;
                saveSettings();
            }).left().row();
            
            // Use Pipe Input
            t.check("Use Pipe Input (Centralized Water)", usePipeInput, v -> {
                usePipeInput = v;
                saveSettings();
            }).left().row();
            
            // === AUTO-SCAN SETTINGS ===
            t.add("").row(); // Spacer
            t.add("[accent]Auto-Scan Settings[]").left().padTop(20f).row();
            t.image().color(Color.accent).fillX().height(2f).pad(4f).row();
            
            // Enable Auto-Scan
            t.check("Enable Auto-Scan Mode", autoScanEnabled, v -> {
                autoScanEnabled = v;
                saveSettings();
            }).left().padTop(10f).tooltip("Automatically scans and places drills around player").row();
            
            // Auto-Scan Key
            t.table(scanKey -> {
                scanKey.left();
                scanKey.add("Auto-Scan Key: ").left();
                scanKey.button(autoScanKey.toString(), Styles.flatTogglet, () -> {
                    ui.showTextInput("Set Auto-Scan Key", "Enter key name (e.g., j, k, etc.)", 10, autoScanKey.toString(), text -> {
                        try {
                            autoScanKey = KeyCode.byName(text.toLowerCase());
                            saveSettings();
                            ui.showInfoToast("Auto-scan key set to: " + autoScanKey, 2f);
                        } catch(Exception e) {
                            ui.showInfoToast("Invalid key name!", 2f);
                        }
                    });
                }).width(100f);
            }).row();
            
            // Scan Radius Slider
            t.table(radius -> {
                radius.left();
                radius.add("Scan Radius: ").left();
                Label radiusLabel = radius.add(autoScanRadius + " tiles").width(80f).left().get();
                radius.row();
                radius.slider(5, 50, 1, autoScanRadius, v -> {
                    autoScanRadius = (int)v;
                    radiusLabel.setText((int)v + " tiles");
                    saveSettings();
                }).width(300f).padTop(4f);
            }).row();
            
            // Drill Tier Selection
            t.table(tier -> {
                tier.left();
                tier.add("Drill Tier: ").left();
                String[] drillNames = {"Mechanical", "Pneumatic", "Laser", "Blast"};
                Label tierLabel = tier.add(drillNames[autoScanDrillTier]).width(100f).left().get();
                tier.row();
                tier.slider(0, 3, 1, autoScanDrillTier, v -> {
                    autoScanDrillTier = (int)v;
                    tierLabel.setText(drillNames[(int)v]);
                    saveSettings();
                }).width(300f).padTop(4f);
            }).row();
            
            // Info label
            t.add("[lightgray]Press " + autoScanKey + " to scan & place drills[]").left().padTop(10f).scale(0.85f).row();
            
        }).grow();
        
        settingsDialog.addCloseButton();
        settingsDialog.buttons.button("Reset to Default", Icon.refresh, () -> {
            resetToDefaults();
            settingsDialog.hide();
            Time.runTask(5f, () -> settingsDialog.show());
        }).size(200f, 50f);
    }
    
    private void toggleMod() {
        enabled = !enabled;
        ui.showInfoToast("AutoDrill " + (enabled ? "[green]Enabled[]" : "[red]Disabled[]"), 2f);
    }
    
    private void handleInput() {
        if(Core.input.keyTap(KeyCode.mouseLeft) && !Core.scene.hasMouse()) {
            Tile selected = world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
            
            if(selected != null && selected.drop() != null && selected.block() == Blocks.air) {
                showDrillOptions(selected);
            }
        }
    }
    
    private void showDrillOptions(Tile tile) {
        if(drillDialog != null) drillDialog.remove();
        
        Item ore = tile.drop();
        if(ore == null) return;
        
        // Flood fill resource patch
        floodFill(tile, ore);
        
        if(floodTiles.size < minOres) {
            ui.showInfoToast("Not enough ore tiles found! (" + floodTiles.size + "/" + minOres + ")", 2f);
            return;
        }
        
        // Create drill selection UI
        drillDialog = new Table(Tex.buttonEdge3);
        drillDialog.margin(6f);
        
        Vec2 mousePos = Core.input.mouse();
        drillDialog.setPosition(Mathf.clamp(mousePos.x + 10f, 0, Core.graphics.getWidth() - 200), 
                               Mathf.clamp(mousePos.y - 10f, 0, Core.graphics.getHeight() - 300));
        
        drillDialog.update(() -> {
            if(Core.input.keyTap(KeyCode.mouseLeft) || Core.input.keyTap(KeyCode.mouseRight) || Core.input.keyTap(KeyCode.escape)) {
                drillDialog.remove();
                drillDialog = null;
            }
        });
        
        drillDialog.table(t -> {
            t.add("[accent]Select Drill Type[]").colspan(2).center().pad(4f).row();
            t.image().color(Color.accent).fillX().height(3f).colspan(2).pad(4f).row();
            t.defaults().size(180f, 55f).pad(3f);
            
            // Add drill buttons with icons
            addDrillButton(t, Blocks.mechanicalDrill, ore);
            addDrillButton(t, Blocks.pneumaticDrill, ore);
            t.row();
            addDrillButton(t, Blocks.laserDrill, ore);
            addDrillButton(t, Blocks.blastDrill, ore);
        }).row();
        
        // Info display
        drillDialog.table(info -> {
            info.image().color(Color.accent).fillX().height(2f).pad(4f).row();
            info.add("[lightgray]Ore Patch: " + floodTiles.size + " tiles[]").left().pad(4f).row();
            info.add("[lightgray]Ore Type: " + ore.localizedName + "[]").left().pad(4f);
        }).fillX().row();
        
        // Quick options
        drillDialog.table(opt -> {
            opt.defaults().left().pad(2f);
            opt.check("Water", placeWaterExtractors, v -> placeWaterExtractors = v)
                .tooltip("Place water extractors near drills");
            opt.check("Power", placePowerNodes, v -> placePowerNodes = v)
                .tooltip("Place power nodes near drills");
            opt.row();
            opt.check("Pipe Mode", usePipeInput, v -> usePipeInput = v)
                .tooltip("Use centralized water with pipes").colspan(2);
        }).fillX().padTop(6f);
        
        Core.scene.add(drillDialog);
    }
    
    private void addDrillButton(Table table, Block block, Item ore) {
        if(!(block instanceof Drill drill)) return;
        
        boolean canMine = drill.tier >= ore.hardness;
        
        table.button(b -> {
            b.left();
            b.image(block.uiIcon).size(40f).padLeft(4f).padRight(8f);
            b.table(txt -> {
                txt.add(block.localizedName).color(canMine ? Color.white : Color.gray).left().row();
                txt.add("[lightgray]" + drill.size + "x" + drill.size + " | " + 
                       (drill.liquidBoostIntensity > 0 ? "Liquid Boost" : "No Boost") + "[]")
                    .scale(0.8f).left();
            }).growX().left();
        }, Styles.flatToggleMenut, () -> {
            if(canMine) {
                placeDrills(drill, ore);
                if(drillDialog != null) {
                    drillDialog.remove();
                    drillDialog = null;
                }
            }
        }).disabled(!canMine).tooltip(canMine ? "Click to place drills" : "Cannot mine " + ore.localizedName);
    }
    
    private void floodFill(Tile start, Item ore) {
        floodTiles.clear();
        queue.clear();
        visited.clear();
        
        queue.addLast(start);
        visited.add(start.pos());
        
        while(queue.size > 0 && floodTiles.size < maxTiles) {
            Tile current = queue.removeFirst();
            
            if(current.drop() == ore && Build.validPlace(Blocks.copperWall, player.team(), current.x, current.y, 0)) {
                floodTiles.add(current);
                
                // Check 8-directional neighbors for better coverage
                for(int x = -1; x <= 1; x++) {
                    for(int y = -1; y <= 1; y++) {
                        if(x == 0 && y == 0) continue;
                        
                        Tile next = current.nearby(x, y);
                        if(next != null && !visited.contains(next.pos())) {
                            visited.add(next.pos());
                            queue.addLast(next);
                        }
                    }
                }
            }
        }
    }
    
    private void placeDrills(Drill drill, Item ore) {
        placements.clear();
        placed.clear();
        tileOreCache.clear();
        
        // Pre-calculate ore counts for optimization
        for(Tile t : floodTiles) {
            tileOreCache.put(t, countOre(t, drill));
        }
        
        // Calculate optimal drill positions
        calculateOptimalPlacements(drill, ore);
        
        if(placements.isEmpty()) {
            ui.showInfoToast("No valid drill positions found!", 2f);
            return;
        }
        
        // Place drills using BuildPlan
        for(DrillPlacement dp : placements) {
            BuildPlan plan = new BuildPlan(dp.tile.x, dp.tile.y, 0, drill);
            player.unit().addBuild(plan);
        }
        
        // Place support structures
        if(drill.liquidBoostIntensity > 0 && placeWaterExtractors) {
            if(usePipeInput) {
                connectWithPipes(placements, drill);
            } else {
                placeExtractors(placements, drill);
            }
        }
        
        if(placePowerNodes) {
            placePowerNodesForDrills(placements, drill);
        }
        
        ui.showInfoToast("Placed " + placements.size + " " + drill.localizedName + "(s)!", 2.5f);
        clearCaches();
    }
    
    private void calculateOptimalPlacements(Drill drill, Item ore) {
        int size = drill.size;
        int offset = -(size - 1) / 2;
        
        Seq<ScoredPosition> scored = new Seq<>(floodTiles.size);
        
        for(Tile tile : floodTiles) {
            if(placed.contains(tile.pos())) continue;
            
            ObjectIntMap.Entry<Item> oreData = tileOreCache.get(tile);
            if(oreData == null || oreData.key != ore || oreData.value < minOres) continue;
            
            if(canPlaceDrill(tile, size, offset)) {
                scored.add(new ScoredPosition(tile, oreData.value));
            }
        }
        
        // Sort by ore count descending
        scored.sort(sp -> -sp.score);
        
        // Greedily select non-overlapping positions
        int limit = Math.min(scored.size, optimizationQuality * 100);
        for(int i = 0; i < limit && i < scored.size; i++) {
            ScoredPosition sp = scored.get(i);
            if(canPlaceDrill(sp.tile, size, offset)) {
                placements.add(new DrillPlacement(sp.tile, sp.score));
                markDrillArea(sp.tile, size, offset);
            }
        }
    }
    
    private ObjectIntMap.Entry<Item> countOre(Tile tile, Drill drill) {
        ObjectIntMap<Item> oreCount = new ObjectIntMap<>();
        Seq<Item> items = new Seq<>();
        
        for(Tile other : tile.getLinkedTilesAs(drill, new Seq<>())) {
            if(drill.canMine(other)) {
                oreCount.increment(drill.getDrop(other), 0, 1);
            }
        }
        
        if(oreCount.size == 0) return null;
        
        // Get best ore type
        for(Item i : oreCount.keys()) items.add(i);
        items.sort((i1, i2) -> {
            int priority = Boolean.compare(!i1.lowPriority, !i2.lowPriority);
            if(priority != 0) return priority;
            return Integer.compare(oreCount.get(i2, 0), oreCount.get(i1, 0));
        });
        
        Item best = items.first();
        ObjectIntMap.Entry<Item> entry = new ObjectIntMap.Entry<>();
        entry.key = best;
        entry.value = oreCount.get(best, 0);
        return entry;
    }
    
    private boolean canPlaceDrill(Tile center, int size, int offset) {
        for(int dx = 0; dx < size; dx++) {
            for(int dy = 0; dy < size; dy++) {
                Tile t = world.tile(center.x + offset + dx, center.y + offset + dy);
                if(t == null || placed.contains(t.pos()) || 
                   !Build.validPlace(Blocks.copperWall, player.team(), t.x, t.y, 0)) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private void markDrillArea(Tile center, int size, int offset) {
        for(int dx = 0; dx < size; dx++) {
            for(int dy = 0; dy < size; dy++) {
                Tile t = world.tile(center.x + offset + dx, center.y + offset + dy);
                if(t != null) placed.add(t.pos());
            }
        }
    }
    
    private void placeExtractors(Seq<DrillPlacement> drills, Drill drill) {
        Block extractor = Blocks.waterExtractor;
        IntSet extractorPlaced = new IntSet();
        int count = 0;
        
        for(DrillPlacement dp : drills) {
            Tile best = findBestExtractorSpot(dp.tile, drill.size, extractorPlaced);
            if(best != null) {
                BuildPlan plan = new BuildPlan(best.x, best.y, 0, extractor);
                player.unit().addBuild(plan);
                markArea(best, extractor.size, extractorPlaced);
                count++;
            }
        }
        
        if(count > 0) {
            ui.showInfoToast("Placed " + count + " water extractors", 1.5f);
        }
    }
    
    private Tile findBestExtractorSpot(Tile drillTile, int drillSize, IntSet occupied) {
        Point2[] edges = Edges.getEdges(drillSize);
        for(Point2 edge : edges) {
            Tile candidate = world.tile(drillTile.x + edge.x, drillTile.y + edge.y);
            if(candidate != null && canPlaceBlock(candidate, Blocks.waterExtractor, occupied)) {
                return candidate;
            }
        }
        return null;
    }
    
    private void connectWithPipes(Seq<DrillPlacement> drills, Drill drill) {
        if(drills.isEmpty()) return;
        
        Block pipe = Blocks.conduit;
        Block extractor = Blocks.waterExtractor;
        IntSet pipePos = new IntSet();
        
        // Find central location
        int avgX = 0, avgY = 0;
        for(DrillPlacement dp : drills) {
            avgX += dp.tile.x;
            avgY += dp.tile.y;
        }
        avgX /= drills.size;
        avgY /= drills.size;
        
        Tile waterSource = findNearestEmpty(avgX, avgY, Blocks.waterExtractor.size, pipePos);
        if(waterSource == null) return;
        
        BuildPlan extractorPlan = new BuildPlan(waterSource.x, waterSource.y, 0, extractor);
        player.unit().addBuild(extractorPlan);
        markArea(waterSource, extractor.size, pipePos);
        
        // Connect each drill
        int connected = 0;
        for(DrillPlacement dp : drills) {
            if(connectPipe(waterSource, dp.tile, pipe, pipePos)) {
                connected++;
            }
        }
        
        if(connected > 0) {
            ui.showInfoToast("Connected " + connected + " drills with pipes", 1.5f);
        }
    }
    
    private boolean connectPipe(Tile from, Tile to, Block pipe, IntSet existing) {
        int x = from.x, y = from.y;
        int targetX = to.x, targetY = to.y;
        boolean success = false;
        
        while(x != targetX || y != targetY) {
            if(x < targetX) x++;
            else if(x > targetX) x--;
            else if(y < targetY) y++;
            else if(y > targetY) y--;
            
            Tile current = world.tile(x, y);
            if(current != null && !existing.contains(current.pos()) && 
               Build.validPlace(pipe, player.team(), x, y, 0)) {
                BuildPlan plan = new BuildPlan(x, y, 0, pipe);
                player.unit().addBuild(plan);
                existing.add(current.pos());
                success = true;
            }
        }
        return success;
    }
    
    private void placePowerNodesForDrills(Seq<DrillPlacement> drills, Drill drill) {
        Block powerNode = Blocks.powerNode;
        IntSet powerPlaced = new IntSet();
        int count = 0;
        
        for(DrillPlacement dp : drills) {
            Tile best = findBestExtractorSpot(dp.tile, drill.size, powerPlaced);
            if(best != null) {
                BuildPlan plan = new BuildPlan(best.x, best.y, 0, powerNode);
                player.unit().addBuild(plan);
                markArea(best, powerNode.size, powerPlaced);
                count++;
            }
        }
        
        if(count > 0) {
            ui.showInfoToast("Placed " + count + " power nodes", 1.5f);
        }
    }
    
    private boolean canPlaceBlock(Tile tile, Block block, IntSet occupied) {
        int size = block.size;
        int offset = -(size - 1) / 2;
        
        for(int dx = 0; dx < size; dx++) {
            for(int dy = 0; dy < size; dy++) {
                Tile t = world.tile(tile.x + offset + dx, tile.y + offset + dy);
                if(t == null || occupied.contains(t.pos()) || 
                   !Build.validPlace(block, player.team(), t.x, t.y, 0)) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private void markArea(Tile center, int size, IntSet marked) {
        int offset = -(size - 1) / 2;
        for(int dx = 0; dx < size; dx++) {
            for(int dy = 0; dy < size; dy++) {
                Tile t = world.tile(center.x + offset + dx, center.y + offset + dy);
                if(t != null) marked.add(t.pos());
            }
        }
    }
    
    private Tile findNearestEmpty(int x, int y, int size, IntSet occupied) {
        for(int radius = 1; radius < 15; radius++) {
            for(int dx = -radius; dx <= radius; dx++) {
                for(int dy = -radius; dy <= radius; dy++) {
                    if(Math.abs(dx) + Math.abs(dy) != radius) continue;
                    Tile t = world.tile(x + dx, y + dy);
                    if(t != null && canPlaceBlock(t, Blocks.waterExtractor, occupied)) {
                        return t;
                    }
                }
            }
        }
        return null;
    }
    
    private void clearCaches() {
        tileOreCache.clear();
        floodTiles.clear();
        placements.clear();
        visited.clear();
        placed.clear();
    }
    
    private void performAutoScan() {
        if(!state.isGame() || player == null || player.unit() == null) {
            ui.showInfoToast("Cannot auto-scan: Invalid game state", 2f);
            return;
        }
        
        // Get player position
        int playerX = World.toTile(player.unit().x);
        int playerY = World.toTile(player.unit().y);
        
        // Get selected drill
        Drill selectedDrill = getDrillByTier(autoScanDrillTier);
        if(selectedDrill == null) {
            ui.showInfoToast("Invalid drill tier selected", 2f);
            return;
        }
        
        ui.showInfoToast("[accent]Scanning " + autoScanRadius + " tile radius...", 2f);
        
        // Find all ore patches within radius
        IntSet scannedTiles = new IntSet();
        Seq<Tile> oreStarts = new Seq<>();
        int totalDrills = 0;
        
        for(int dx = -autoScanRadius; dx <= autoScanRadius; dx++) {
            for(int dy = -autoScanRadius; dy <= autoScanRadius; dy++) {
                // Check if within circular radius
                if(dx * dx + dy * dy > autoScanRadius * autoScanRadius) continue;
                
                int tileX = playerX + dx;
                int tileY = playerY + dy;
                Tile tile = world.tile(tileX, tileY);
                
                if(tile == null || scannedTiles.contains(tile.pos())) continue;
                
                Item ore = tile.drop();
                if(ore != null && tile.block() == Blocks.air && selectedDrill.tier >= ore.hardness) {
                    // Found new ore patch
                    floodFill(tile, ore);
                    
                    // Mark all tiles as scanned
                    for(Tile t : floodTiles) {
                        scannedTiles.add(t.pos());
                    }
                    
                    if(floodTiles.size >= minOres) {
                        // Place drills for this patch
                        placements.clear();
                        placed.clear();
                        tileOreCache.clear();
                        
                        // Pre-calculate ore counts
                        for(Tile t : floodTiles) {
                            tileOreCache.put(t, countOre(t, selectedDrill));
                        }
                        
                        calculateOptimalPlacements(selectedDrill, ore);
                        
                        // Place drills
                        for(DrillPlacement dp : placements) {
                            BuildPlan plan = new BuildPlan(dp.tile.x, dp.tile.y, 0, selectedDrill);
                            player.unit().addBuild(plan);
                            totalDrills++;
                        }
                        
                        // Place support structures
                        if(selectedDrill.liquidBoostIntensity > 0 && placeWaterExtractors) {
                            if(usePipeInput) {
                                connectWithPipes(placements, selectedDrill);
                            } else {
                                placeExtractors(placements, selectedDrill);
                            }
                        }
                        
                        if(placePowerNodes) {
                            placePowerNodesForDrills(placements, selectedDrill);
                        }
                    }
                }
            }
        }
        
        clearCaches();
        
        if(totalDrills > 0) {
            ui.showInfoToast("[green]Auto-scan complete![] Placed " + totalDrills + " drills", 3f);
        } else {
            ui.showInfoToast("No valid ore patches found in radius", 2f);
        }
    }
    
    private Drill getDrillByTier(int tier) {
        switch(tier) {
            case 0: return (Drill)Blocks.mechanicalDrill;
            case 1: return (Drill)Blocks.pneumaticDrill;
            case 2: return (Drill)Blocks.laserDrill;
            case 3: return (Drill)Blocks.blastDrill;
            default: return null;
        }
    }
    
    private void loadSettings() {
        if(Core.settings == null) return;
        
        toggleKey = KeyCode.byName(Core.settings.getString("autodrill-key", "h"));
        autoScanKey = KeyCode.byName(Core.settings.getString("autodrill-scankey", "j"));
        maxTiles = Core.settings.getInt("autodrill-maxtiles", 1000);
        minOres = Core.settings.getInt("autodrill-minores", 2);
        placeWaterExtractors = Core.settings.getBool("autodrill-water", true);
        placePowerNodes = Core.settings.getBool("autodrill-power", true);
        usePipeInput = Core.settings.getBool("autodrill-pipes", false);
        displayToggleButton = Core.settings.getBool("autodrill-button", true);
        optimizationQuality = Core.settings.getInt("autodrill-quality", 3);
        autoScanEnabled = Core.settings.getBool("autodrill-autoscan", false);
        autoScanRadius = Core.settings.getInt("autodrill-scanradius", 20);
        autoScanDrillTier = Core.settings.getInt("autodrill-drilltier", 2);
    }
    
    private void saveSettings() {
        if(Core.settings == null) return;
        
        Core.settings.put("autodrill-key", toggleKey.name());
        Core.settings.put("autodrill-scankey", autoScanKey.name());
        Core.settings.put("autodrill-maxtiles", maxTiles);
        Core.settings.put("autodrill-minores", minOres);
        Core.settings.put("autodrill-water", placeWaterExtractors);
        Core.settings.put("autodrill-power", placePowerNodes);
        Core.settings.put("autodrill-pipes", usePipeInput);
        Core.settings.put("autodrill-button", displayToggleButton);
        Core.settings.put("autodrill-quality", optimizationQuality);
        Core.settings.put("autodrill-autoscan", autoScanEnabled);
        Core.settings.put("autodrill-scanradius", autoScanRadius);
        Core.settings.put("autodrill-drilltier", autoScanDrillTier);
    }
    
    private void resetToDefaults() {
        toggleKey = KeyCode.h;
        autoScanKey = KeyCode.j;
        maxTiles = 1000;
        minOres = 2;
        placeWaterExtractors = true;
        placePowerNodes = true;
        usePipeInput = false;
        displayToggleButton = true;
        optimizationQuality = 3;
        autoScanEnabled = false;
        autoScanRadius = 20;
        autoScanDrillTier = 2;
        saveSettings();
        ui.showInfoToast("Settings reset to defaults", 2f);
    }
    
    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("autodrill", "[on/off]", "Toggle AutoDrill mode", (args, player) -> {
            if(args.length == 0) {
                toggleMod();
            } else {
                enabled = args[0].equalsIgnoreCase("on");
                ui.showInfoToast("AutoDrill " + (enabled ? "[green]Enabled[]" : "[red]Disabled[]"), 2f);
            }
        });
        
        handler.<Player>register("autodrill-settings", "Open AutoDrill settings", (args, player) -> {
            if(settingsDialog != null) {
                settingsDialog.show();
            }
        });
        
        handler.<Player>register("autodrill-scan", "Perform auto-scan around player", (args, player) -> {
            if(autoScanEnabled) {
                performAutoScan();
            } else {
                ui.showInfoToast("Auto-scan is disabled. Enable in settings!", 2f);
            }
        });
    }
    
    // Helper classes
    private static class DrillPlacement {
        final Tile tile;
        final int score;
        
        DrillPlacement(Tile tile, int score) {
            this.tile = tile;
            this.score = score;
        }
    }
    
    private static class ScoredPosition {
        final Tile tile;
        final int score;
        
        ScoredPosition(Tile tile, int score) {
            this.tile = tile;
            this.score = score;
        }
    }
}