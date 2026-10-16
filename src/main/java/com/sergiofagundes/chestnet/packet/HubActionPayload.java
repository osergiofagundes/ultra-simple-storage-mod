package com.sergiofagundes.chestnet.packet;

import com.sergiofagundes.chestnet.ChestNet;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ByIdMap;

/**
 * Cliente -> servidor: o jogador clicou na grade da Mesa Hub.
 * Só diz QUAL item e O QUE fazer; as quantidades são sempre calculadas no servidor.
 */
public record HubActionPayload(int containerId, ItemVariant variant, Action action) implements CustomPacketPayload {
    public static final Type<HubActionPayload> TYPE = new Type<>(ChestNet.id("hub_action"));

    public enum Action {
        /** Clique esquerdo num item: pega 1 pilha. */
        TAKE_STACK,
        /** Clique direito num item: pega meia pilha. */
        TAKE_HALF,
        /** Shift+clique num item: vai direto para o inventário. */
        TAKE_TO_INVENTORY,
        /** Clique esquerdo segurando algo: guarda tudo que está no cursor. */
        INSERT_CARRIED,
        /** Clique direito segurando algo: guarda só 1. */
        INSERT_ONE;

        private static final IntFunction<Action> BY_ID =
                ByIdMap.continuous(Action::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        static final StreamCodec<ByteBuf, Action> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Action::ordinal);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, HubActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HubActionPayload::containerId,
            ItemVariant.PACKET_CODEC, HubActionPayload::variant,
            Action.STREAM_CODEC, HubActionPayload::action,
            HubActionPayload::new);

    @Override
    public Type<HubActionPayload> type() {
        return TYPE;
    }
}
