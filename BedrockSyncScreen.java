package dev.bedrocksync.gui;

import dev.bedrocksync.BedrockSyncClient;
import dev.bedrocksync.auth.BedrockSession;
import dev.bedrocksync.skin.LocalSkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Account management screen. Controls: sign in, sign out, re-check local skin.
 */
public final class BedrockSyncScreen extends Screen {

    private final Screen parent;
    private Button signInButton;
    private LocalSkinManager.SkinInfo skinInfo;

    public BedrockSyncScreen(Screen parent) {
        super(Component.literal("BedrockSync Account"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 40;

        signInButton = addRenderableWidget(Button.builder(
                        Component.literal("Sign in with Microsoft"),
                        b -> BedrockSyncClient.get().auth().signIn(BedrockSyncClient.get().session()))
                .bounds(cx - 100, y, 200, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.literal("Sign out"),
                        b -> BedrockSyncClient.get().session().signOut())
                .bounds(cx - 100, y + 26, 200, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.literal("Re-check local skin"),
                        b -> skinInfo = BedrockSyncClient.get().skins().inspect())
                .bounds(cx - 100, y + 52, 200, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.literal("Done"),
                        b -> onClose())
                .bounds(cx - 100, this.height - 30, 200, 20)
                .build());

        if (skinInfo == null) {
            skinInfo = BedrockSyncClient.get().skins().inspect();
        }
    }

    @Override
    public void tick() {
        BedrockSession session = BedrockSyncClient.get().session();
        signInButton.active = session.state() != BedrockSession.State.PENDING;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        Font font = Minecraft.getInstance().font;
        BedrockSession session = BedrockSyncClient.get().session();
        int x = this.width / 2 - 100;
        int y = 84;

        graphics.drawCenteredString(font, this.title, this.width / 2, 12, 0xFFFFFF);

        graphics.drawString(font, "Status: " + session.message(), x, y, 0xFFFFFF);
        y += 14;

        if (session.state() == BedrockSession.State.PENDING && !session.userCode().isEmpty()) {
            graphics.drawString(font, "Go to: " + session.verificationUri(), x, y, 0xFFFF55);
            y += 12;
            graphics.drawString(font, "Code: " + session.userCode(), x, y, 0xFFFF55);
            y += 14;
        }

        if (session.state() == BedrockSession.State.SIGNED_IN) {
            graphics.drawString(font, "Gamertag: " + session.gamertag(), x, y, 0x55FF55);
            y += 12;
            graphics.drawString(font, "Xbox ID: " + session.xuid(), x, y, 0x55FF55);
            y += 14;
        }

        if (skinInfo != null) {
            graphics.drawString(font, "Skin: " + skinInfo.message(), x, y, 0xAAAAAA);
            y += 14;
        }

        graphics.drawString(font, "Java servers need a Java Edition profile.", x, y, 0xFF5555);
        y += 12;
        graphics.drawString(font, "A Bedrock-only account cannot join online-mode Java servers.", x, y, 0xFF5555);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
