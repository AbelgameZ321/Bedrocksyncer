package dev.bedrocksync;

import dev.bedrocksync.auth.BedrockSession;
import dev.bedrocksync.auth.MicrosoftAuthService;
import dev.bedrocksync.config.BedrockSyncConfig;
import dev.bedrocksync.gui.BedrockSyncScreen;
import dev.bedrocksync.skin.LocalSkinManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint. Purely client-side: no server components, no packets.
 */
public final class BedrockSyncClient implements ClientModInitializer {
    public static final String MOD_ID = "bedrocksync";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static BedrockSyncClient instance;

    private BedrockSyncConfig config;
    private MicrosoftAuthService auth;
    private BedrockSession session;
    private LocalSkinManager skins;

    public static BedrockSyncClient get() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        config = BedrockSyncConfig.load();
        auth = new MicrosoftAuthService(config.getClientId());
        session = new BedrockSession();
        skins = new LocalSkinManager();

        // Add a "BedrockSync" button to the title screen (top-right corner).
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                Button button = Button.builder(
                                Component.literal("BedrockSync"),
                                b -> client.setScreen(new BedrockSyncScreen(screen)))
                        .bounds(scaledWidth - 104, 4, 100, 20)
                        .build();
                Screens.getButtons(screen).add(button);
            }
        });

        LOGGER.info("BedrockSync initialized (client-side only).");
    }

    public BedrockSyncConfig config() {
        return config;
    }

    public MicrosoftAuthService auth() {
        return auth;
    }

    public BedrockSession session() {
        return session;
    }

    public LocalSkinManager skins() {
        return skins;
    }
}
