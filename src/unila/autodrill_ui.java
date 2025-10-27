package unila;

import arc.Core;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.*;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.production.Drill;
import static mindustry.Vars.*;

/**
 * User interface components for AutoDrill
 * Handles the toggle button, drill selection dialog, and settings dialog
 */
public class AutoDrillUI {
    private final AutoDrillManager manager;
    
    private Table drillDialog;
    private Button toggleButton;
    private BaseDialog settingsDialog;
    
    private Tile selectedTile;
    private Seq<Tile> currentFloodTiles;
    
    public AutoDrillUI(AutoDrillManager manager) {
        this.manager = manager;
        manager.setOnShowDrillOptions(() -> handleTileSelection());
    }
    
    /**
     * Build all UI components
     */
    public void build() {
        if (Config.displayToggleButton) {
            addToggleButton();
        }
        createSettingsDialog();
    }
    
    /**
     * Show settings dialog
     */
    public void showSettings() {
        if (settingsDialog != null) {
            settingsDialog.show();
        }
    }
    
    /**
     * Handle tile selection for drill placement
     */
    private void handleTileSelection() {
        if (!manager.isEnabled()) return;
        
        Tile tile = world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
        if (tile == null || tile.drop() == null || tile.block() != Blocks.air) return;
        
        selectedTile = tile;
        Item ore = tile.drop();
        
        currentFloodTiles = manager.getFloodTiles(tile, ore);
        
        if (currentFloodTiles.size < Config.minOres) {
            ui.showInfoToast("Not enough ore! (" + currentFloodTiles.size + "/" + Config.minOres + ")", 2f);
            return;
        }
        
        showDrillSelectionDialog(tile, ore);
    }
    
    /**
     * Show drill selection dialog
     */
    private void showDrillSelectionDialog(Tile tile, Item ore) {
        if (drillDialog != null) {
            drillDialog.remove();
        }
        
        drillDialog = new Table(Tex.buttonEdge3);
        drillDialog.margin(8f);
        
        // Position near mouse cursor
        Vec2 mousePos = Core.input.mouse();
        float dialogX = Mathf.clamp(mousePos.x + 10f, 0, Core.graphics.getWidth() - 220);
        float dialogY = Mathf.clamp(mousePos.y - 10f, 0, Core.graphics.getHeight() - 350);
        drillDialog.setPosition(dialogX, dialogY);
        
        // Close on click outside
        drillDialog.update(() -> {
            if (Core.input.keyTap(KeyCode.mouseLeft) || 
                Core.input.keyTap(KeyCode.mouseRight) || 
                Core.input.keyTap(KeyCode.escape)) {
                drillDialog.remove();
                drillDialog = null;
            }
        });
        
        // Header
        drillDialog.table(header -> {
            header.image(Icon.production).size(28f).padRight(8f).color(Color.accent);
            header.add("[accent]Select Drill[]").center();
        }).fillX().pad(6f).row();
        
        drillDialog.image().color(Color.accent).fillX().height(3f).pad(4f).row();
        
        // Drill buttons
        drillDialog.table(buttons -> {
            buttons.defaults().size(190f, 58f).pad(3f);
            
            addDrillButton(buttons, Blocks.mechanicalDrill, ore);
            addDrillButton(buttons, Blocks.pneumaticDrill, ore);
            buttons.row();
            addDrillButton(buttons, Blocks.laserDrill, ore);
            addDrillButton(buttons, Blocks.blastDrill, ore);
        }).row();
        
        drillDialog.image().color(Color.gray).fillX().height(2f).pad(4f).row();
        
        // Info section
        drillDialog.table(info -> {
            info.defaults().left().pad(3f);
            info.table(tiles -> {
                tiles.image(Icon.zoom).size(18f).padRight(6f).color(Color.lightGray);
                tiles.add("[lightgray]" + currentFloodTiles.size + " tiles[]");
            }).left().row();
            
            info.table(oreInfo -> {
                oreInfo.image(ore.uiIcon).size(18f).padRight(6f);
                oreInfo.add("[lightgray]" + ore.localizedName + "[]");
            }).left();
        }).fillX().padTop(4f).row();
        
        drillDialog.image().color(Color.gray).fillX().height(2f).pad(4f).row();
        
        // Options
        drillDialog.table(options -> {
            options.defaults().left().pad(3f);
            
            options.table(water -> {
                Color waterColor = Config.placeWaterExtractors ? Color.cyan : Color.gray;
                water.image(Icon.liquid).size(20f).padRight(4f).color(waterColor);
                water.check("Water", Config.placeWaterExtractors, v -> {
                    Config.placeWaterExtractors = v;
                    Config.save();
                });
            }).left();
            
            options.table(power -> {
                Color powerColor = Config.placePowerNodes ? Color.yellow : Color.gray;
                power.image(Icon.power).size(20f).padRight(4f).color(powerColor);
                power.check("Power", Config.placePowerNodes, v -> {
                    Config.placePowerNodes = v;
                    Config.save();
                });
            }).left();
            
            options.row();
            
            options.table(pipe -> {
                Color pipeColor = Config.usePipeInput ? Color.royal : Color.gray;
                pipe.image(Icon.distribution).size(20f).padRight(4f).color(pipeColor);
                pipe.check("Pipe", Config.usePipeInput, v -> {
                    Config.usePipeInput = v;
                    Config.save();
                });
            }).left().colspan(2);
        }).fillX().padTop(6f);
        
        Core.scene.add(drillDialog);
    }
    
