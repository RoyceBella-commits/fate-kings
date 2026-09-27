package cn.blockforge.fatekings.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server config {@code config/fatekings-server.json}: {@code {"terrainDestruction": true}}. */
public final class FateConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("FateKings/Config");
    private static volatile boolean terrainDestruction = true;
    private static Path path;

    private FateConfig() {
    }

    public static boolean terrainDestruction() {
        return terrainDestruction;
    }

    public static void load() {
        path = FabricLoader.getInstance().getConfigDir().resolve("fatekings-server.json");
        load(path);
    }

    /** Separate for the headless checks. */
    public static void load(Path file) {
        path = file;
        if (!Files.exists(file)) {
            save();
            return;
        }
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            var v = o.get("terrainDestruction");
            if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) terrainDestruction = v.getAsBoolean();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Cannot read {}; terrain destruction stays {}", file, terrainDestruction, e);
        }
    }

    public static void setTerrainDestruction(boolean on) {
        terrainDestruction = on;
        save();
    }

    private static void save() {
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("terrainDestruction", terrainDestruction);
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, o + "\n", StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Cannot save {}", path, e);
        }
    }
}
