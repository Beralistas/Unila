package unila;

import arc.*;
import arc.graphics.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
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
import mindustry.world.blocks.production.*;
import static mindustry.Vars.*;

public class UnilaMod extends Mod {
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
    public static boolean autoScanEnabled = false;
    public static int autoScanRadius = 20;
    public static int autoScanDrillTier = 2;
    
    private Table drillDialog;
    private Button toggleButton;
    private BaseDialog settingsDialog;
    private final Seq<Tile> floodTiles = new Seq<>(512);
    private final Seq<DrillPlacement> placements = new Seq<>(128);
    private final IntSet visited = new IntSet(1024);
    private final IntSet placed = new IntSet(512);
    private final Queue<Tile> queue = new Queue<>();
    private final ObjectMap<Tile, ObjectIntMap.Entry<Item>> tileOreCache = new ObjectMap<>(256);
    
    public UnilaMod() {
        Log.info("Unila AutoDrill V8 loaded!");
        Events.on(ClientLoadEvent.class, e -> Time.runTask(10f, () -> {
            loadSettings();
            addToggleButton();
            createSettingsDialog();
        }));
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
        if(Core.input.keyTap(toggleKey)) toggleMod();
        if(Core.input.keyTap(autoScanKey)) {
            if(autoScanEnabled) performAutoScan();
            else ui.showInfoToast("Auto-scan disabled. Enable in settings!", 2f);
        }
        if(enabled && state.isGame() && !state.isPaused() && !ui.chatfrag.shown()) handleInput();
    }
    
    private void addToggleButton() {
        if(!displayToggleButton) return;
        ui.hudGroup.fill(cont -> {
            cont.name = "unila-autodrill-toggle";
            cont.bottom().right();
            toggleButton = cont.button(b -> {
                b.image(Icon.production).size(24f).padRight(6f).update(i -> i.setColor(enabled ? Color.green : Color.gray));
                b.add("AutoDrill");
            }, Styles.togglet, this::toggleMod).size(130f, 48f).margin(8f).padRight(10f).padBottom(60f)
                .update(b -> b.setChecked(enabled)).tooltip("Toggle (Hotkey: " + toggleKey + ")").get();
            cont.button(b -> b.image(Icon.settings).size(28f), Styles.clearTransi, () -> settingsDialog.show())
                .size(48f).padRight(150f).padBottom(60f).tooltip("Settings");
        });
    }
    
