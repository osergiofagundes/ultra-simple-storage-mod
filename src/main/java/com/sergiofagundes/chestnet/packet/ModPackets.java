package com.sergiofagundes.chestnet.packet;

import com.sergiofagundes.chestnet.menu.HubMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Registra os pacotes do mod (dos dois lados) e o que o servidor faz ao receber um clique. */
public final class ModPackets {
    /** Redes com milhares de tipos de item podem passar do limite padrão de 1 MiB por pacote. */
    private static final int MAX_CONTENTS_SIZE = 8 * 1024 * 1024;

    private ModPackets() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().registerLarge(HubContentsPayload.TYPE, HubContentsPayload.CODEC, MAX_CONTENTS_SIZE);
        PayloadTypeRegistry.serverboundPlay().register(HubActionPayload.TYPE, HubActionPayload.CODEC);

        // O Fabric já chama isto na thread do servidor
        ServerPlayNetworking.registerGlobalReceiver(HubActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player.containerMenu instanceof HubMenu menu
                    && menu.containerId == payload.containerId()
                    && menu.stillValid(player)) {
                menu.handleAction(player, payload.variant(), payload.action());
            }
        });
    }
}
