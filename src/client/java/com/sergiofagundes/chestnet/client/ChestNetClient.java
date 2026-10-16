package com.sergiofagundes.chestnet.client;

import com.sergiofagundes.chestnet.ModRegistry;
import com.sergiofagundes.chestnet.menu.HubMenu;
import com.sergiofagundes.chestnet.packet.HubContentsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

/** Código que só roda no cliente (fica em src/client, nunca carrega em servidor dedicado). */
public class ChestNetClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Diz ao jogo qual tela abrir para o menu da Mesa Hub
        MenuScreens.register(ModRegistry.HUB_MENU, HubScreen::new);

        // Lista de itens da rede enviada pelo servidor -> guarda no menu aberto (a tela lê de lá)
        ClientPlayNetworking.registerGlobalReceiver(HubContentsPayload.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof HubMenu menu && menu.containerId == payload.containerId()) {
                menu.setClientEntries(payload.entries());
            }
        });
    }
}