    private void createSettingsDialog() {
        settingsDialog = new BaseDialog("AutoDrill Settings");
        settingsDialog.cont.pane(t -> {
            t.defaults().size(420f, 60f).pad(4f);
            t.table(h -> {h.image(Icon.settings).size(32f).padRight(8f); h.add("[accent]Basic Settings[]").left();}).left().padBottom(8f).row();
            t.image().color(Color.accent).fillX().height(3f).pad(4f).row();
            
            t.table(k -> {k.left(); k.image(Icon.add).size(24f).padRight(8f); k.add("Activation Key: ").left();
                k.button(b -> {b.image(Icon.pencil).size(20f).padRight(4f); b.add(toggleKey.toString());}, Styles.flatTogglet, () -> {
                    ui.showTextInput("Set Key", "Enter key (h, k, etc.)", 10, toggleKey.toString(), text -> {
                        try {toggleKey = KeyCode.byName(text.toLowerCase()); saveSettings(); ui.showInfoToast("Key: " + toggleKey, 2f);}
                        catch(Exception e) {ui.showInfoToast("Invalid key!", 2f);}
                    });
                }).width(120f);
            }).left().row();
            
            t.table(b -> {b.left(); b.image(Icon.eye).size(24f).padRight(8f);
                b.check("Display Toggle Button", displayToggleButton, v -> {displayToggleButton = v; saveSettings(); ui.showInfoToast("Restart needed", 2f);}).left();
            }).left().row();
            
            t.table(m -> {m.left(); m.image(Icon.zoom).size(24f).padRight(8f); m.add("Max Tiles: ").left();
                Label ml = m.add(maxTiles + "").width(60f).left().color(Color.accent).get(); m.row(); m.add().width(32f);
                m.slider(100, 5000, 100, maxTiles, v -> {maxTiles = (int)v; ml.setText((int)v + ""); saveSettings();}).width(320f);
            }).left().row();
            
            t.table(m -> {m.left(); m.image(Icon.units).size(24f).padRight(8f); m.add("Min Ores: ").left();
                Label ml = m.add(minOres + "").width(60f).left().color(Color.accent).get(); m.row(); m.add().width(32f);
                m.slider(1, 10, 1, minOres, v -> {minOres = (int)v; ml.setText((int)v + ""); saveSettings();}).width(320f);
            }).left().row();
            
            t.table(o -> {o.left(); o.image(Icon.effect).size(24f).padRight(8f); o.add("Quality: ").left();
                Label ol = o.add(optimizationQuality + "").width(60f).left().color(Color.accent).get(); o.row(); o.add().width(32f);
                o.slider(1, 10, 1, optimizationQuality, v -> {optimizationQuality = (int)v; ol.setText((int)v + ""); saveSettings();}).width(320f);
            }).left().row();
            
            t.table(w -> {w.left(); w.image(Icon.liquid).size(24f).padRight(8f);
                w.check("Water Extractors", placeWaterExtractors, v -> {placeWaterExtractors = v; saveSettings();}).left();
            }).left().row();
            
            t.table(p -> {p.left(); p.image(Icon.power).size(24f).padRight(8f);
                p.check("Power Nodes", placePowerNodes, v -> {placePowerNodes = v; saveSettings();}).left();
            }).left().row();
            
            t.table(p -> {p.left(); p.image(Icon.distribution).size(24f).padRight(8f);
                p.check("Pipe Mode", usePipeInput, v -> {usePipeInput = v; saveSettings();}).left();
            }).left().row();
            
            t.add("").row();
            t.table(s -> {s.image(Icon.upload).size(32f).padRight(8f); s.add("[accent]Auto-Scan[]").left();}).left().padTop(20f).row();
            t.image().color(Color.accent).fillX().height(3f).pad(4f).row();
            
            t.table(e -> {e.left(); e.image(Icon.waves).size(24f).padRight(8f);
                e.check("Enable Auto-Scan", autoScanEnabled, v -> {autoScanEnabled = v; saveSettings();}).left();
            }).left().row();
            
            t.table(k -> {k.left(); k.image(Icon.play).size(24f).padRight(8f); k.add("Scan Key: ").left();
                k.button(b -> {b.image(Icon.pencil).size(20f).padRight(4f); b.add(autoScanKey.toString());}, Styles.flatTogglet, () -> {
                    ui.showTextInput("Set Scan Key", "Enter key (j, k, etc.)", 10, autoScanKey.toString(), text -> {
                        try {autoScanKey = KeyCode.byName(text.toLowerCase()); saveSettings(); ui.showInfoToast("Scan key: " + autoScanKey, 2f);}
                        catch(Exception e) {ui.showInfoToast("Invalid!", 2f);}
                    });
                }).width(120f);
            }).left().row();
            
            t.table(r -> {r.left(); r.image(Icon.rotate).size(24f).padRight(8f); r.add("Radius: ").left();
                Label rl = r.add(autoScanRadius + " tiles").width(90f).left().color(Color.accent).get(); r.row(); r.add().width(32f);
                r.slider(5, 50, 1, autoScanRadius, v -> {autoScanRadius = (int)v; rl.setText((int)v + " tiles"); saveSettings();}).width(320f);
            }).left().row();
            
            t.table(d -> {d.left(); d.image(Icon.production).size(24f).padRight(8f); d.add("Drill: ").left();
                String[] dn = {"Mechanical", "Pneumatic", "Laser", "Blast"}; Label dl = d.add(dn[autoScanDrillTier]).width(110f).left().color(Color.orange).get();
                d.row(); d.add().width(32f); d.slider(0, 3, 1, autoScanDrillTier, v -> {autoScanDrillTier = (int)v; dl.setText(dn[(int)v]); saveSettings();}).width(320f);
            }).left().row();
            
            t.table(i -> {i.left(); i.image(Icon.info).size(20f).padRight(8f).color(Color.lightGray);
                i.add("[lightgray]Press " + autoScanKey + " to scan[]").left().scale(0.85f);
            }).left().padTop(10f).row();
        }).grow();
        settingsDialog.addCloseButton();
        settingsDialog.buttons.button(b -> {b.image(Icon.refresh).size(24f).padRight(6f); b.add("Reset");}, () -> {
            resetToDefaults(); settingsDialog.hide(); Time.runTask(5f, () -> settingsDialog.show());
        }).size(200f, 50f);
    }
    
