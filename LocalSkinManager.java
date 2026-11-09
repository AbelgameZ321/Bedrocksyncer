package dev.bedrocksync.skin;

import dev.bedrocksync.BedrockSyncClient;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local skin inspection only. Reads config/bedrocksync/skin.png and validates
 * its dimensions. It does NOT upload anything to Microsoft or Mojang, and it
 * does NOT pull skins from Bedrock/Xbox services.
 */
public final class LocalSkinManager {

    public record SkinInfo(boolean found, int width, int height, String message) {}

    public static Path skinPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("bedrocksync").resolve("skin.png");
    }

    public SkinInfo inspect() {
        Path path = skinPath();
        if (!Files.isRegularFile(path)) {
            return new SkinInfo(false, 0, 0, "No skin file. Place a PNG at " + path);
        }
        try (InputStream in = Files.newInputStream(path)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                return new SkinInfo(true, 0, 0, "File is not a readable PNG.");
            }
            int w = image.getWidth();
            int h = image.getHeight();
            boolean valid = w == 64 && (h == 64 || h == 32);
            String msg = valid
                    ? "Skin file OK (" + w + "x" + h + ")."
                    : "Unexpected size " + w + "x" + h + ". Expected 64x64 or 64x32.";
            return new SkinInfo(true, w, h, msg);
        } catch (IOException e) {
            BedrockSyncClient.LOGGER.warn("Could not read skin file: {}", e.toString());
            return new SkinInfo(true, 0, 0, "Could not read the skin file.");
        } catch (LinkageError e) {
            // java.desktop (ImageIO) may be absent on some non-desktop JVMs.
            BedrockSyncClient.LOGGER.warn("ImageIO unavailable on this runtime: {}", e.toString());
            return new SkinInfo(true, 0, 0, "Image decoding is unavailable on this Java runtime.");
        }
    }
}
