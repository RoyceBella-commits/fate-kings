package cn.blockforge.fatekings.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client options ({@code config/fatekings-client.json}): HUD panel, fewer effects, less camera shake,
 * no flashes. All three comfort options are wired to the effects (Ea's rift, the sky, flashes, shake).
 */
public final class ClientPrefs {
    private static final Logger LOGGER = LoggerFactory.getLogger("FateKings/ClientPrefs");
    private final Path path;
    private JsonObject values = new JsonObject();
    private boolean hudVisible = true;
    private boolean lowFx;
    private boolean reduceShake;
    private boolean noFlash;

    public ClientPrefs(Path path) {
        this.path = path;
        if (!Files.exists(path)) {
            save();
            return;
        }
        try {
            this.values = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            this.hudVisible = flag("hudVisible", true);
            this.lowFx = flag("lowFx", false);
            this.reduceShake = flag("reduceShake", false);
            this.noFlash = flag("noFlash", false);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Cannot read client options {}; using defaults", path, e);
            this.values = new JsonObject();
        }
    }

    private boolean flag(String key, boolean def) {
        var v = this.values.get(key);
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean() ? v.getAsBoolean() : def;
    }

    public boolean hudVisible() {
        return this.hudVisible;
    }

    public boolean lowFx() {
        return this.lowFx;
    }

    public boolean reduceShake() {
        return this.reduceShake;
    }

    public boolean noFlash() {
        return this.noFlash;
    }

    public void set(String key, boolean value) {
        switch (key) {
            case "hudVisible" -> this.hudVisible = value;
            case "lowFx" -> this.lowFx = value;
            case "reduceShake" -> this.reduceShake = value;
            case "noFlash" -> this.noFlash = value;
            default -> throw new IllegalArgumentException(key);
        }
        save();
    }

    private void save() {
        try {
            Files.createDirectories(this.path.getParent());
            this.values.addProperty("hudVisible", this.hudVisible);
            this.values.addProperty("lowFx", this.lowFx);
            this.values.addProperty("reduceShake", this.reduceShake);
            this.values.addProperty("noFlash", this.noFlash);
            Path tmp = this.path.resolveSibling(this.path.getFileName() + ".tmp");
            Files.writeString(tmp, this.values + "\n", StandardCharsets.UTF_8);
            Files.move(tmp, this.path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Cannot save client options {}", this.path, e);
        }
    }
}
