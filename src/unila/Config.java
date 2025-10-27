package unila;

import arc.Core;
import arc.input.KeyCode;

/**
 * Configuration manager for AutoDrill settings
 * Handles loading, saving, and default values
 */
public class Config {
    // Keybindings
    public static KeyCode toggleKey = KeyCode.h;
    public static KeyCode autoScanKey = KeyCode.j;
    
    // Core settings
    public static int maxTiles = 1000;
    public static int minOres = 2;
    public static int optimizationQuality = 3;
    
    // Feature flags
    public static boolean placeWaterExtractors = true;
    public static boolean placePowerNodes = true;
    public static boolean usePipeInput = false;
    public static boolean displayToggleButton = true;
    
    // Auto-scan settings
    public static boolean autoScanEnabled = false;
    public static int autoScanRadius = 20;
    public static int autoScanDrillTier = 2;
    
    // Setting keys
    private static final String KEY_TOGGLE = "unila-key";
    private static final String KEY_SCAN = "unila-scankey";
    private static final String KEY_MAX_TILES = "unila-maxtiles";
    private static final String KEY_MIN_ORES = "unila-minores";
    private static final String KEY_WATER = "unila-water";
    private static final String KEY_POWER = "unila-power";
    private static final String KEY_PIPES = "unila-pipes";
    private static final String KEY_BUTTON = "unila-button";
    private static final String KEY_QUALITY = "unila-quality";
    private static final String KEY_AUTOSCAN = "unila-autoscan";
    private static final String KEY_SCAN_RADIUS = "unila-scanradius";
    private static final String KEY_DRILL_TIER = "unila-drilltier";
    
    /**
     * Load settings from Core.settings
     */
    public static void load() {
        if (Core.settings == null) return;
        
        try {
            toggleKey = KeyCode.byName(Core.settings.getString(KEY_TOGGLE, "h"));
        } catch (Exception e) {
            toggleKey = KeyCode.h;
        }
        
        try {
            autoScanKey = KeyCode.byName(Core.settings.getString(KEY_SCAN, "j"));
        } catch (Exception e) {
            autoScanKey = KeyCode.j;
        }
        
        maxTiles = Core.settings.getInt(KEY_MAX_TILES, 1000);
        minOres = Core.settings.getInt(KEY_MIN_ORES, 2);
        placeWaterExtractors = Core.settings.getBool(KEY_WATER, true);
        placePowerNodes = Core.settings.getBool(KEY_POWER, true);
        usePipeInput = Core.settings.getBool(KEY_PIPES, false);
        displayToggleButton = Core.settings.getBool(KEY_BUTTON, true);
        optimizationQuality = Core.settings.getInt(KEY_QUALITY, 3);
        autoScanEnabled = Core.settings.getBool(KEY_AUTOSCAN, false);
        autoScanRadius = Core.settings.getInt(KEY_SCAN_RADIUS, 20);
        autoScanDrillTier = Core.settings.getInt(KEY_DRILL_TIER, 2);
    }
    
    /**
     * Save current settings to Core.settings
     */
    public static void save() {
        if (Core.settings == null) return;
        
        Core.settings.put(KEY_TOGGLE, toggleKey.name());
        Core.settings.put(KEY_SCAN, autoScanKey.name());
        Core.settings.put(KEY_MAX_TILES, maxTiles);
        Core.settings.put(KEY_MIN_ORES, minOres);
        Core.settings.put(KEY_WATER, placeWaterExtractors);
        Core.settings.put(KEY_POWER, placePowerNodes);
        Core.settings.put(KEY_PIPES, usePipeInput);
        Core.settings.put(KEY_BUTTON, displayToggleButton);
        Core.settings.put(KEY_QUALITY, optimizationQuality);
        Core.settings.put(KEY_AUTOSCAN, autoScanEnabled);
        Core.settings.put(KEY_SCAN_RADIUS, autoScanRadius);
        Core.settings.put(KEY_DRILL_TIER, autoScanDrillTier);
    }
    
    /**
     * Reset all settings to default values
     */
    public static void reset() {
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
        save();
    }
}