    private void toggleMod() {
        enabled = !enabled;
        ui.showInfoToast("AutoDrill " + (enabled ? "[green]ON[]" : "[red]OFF[]"), 2f);
    }
    
    private void handleInput() {
        if(Core.input.keyTap(KeyCode.mouseLeft) && !Core.scene.hasMouse()) {
            Tile selected = world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
            if(selected != null && selected.drop() != null && selected.block() == Blocks.air) showDrillOptions(selected);
        }
    }
    
    private void showDrillOptions(Tile tile) {
        if(drillDialog != null) drillDialog.remove();
        Item ore = tile.drop();
        if(ore == null) return;
        floodFill(tile, ore);
        if(floodTiles.size < minOres) {
            ui.showInfoToast("Not enough ore! (" + floodTiles.size + "/" + minOres + ")", 2f);
            return;
        }
        drillDialog = new Table(Tex.buttonEdge3);
        drillDialog.margin(8f);
        Vec2 mp = Core.input.mouse();
        drillDialog.setPosition(Mathf.clamp(mp.x + 10f, 0, Core.graphics.getWidth() - 220), 
                               Mathf.clamp(mp.y - 10f, 0, Core.graphics.getHeight() - 350));
        drillDialog.update(() -> {
            if(Core.input.keyTap(KeyCode.mouseLeft) || Core.input.keyTap(KeyCode.mouseRight) || Core.input.keyTap(KeyCode.escape)) {
                drillDialog.remove(); drillDialog = null;
            }
        });
        drillDialog.table(h -> {h.image(Icon.production).size(28f).padRight(8f).color(Color.accent); h.add("[accent]Select Drill[]").center();}).fillX().pad(6f).row();
        drillDialog.image().color(Color.accent).fillX().height(3f).pad(4f).row();
        drillDialog.table(t -> {
            t.defaults().size(190f, 58f).pad(3f);
            addDrillButton(t, Blocks.mechanicalDrill, ore);
            addDrillButton(t, Blocks.pneumaticDrill, ore);
            t.row();
            addDrillButton(t, Blocks.laserDrill, ore);
            addDrillButton(t, Blocks.blastDrill, ore);
        }).row();
        drillDialog.image().color(Color.gray).fillX().height(2f).pad(4f).row();
        drillDialog.table(i -> {
            i.defaults().left().pad(3f);
            i.table(p -> {p.image(Icon.zoom).size(18f).padRight(6f).color(Color.lightGray); p.add("[lightgray]" + floodTiles.size + " tiles[]");}).left().row();
            i.table(o -> {o.image(ore.uiIcon).size(18f).padRight(6f); o.add("[lightgray]" + ore.localizedName + "[]");}).left();
        }).fillX().padTop(4f).row();
        drillDialog.image().color(Color.gray).fillX().height(2f).pad(4f).row();
        drillDialog.table(o -> {
            o.defaults().left().pad(3f);
            o.table(w -> {w.image(Icon.liquid).size(20f).padRight(4f).color(placeWaterExtractors ? Color.cyan : Color.gray);
                w.check("Water", placeWaterExtractors, v -> placeWaterExtractors = v);}).left();
            o.table(p -> {p.image(Icon.power).size(20f).padRight(4f).color(placePowerNodes ? Color.yellow : Color.gray);
                p.check("Power", placePowerNodes, v -> placePowerNodes = v);}).left();
            o.row();
            o.table(p -> {p.image(Icon.distribution).size(20f).padRight(4f).color(usePipeInput ? Color.royal : Color.gray);
                p.check("Pipe", usePipeInput, v -> usePipeInput = v);}).left().colspan(2);
        }).fillX().padTop(6f);
        Core.scene.add(drillDialog);
    }
    
    private void addDrillButton(Table table, Block block, Item ore) {
        if(!(block instanceof Drill drill)) return;
        boolean canMine = drill.tier >= ore.hardness;
        table.button(b -> {
            b.left(); b.image(block.uiIcon).size(40f).padLeft(4f).padRight(8f);
            b.table(t -> {
                t.add(block.localizedName).color(canMine ? Color.white : Color.gray).left().row();
                t.add("[lightgray]" + drill.size + "x" + drill.size + "[]").scale(0.8f).left();
            }).growX().left();
        }, Styles.flatToggleMenut, () -> {
            if(canMine) {placeDrills(drill, ore); if(drillDialog != null) {drillDialog.remove(); drillDialog = null;}}
        }).disabled(!canMine);
    }
    
