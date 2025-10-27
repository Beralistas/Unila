package unila;

import arc.Core;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.Mod;
import static mindustry.Vars.*;

/**
 * Main mod class for Unila AutoDrill V8
 * Handles initialization and command registration
 */
public class UnilaMod extends Mod {
    private AutoDrillManager manager;
    private AutoDrillUI ui;
    
    public UnilaMod() {
        Log.info("Unila AutoDrill V8 loaded!");
        
        Events.on(ClientLoadEvent.class, e -> {
            Time.runTask(10f, this::initialize);
        });
        
        Events.on(WorldLoadEvent.class, e -> {
            if (manager != null) {
                manager.disable();
                manager.clearCaches();
            }
        });
    }
    
    private void initialize() {
        Config.load();
        manager = new AutoDrillManager();
        ui = new AutoDrillUI(manager);
        ui.build();
    }
    
    @Override
    public void init() {
        Events.run(Trigger.update, () -> {
            if (manager != null) {
                manager.update();
            }
        });
    }
    
    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("autodrill", "[on/off]", "Toggle AutoDrill", (args, player) -> {
            if (manager == null) return;
            
            if (args.length == 0) {
                manager.toggle();
            } else {
                boolean enable = args[0].equalsIgnoreCase("on");
                if (enable) manager.enable();
                else manager.disable();
                mindustry.Vars.ui.showInfoToast("AutoDrill " + (enable ? "[green]ON[]" : "[red]OFF[]"), 2f);
            }
        });
        
        handler.<Player>register("autodrill-settings", "Open settings dialog", (args, player) -> {
            if (ui != null) ui.showSettings();
        });
        
        handler.<Player>register("autodrill-scan", "Perform auto-scan around player", (args, player) -> {
            if (manager != null) manager.performAutoScan();
        });
    }
}