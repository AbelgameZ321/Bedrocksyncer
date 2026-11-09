package dev.bedrocksync.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import dev.bedrocksync.BedrockSyncClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stores only non-secret settings. Microsoft tokens are NEVER written here or
 * anywhere else on disk; they live in memory for the duration of the sign-in.
 *
 * File: config/bedrocksync.json
 */
public final class BedrockSyncConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Azure/Entra application (client) ID that YOU register for BedrockSync.
     * See README.md for the registration steps. Empty means sign-in is disabled.
     */
    @SerializedName("client_id")
    private String clientId = "";

    public String getClientId() {
        return clientId == null ? "" : clientId.trim();
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("bedrocksync.json");
    }

    public static BedrockSyncConfig load() {
        Path path = path();
        try {
            if (Files.isRegularFile(path)) {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                BedrockSyncConfig config = GSON.fromJson(json, BedrockSyncConfig.class);
                if (config != null) {
                    return config;
                }
            } else {
                BedrockSyncConfig fresh = new BedrockSyncConfig();
                fresh.save();
                BedrockSyncClient.LOGGER.info("Created default config at {}", path);
                return fresh;
            }
        } catch (IOException | RuntimeException e) {
            BedrockSyncClient.LOGGER.warn("Could not read {}: {}. Using defaults.", path, e.getMessage());
        }
        return new BedrockSyncConfig();
    }

    public void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            BedrockSyncClient.LOGGER.warn("Could not write {}: {}", path, e.getMessage());
        }
    }
}
