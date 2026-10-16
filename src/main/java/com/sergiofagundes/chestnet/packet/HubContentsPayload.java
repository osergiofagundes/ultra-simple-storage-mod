package com.sergiofagundes.chestnet.packet;

import com.sergiofagundes.chestnet.ChestNet;
import com.sergiofagundes.chestnet.network.StorageNetwork;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor -> cliente: a lista completa de itens da rede (item + total real).
 * A tela usa essa lista para desenhar a grade, filtrar pela busca e rolar.
 */
public record HubContentsPayload(int containerId, List<StorageNetwork.Entry> entries) implements CustomPacketPayload {
    public static final Type<HubContentsPayload> TYPE = new Type<>(ChestNet.id("hub_contents"));

    private static final StreamCodec<RegistryFriendlyByteBuf, StorageNetwork.Entry> ENTRY_CODEC = StreamCodec.composite(
            ItemVariant.PACKET_CODEC, StorageNetwork.Entry::variant,
            ByteBufCodecs.VAR_LONG, StorageNetwork.Entry::amount,
            StorageNetwork.Entry::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, HubContentsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HubContentsPayload::containerId,
            ENTRY_CODEC.apply(ByteBufCodecs.list()), HubContentsPayload::entries,
            HubContentsPayload::new);

    @Override
    public Type<HubContentsPayload> type() {
        return TYPE;
    }
}