    /**
     * Add drill button to the selection dialog
     */
    private void addDrillButton(Table table, Block block, Item ore) {
        if (!(block instanceof Drill drill)) return;
        
        boolean canMine = drill.tier >= ore.hardness;
        
        table.button(button -> {
            button.left();
            button.image(block.uiIcon).size(40f).padLeft(4f).padRight(8f);
            button.table(info -> {
                Color textColor = canMine ? Color.white : Color.gray;
                info.add(block.localizedName).color(textColor).left().row();
                info.add("[lightgray]" + drill.size + "x" + drill.size + "[]").scale(0.8f).left();
            }).growX().left();
        }, Styles.flatToggleMenut, () -> {
            if (canMine) {
                manager.placeDrills(drill, ore);
                if (drillDialog != null) {
                    drillDialog.remove();
                    drillDialog = null;
                }
            }
        }).disabled(!canMine);
    }
    
    /**
     * Add toggle button to HUD
     */
    private void addToggleButton() {
        ui.hudGroup.fill(container -> {
            container.name = "unila-autodrill-toggle";
            container.bottom().right();
            
            toggleButton = container.button(button -> {
                button.image(Icon.production).size(24f).padRight(6f)
                    .update(img -> img.setColor(manager.isEnabled() ? Color.green : Color.gray));
                button.add("AutoDrill");
            }, Styles.togglet, () -> manager.toggle())
                .size(130f, 48f)
                .margin(8f)
                .padRight(10f)
                .padBottom(60f)
                .update(btn -> btn.setChecked(manager.isEnabled()))
                .tooltip("Toggle AutoDrill (Hotkey: " + Config.toggleKey + ")")
                .get();
            
            container.button(button -> button.image(Icon.settings).size(28f), 
                Styles.clearTransi, 
                this::showSettings)
                .size(48f)
                .padRight(150f)
                .padBottom(60f)
                .tooltip("AutoDrill Settings");
        });
    }
    