    private void floodFill(Tile start, Item ore) {
        floodTiles.clear(); queue.clear(); visited.clear();
        queue.addLast(start); visited.add(start.pos());
        while(queue.size > 0 && floodTiles.size < maxTiles) {
            Tile c = queue.removeFirst();
            if(c.drop() == ore && Build.validPlace(Blocks.copperWall, player.team(), c.x, c.y, 0)) {
                floodTiles.add(c);
                for(int x = -1; x <= 1; x++) for(int y = -1; y <= 1; y++) {
                    if(x == 0 && y == 0) continue;
                    Tile n = c.nearby(x, y);
                    if(n != null && !visited.contains(n.pos())) {visited.add(n.pos()); queue.addLast(n);}
                }
            }
        }
    }
    
    private void placeDrills(Drill drill, Item ore) {
        placements.clear(); placed.clear(); tileOreCache.clear();
        for(Tile t : floodTiles) tileOreCache.put(t, countOre(t, drill));
        calculateOptimalPlacements(drill, ore);
        if(placements.isEmpty()) {ui.showInfoToast("No valid spots!", 2f); return;}
        for(DrillPlacement dp : placements) player.unit().addBuild(new BuildPlan(dp.tile.x, dp.tile.y, 0, drill));
        if(drill.liquidBoostIntensity > 0 && placeWaterExtractors) {
            if(usePipeInput) connectWithPipes(drill);
            else placeExtractors(drill);
        }
        if(placePowerNodes) placePowerNodesForDrills(drill);
        ui.showInfoToast("Placed " + placements.size + " drills!", 2.5f);
        clearCaches();
    }
    
    private void calculateOptimalPlacements(Drill drill, Item ore) {
        int sz = drill.size, off = -(sz - 1) / 2;
        Seq<ScoredPosition> scored = new Seq<>();
        for(Tile t : floodTiles) {
            if(placed.contains(t.pos())) continue;
            ObjectIntMap.Entry<Item> od = tileOreCache.get(t);
            if(od == null || od.key != ore || od.value < minOres) continue;
            if(canPlaceDrill(t, sz, off)) scored.add(new ScoredPosition(t, od.value));
        }
        scored.sort(sp -> -sp.score);
        int lim = Math.min(scored.size, optimizationQuality * 100);
        for(int i = 0; i < lim; i++) {
            ScoredPosition sp = scored.get(i);
            if(canPlaceDrill(sp.tile, sz, off)) {placements.add(new DrillPlacement(sp.tile, sp.score)); markDrillArea(sp.tile, sz, off);}
        }
    }
    
    private ObjectIntMap.Entry<Item> countOre(Tile tile, Drill drill) {
        ObjectIntMap<Item> oc = new ObjectIntMap<>(); Seq<Item> items = new Seq<>();
        for(Tile o : tile.getLinkedTilesAs(drill, new Seq<>())) if(drill.canMine(o)) oc.increment(drill.getDrop(o), 0, 1);
        if(oc.size == 0) return null;
        for(Item i : oc.keys()) items.add(i);
        items.sort((i1, i2) -> Integer.compare(oc.get(i2, 0), oc.get(i1, 0)));
        Item best = items.first();
        ObjectIntMap.Entry<Item> e = new ObjectIntMap.Entry<>();
        e.key = best; e.value = oc.get(best, 0);
        return e;
    }
    
    private boolean canPlaceDrill(Tile c, int sz, int off) {
        for(int x = 0; x < sz; x++) for(int y = 0; y < sz; y++) {
            Tile t = world.tile(c.x + off + x, c.y + off + y);
            if(t == null || placed.contains(t.pos()) || !Build.validPlace(Blocks.copperWall, player.team(), t.x, t.y, 0)) return false;
        }
        return true;
    }
    
    private void markDrillArea(Tile c, int sz, int off) {
        for(int x = 0; x < sz; x++) for(int y = 0; y < sz; y++) {
            Tile t = world.tile(c.x + off + x, c.y + off + y);
            if(t != null) placed.add(t.pos());
        }
    }
    
