package com.sergiofagundes.chestnet.network;

import com.sergiofagundes.chestnet.block.CableBlock;
import com.sergiofagundes.chestnet.block.ConnectorBlock;
import com.sergiofagundes.chestnet.block.HubBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Uma "foto" da rede: quais baús estão ligados à Mesa Hub naquele momento.
 *
 * Usa a Transfer API do Fabric (ItemStorage.SIDED), então funciona com baús,
 * barris, shulker boxes e inventários de outros mods — não só com o baú vanilla.
 * Todas as operações usam transações: ou dá tudo certo, ou nada muda.
 */
public final class StorageNetwork {
    /** Limite de blocos percorridos (evita travar o servidor com redes gigantes). */
    public static final int MAX_NODES = 4096;

    public record Node(BlockPos pos, Storage<ItemVariant> storage) {}

    public record Entry(ItemVariant variant, long amount) {
        public int maxStackSize() {
            return variant.toStack().getMaxStackSize();
        }
    }

    private final List<Node> nodes;

    private StorageNetwork(List<Node> nodes) {
        this.nodes = nodes;
    }

    public static boolean isNetworkBlock(BlockState state) {
        return state.getBlock() instanceof CableBlock
                || state.getBlock() instanceof ConnectorBlock
                || state.getBlock() instanceof HubBlock;
    }

    /**
     * Percorre a rede a partir da Mesa Hub (busca em largura) e coleta os
     * inventários para onde os conectores estão apontando.
     */
    public static StorageNetwork scan(Level level, BlockPos hubPos) {
        List<Node> nodes = new ArrayList<>();
        Set<BlockPos> storageKeys = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        visited.add(hubPos);
        queue.add(hubPos);

        while (!queue.isEmpty() && visited.size() <= MAX_NODES) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);

            // Conector: olha o bloco para onde ele aponta
            if (state.getBlock() instanceof ConnectorBlock) {
                Direction facing = state.getValue(ConnectorBlock.FACING);
                BlockPos target = pos.relative(facing);
                if (level.isLoaded(target)) {
                    BlockState targetState = level.getBlockState(target);
                    if (!isNetworkBlock(targetState) && storageKeys.add(storageKey(target, targetState))) {
                        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, target, facing.getOpposite());
                        if (storage != null) {
                            nodes.add(new Node(target.immutable(), storage));
                        }
                    }
                }
            }

            // Continua andando por cabos / conectores / mesas vizinhos
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (!visited.contains(next) && level.isLoaded(next) && isNetworkBlock(level.getBlockState(next))) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return new StorageNetwork(nodes);
    }

    /**
     * Baú duplo = duas posições, mas o mesmo inventário. Usamos sempre a
     * "menor" das duas posições como chave para não contar os itens em dobro.
     */
    private static BlockPos storageKey(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            return pos.compareTo(other) <= 0 ? pos.immutable() : other.immutable();
        }
        return pos.immutable();
    }

    public int storageCount() {
        return nodes.size();
    }

    /** Soma todos os itens de todos os baús, do que tem mais para o que tem menos. */
    public List<Entry> sortedEntries() {
        Map<ItemVariant, Long> totals = new LinkedHashMap<>();
        for (Node node : nodes) {
            for (StorageView<ItemVariant> view : node.storage().nonEmptyViews()) {
                if (!view.isResourceBlank() && view.getAmount() > 0) {
                    totals.merge(view.getResource(), view.getAmount(), Long::sum);
                }
            }
        }
        List<Entry> entries = new ArrayList<>(totals.size());
        totals.forEach((variant, amount) -> entries.add(new Entry(variant, amount)));
        entries.sort(ENTRY_ORDER);
        return entries;
    }

    private static final Comparator<Entry> ENTRY_ORDER = Comparator
            .comparingLong(Entry::amount).reversed()
            .thenComparing(e -> BuiltInRegistries.ITEM.getKey(e.variant().getItem()).toString());

    /** Tira até {@code amount} unidades do item da rede. Retorna quanto conseguiu tirar. */
    public int extract(ItemVariant variant, int amount) {
        if (variant.isBlank() || amount <= 0) return 0;
        long extracted = 0;
        try (Transaction tx = Transaction.openOuter()) {
            for (Node node : nodes) {
                extracted += node.storage().extract(variant, amount - extracted, tx);
                if (extracted >= amount) break;
            }
            tx.commit();
        }
        return (int) extracted;
    }

    /**
     * Guarda a pilha na rede. Primeiro tenta os baús que JÁ têm esse item
     * (mantém tudo agrupado), depois qualquer espaço livre.
     *
     * @return o que sobrou (vazio se coube tudo)
     */
    public ItemStack insert(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemVariant variant = ItemVariant.of(stack);
        long amount = stack.getCount();
        long inserted = 0;
        try (Transaction tx = Transaction.openOuter()) {
            for (Node node : nodes) {
                if (inserted >= amount) break;
                if (contains(node.storage(), variant)) {
                    inserted += node.storage().insert(variant, amount - inserted, tx);
                }
            }
            for (Node node : nodes) {
                if (inserted >= amount) break;
                inserted += node.storage().insert(variant, amount - inserted, tx);
            }
            tx.commit();
        }
        int remaining = (int) (amount - inserted);
        return remaining <= 0 ? ItemStack.EMPTY : stack.copyWithCount(remaining);
    }

    private static boolean contains(Storage<ItemVariant> storage, ItemVariant variant) {
        for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
            if (view.getResource().equals(variant)) return true;
        }
        return false;
    }
}