    /**
     * Create settings dialog
     */
    private void createSettingsDialog() {
        settingsDialog = new BaseDialog("AutoDrill Settings");
        
        settingsDialog.cont.pane(pane -> {
            pane.defaults().size(420f, 60f).pad(4f);
            
            // Header
            pane.table(header -> {
                header.image(Icon.settings).size(32f).padRight(8f);
                header.add("[accent]Basic Settings[]").left();
            }).left().padBottom(8f).row();
            
            pane.image().color(Color.accent).fillX().height(3f).pad(4f).row();
            
            // Activation key
            pane.table(keyRow -> {
                keyRow.left();
                keyRow.image(Icon.add).size(24f).padRight(8f);
                keyRow.add("Activation Key: ").left();
                keyRow.button(btn -> {
                    btn.image(Icon.pencil).size(20f).padRight(4f);
                    btn.add(Config.toggleKey.toString());
                }, Styles.flatTogglet, () -> {
                    ui.showTextInput("Set Activation Key", "Enter key (h, k, etc.)", 10, 
                        Config.toggleKey.toString(), text -> {
                            try {
                                Config.toggleKey = KeyCode.byName(text.toLowerCase());
                                Config.save();
                                ui.showInfoToast("Key set to: " + Config.toggleKey, 2f);
                            } catch (Exception e) {
                                ui.showInfoToast("Invalid key!", 2f);
                            }
                        });
                }).width(120f);
            }).left().row();
            
            // Display toggle button
            pane.table(btnRow -> {
                btnRow.left();
                btnRow.image(Icon.eye).size(24f).padRight(8f);
                btnRow.check("Display Toggle Button", Config.displayToggleButton, value -> {
                    Config.displayToggleButton = value;
                    Config.save();
                    ui.showInfoToast("Restart required to take effect", 2f);
                }).left();
            }).left().row();
            
            // Max tiles slider
            pane.table(maxRow -> {
                maxRow.left();
                maxRow.image(Icon.zoom).size(24f).padRight(8f);
                maxRow.add("Max Tiles: ").left();
                Label maxLabel = maxRow.add(Config.maxTiles + "").width(60f).left().color(Color.accent).get();
                maxRow.row();
                maxRow.add().width(32f);
                maxRow.slider(100, 5000, 100, Config.maxTiles, value -> {
                    Config.maxTiles = (int)value;
                    maxLabel.setText((int)value + "");
                    Config.save();
                }).width(320f);
            }).left().row();
            
            // Min ores slider
            pane.table(minRow -> {
                minRow.left();
                minRow.image(Icon.units).size(24f).padRight(8f);
                minRow.add("Min Ores: ").left();
                Label minLabel = minRow.add(Config.minOres + "").width(60f).left().color(Color.accent).get();
                minRow.row();
                minRow.add().width(32f);
                minRow.slider(1, 10, 1, Config.minOres, value -> {
                    Config.minOres = (int)value;
                    minLabel.setText((int)value + "");
                    Config.save();
                }).width(320f);
            }).left().row();
            
            // Optimization quality slider
            pane.table(qualRow -> {
                qualRow.left();
                qualRow.image(Icon.effect).size(24f).padRight(8f);
                qualRow.add("Optimization Quality: ").left();
                Label qualLabel = qualRow.add(Config.optimizationQuality + "").width(60f).left().color(Color.accent).get();
                qualRow.row();
                qualRow.add().width(32f);
                qualRow.slider(1, 10, 1, Config.optimizationQuality, value -> {
                    Config.optimizationQuality = (int)value;
                    qualLabel.setText((int)value + "");
                    Config.save();
                }).width(320f);
            }).left().row();
            
            // Water extractors checkbox
            pane.table(waterRow -> {
                waterRow.left();
                waterRow.image(Icon.liquid).size(24f).padRight(8f);
                waterRow.check("Place Water Extractors", Config.placeWaterExtractors, value -> {
                    Config.placeWaterExtractors = value;
                    Config.save();
                }).left();
            }).left().row();
            
            // Power nodes checkbox
            pane.table(powerRow -> {
                powerRow.left();
                powerRow.image(Icon.power).size(24f).padRight(8f);
                powerRow.check("Place Power Nodes", Config.placePowerNodes, value -> {
                    Config.placePowerNodes = value;
                    Config.save();
                }).left();
            }).left().row();
            
            // Pipe mode checkbox
            pane.table(pipeRow -> {
                pipeRow.left();
                pipeRow.image(Icon.distribution).size(24f).padRight(8f);
                pipeRow.check("Use Pipe Mode", Config.usePipeInput, value -> {
                    Config.usePipeInput = value;
                    Config.save();
                }).left();
            }).left().row();
            
            pane.add("").row();
            
            // Auto-scan header
            pane.table(scanHeader -> {
                scanHeader.image(Icon.upload).size(32f).padRight(8f);
                scanHeader.add("[accent]Auto-Scan Settings[]").left();
            }).left().padTop(20f).row();
            
            pane.image().color(Color.accent).fillX().height(3f).pad(4f).row();
            
            // Enable auto-scan
            pane.table(enableRow -> {
                enableRow.left();
                enableRow.image(Icon.waves).size(24f).padRight(8f);
                enableRow.check("Enable Auto-Scan", Config.autoScanEnabled, value -> {
                    Config.autoScanEnabled = value;
                    Config.save();
                }).left();
            }).left().row();
            
            // Scan key
            pane.table(scanKeyRow -> {
                scanKeyRow.left();
                scanKeyRow.image(Icon.play).size(24f).padRight(8f);
                scanKeyRow.add("Scan Key: ").left();
                scanKeyRow.button(btn -> {
                    btn.image(Icon.pencil).size(20f).padRight(4f);
                    btn.add(Config.autoScanKey.toString());
                }, Styles.flatTogglet, () -> {
                    ui.showTextInput("Set Scan Key", "Enter key (j, k, etc.)", 10, 
                        Config.autoScanKey.toString(), text -> {
                            try {
                                Config.autoScanKey = KeyCode.byName(text.toLowerCase());
                                Config.save();
                                ui.showInfoToast("Scan key set to: " + Config.autoScanKey, 2f);
                            } catch (Exception e) {
                                ui.showInfoToast("Invalid key!", 2f);
                            }
                        });
                }).width(120f);
            }).left().row();
            
            // Scan radius slider
            pane.table(radiusRow -> {
                radiusRow.left();
                radiusRow.image(Icon.rotate).size(24f).padRight(8f);
                radiusRow.add("Scan Radius: ").left();
                Label radiusLabel = radiusRow.add(Config.autoScanRadius + " tiles").width(90f).left().color(Color.accent).get();
                radiusRow.row();
                radiusRow.add().width(32f);
                radiusRow.slider(5, 50, 1, Config.autoScanRadius, value -> {
                    Config.autoScanRadius = (int)value;
                    radiusLabel.setText((int)value + " tiles");
                    Config.save();
                }).width(320f);
            }).left().row();
            
            // Drill tier slider
            pane.table(tierRow -> {
                tierRow.left();
                tierRow.image(Icon.production).size(24f).padRight(8f);
                tierRow.add("Drill Tier: ").left();
                Label tierLabel = tierRow.add(DrillUtility.getDrillTierName(Config.autoScanDrillTier))
                    .width(110f).left().color(Color.orange).get();
                tierRow.row();
                tierRow.add().width(32f);
                tierRow.slider(0, 3, 1, Config.autoScanDrillTier, value -> {
                    Config.autoScanDrillTier = (int)value;
                    tierLabel.setText(DrillUtility.getDrillTierName((int)value));
                    Config.save();
                }).width(320f);
            }).left().row();
            
            // Info text
            pane.table(infoRow -> {
                infoRow.left();
                infoRow.image(Icon.info).size(20f).padRight(8f).color(Color.lightGray);
                infoRow.add("[lightgray]Press " + Config.autoScanKey + " to scan around player[]")
                    .left().scale(0.85f);
            }).left().padTop(10f).row();
            
        }).grow();
        
        // Buttons
        settingsDialog.addCloseButton();
        settingsDialog.buttons.button(btn -> {
            btn.image(Icon.refresh).size(24f).padRight(6f);
            btn.add("Reset to Defaults");
        }, () -> {
            Config.reset();
            ui.showInfoToast("Settings reset!", 2f);
            settingsDialog.hide();
            arc.util.Time.runTask(5f, () -> settingsDialog.show());
        }).size(200f, 50f);
    }
}