    private void placeExtractors(Drill drill) {
        Block ext = Blocks.waterExtractor; IntSet ep = new IntSet(); int cnt = 0;
        for(DrillPlacement dp : placements) {
            Tile best = findBestSpot(dp.tile, drill.size, ep);
            if(best != null) {player.unit().addBuild(new BuildPlan(best.x, best.y, 0, ext)); markArea(best, ext.size, ep); cnt++;}
        }
        if(cnt > 0) ui.showInfoToast("+" + cnt + " extractors", 1.5f);
    }
    
    private Tile findBestSpot(Tile dt, int ds, IntSet occ) {
        Point2[] edges = Edges.getEdges(ds);
        for(Point2 e : edges) {
            Tile c = world.tile(dt.x + e.x, dt.y + e.y);
            if(c != null && canPlaceBlock(c, Blocks.waterExtractor, occ)) return c;
        }
        return null;
    }
    
    private void connectWithPipes(Drill drill) {
        if(placements.isEmpty()) return;
        Block pipe = Blocks.conduit, ext = Blocks.waterExtractor; IntSet pp = new IntSet();
        int ax = 0, ay = 0;
        for(DrillPlacement dp : placements) {ax += dp.tile.x; ay += dp.tile.y;}
        ax /= placements.size; ay /= placements.size;
        Tile ws = findNearestEmpty(ax, ay, ext.size, pp);
        if(ws == null) return;
        player.unit().addBuild(new BuildPlan(ws.x, ws.y, 0, ext));
        markArea(ws, ext.size, pp);
        int con = 0;
        for(DrillPlacement dp : placements) if(connectPipe(ws, dp.tile, pipe, pp)) con++;
        if(con > 0) ui.showInfoToast("+" + con + " pipes", 1.5f);
    }
    
    private boolean connectPipe(Tile from, Tile to, Block pipe, IntSet ex) {
        int x = from.x, y = from.y, tx = to.x, ty = to.y; boolean ok = false;
        while(x != tx || y != ty) {
            if(x < tx) x++; else if(x > tx) x--; else if(y < ty) y++; else if(y > ty) y--;
            Tile c = world.tile(x, y);
            if(c != null && !ex.contains(c.pos()) && Build.validPlace(pipe, player.team(), x, y, 0)) {
                player.unit().addBuild(new BuildPlan(x, y, 0, pipe)); ex.add(c.pos()); ok = true;
            }
        }
        return ok;
    }
    
    private void placePowerNodesForDrills(Drill drill) {
        Block pn = Blocks.powerNode; IntSet pp = new IntSet(); int cnt = 0;
        for(DrillPlacement dp : placements) {
            Tile best = findBestSpot(dp.tile, drill.size, pp);
            if(best != null) {player.unit().addBuild(new BuildPlan(best.x, best.y, 0, pn)); markArea(best, pn.size, pp); cnt++;}
        }
        if(cnt > 0) ui.showInfoToast("+" + cnt + " nodes", 1.5f);
    }
    
    private boolean canPlaceBlock(Tile t, Block b, IntSet occ) {
        int sz = b.size, off = -(sz - 1) / 2;
        for(int x = 0; x < sz; x++) for(int y = 0; y < sz; y++) {
            Tile ti = world.tile(t.x + off + x, t.y + off + y);
            if(ti == null || occ.contains(ti.pos()) || !Build.validPlace(b, player.team(), ti.x, ti.y, 0)) return false;
        }
        return true;
    }
    
    private void markArea(Tile c, int sz, IntSet m) {
        int off = -(sz - 1) / 2;
        for(int x = 0; x < sz; x++) for(int y = 0; y < sz; y++) {
            Tile t = world.tile(c.x + off + x, c.y + off + y);
            if(t != null) m.add(t.pos());
        }
    }
    
    private Tile findNearestEmpty(int x, int y, int sz, IntSet occ) {
        for(int r = 1; r < 15; r++) for(int dx = -r; dx <= r; dx++) for(int dy = -r; dy <= r; dy++) {
            if(Math.abs(dx) + Math.abs(dy) != r) continue;
            Tile t = world.tile(x + dx, y + dy);
            if(t != null && canPlaceBlock(t, Blocks.waterExtractor, occ)) return t;
        }
        return null;
    }
    
    private void clearCaches() {
        tileOreCache.clear(); floodTiles.clear(); placements.clear(); visited.clear(); placed.clear();
    }
    
    private void performAutoScan() {
        if(!state.isGame() || player == null || player.unit() == null) {
            ui.showInfoToast("Cannot scan: Invalid state", 2f); return;
        }
        int px = World.toTile(player.unit().x), py = World.toTile(player.unit().y);
        Drill sd = getDrillByTier(autoScanDrillTier);
        if(sd == null) {ui.showInfoToast("Invalid drill tier", 2f); return;}
        ui.showInfoToast("[accent]Scanning...", 2f);
        IntSet scanned = new IntSet(); int total = 0;
        for(int dx = -autoScanRadius; dx <= autoScanRadius; dx++) for(int dy = -autoScanRadius; dy <= autoScanRadius; dy++) {
            if(dx * dx + dy * dy > autoScanRadius * autoScanRadius) continue;
            Tile t = world.tile(px + dx, py + dy);
            if(t == null || scanned.contains(t.pos())) continue;
            Item ore = t.drop();
            if(ore != null && t.block() == Blocks.air && sd.tier >= ore.hardness) {
                floodFill(t, ore);
                for(Tile ft : floodTiles) scanned.add(ft.pos());
                if(floodTiles.size >= minOres) {
                    placements.clear(); placed.clear(); tileOreCache.clear();
                    for(Tile ft : floodTiles) tileOreCache.put(ft, countOre(ft, sd));
                    calculateOptimalPlacements(sd, ore);
                    for(DrillPlacement dp : placements) {player.unit().addBuild(new BuildPlan(dp.tile.x, dp.tile.y, 0, sd)); total++;}
                    if(sd.liquidBoostIntensity > 0 && placeWaterExtractors) {
                        if(usePipeInput) connectWithPipes(sd);
                        else placeExtractors(sd);
                    }
                    if(placePowerNodes) placePowerNodesForDrills(sd);
                }
            }
        }
        clearCaches();
        if(total > 0) ui.showInfoToast("[green]Placed " + total + " drills!", 3f);
        else ui.showInfoToast("No patches found", 2f);
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
        toggleKey = KeyCode.byName(Core.settings.getString("unila-key", "h"));
        autoScanKey = KeyCode.byName(Core.settings.getString("unila-scankey", "j"));
        maxTiles = Core.settings.getInt("unila-maxtiles", 1000);
        minOres = Core.settings.getInt("unila-minores", 2);
        placeWaterExtractors = Core.settings.getBool("unila-water", true);
        placePowerNodes = Core.settings.getBool("unila-power", true);
        usePipeInput = Core.settings.getBool("unila-pipes", false);
        displayToggleButton = Core.settings.getBool("unila-button", true);
        optimizationQuality = Core.settings.getInt("unila-quality", 3);
        autoScanEnabled = Core.settings.getBool("unila-autoscan", false);
        autoScanRadius = Core.settings.getInt("unila-scanradius", 20);
        autoScanDrillTier = Core.settings.getInt("unila-drilltier", 2);
    }
    
    private void saveSettings() {
        if(Core.settings == null) return;
        Core.settings.put("unila-key", toggleKey.name());
        Core.settings.put("unila-scankey", autoScanKey.name());
        Core.settings.put("unila-maxtiles", maxTiles);
        Core.settings.put("unila-minores", minOres);
        Core.settings.put("unila-water", placeWaterExtractors);
        Core.settings.put("unila-power", placePowerNodes);
        Core.settings.put("unila-pipes", usePipeInput);
        Core.settings.put("unila-button", displayToggleButton);
        Core.settings.put("unila-quality", optimizationQuality);
        Core.settings.put("unila-autoscan", autoScanEnabled);
        Core.settings.put("unila-scanradius", autoScanRadius);
        Core.settings.put("unila-drilltier", autoScanDrillTier);
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
        ui.showInfoToast("Settings reset!", 2f);
    }
    
    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("autodrill", "[on/off]", "Toggle AutoDrill", (args, player) -> {
            if(args.length == 0) toggleMod();
            else {
                enabled = args[0].equalsIgnoreCase("on");
                ui.showInfoToast("AutoDrill " + (enabled ? "[green]ON[]" : "[red]OFF[]"), 2f);
            }
        });
        handler.<Player>register("autodrill-settings", "Open settings", (args, player) -> {
            if(settingsDialog != null) settingsDialog.show();
        });
        handler.<Player>register("autodrill-scan", "Perform auto-scan", (args, player) -> {
            if(autoScanEnabled) performAutoScan();
            else ui.showInfoToast("Enable auto-scan in settings!", 2f);
        });
    }
    
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