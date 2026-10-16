package com.sergiofagundes.chestnet.menu;

import com.sergiofagundes.chestnet.ModRegistry;
import com.sergiofagundes.chestnet.block.HubBlock;
import com.sergiofagundes.chestnet.network.StorageNetwork;
import com.sergiofagundes.chestnet.packet.HubActionPayload;
import com.sergiofagundes.chestnet.packet.HubContentsPayload;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Menu da Mesa Hub.
 *
 * Os únicos slots "de verdade" são os do inventário do jogador. A grade com os
 * itens da rede é desenhada pela tela (HubScreen) a partir da lista que o
 * servidor manda (HubContentsPayload). Os cliques na grade voltam como
 * HubActionPayload e são resolvidos aqui, no SERVIDOR.
 */
public class HubMenu extends AbstractContainerMenu {
    // Layout da tela (em pixels, a partir do canto da GUI). O HubScreen usa os mesmos valores.
    public static final int COLUMNS = 9;
    public static final int VISIBLE_ROWS = 6;
    public static final int GRID_X = 8;
    public static final int GRID_Y = 20;
    public static final int INVENTORY_Y = 140;

    private static final int REFRESH_INTERVAL = 20; // ticks (1 segundo)

    private final Player owner;
    private final @Nullable Level level; // null no cliente
    private final BlockPos hubPos;

    // ---- Só no servidor ----
    private StorageNetwork network;
    private List<StorageNetwork.Entry> entries = List.of();
    private @Nullable List<StorageNetwork.Entry> lastSent; // null = ainda não mandou nada ao cliente
    private int ticksUntilRefresh = REFRESH_INTERVAL;

    // ---- Só no cliente ----
    private List<StorageNetwork.Entry> clientEntries = List.of();
    private int clientRevision = 0;

    /** Construtor usado pelo CLIENTE (o Minecraft chama via MenuType). */
    public HubMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null, BlockPos.ZERO);
    }

    /** Construtor usado pelo SERVIDOR (quando o jogador clica na mesa). */
    public HubMenu(int containerId, Inventory playerInventory, @Nullable Level level, BlockPos hubPos) {
        super(ModRegistry.HUB_MENU, containerId);
        this.owner = playerInventory.player;
        this.level = level;
        this.hubPos = hubPos;

        // Inventário do jogador (3 linhas) + hotbar
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
    }

    private boolean isServer() {
        return level != null && !level.isClientSide();
    }

    // ------------------------------------------------------------------
    // Cliques
    // ------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        // Shift+clique em um item do SEU inventário -> manda para a rede
        if (input == ContainerInput.QUICK_MOVE && slotId >= 0 && slotId < slots.size()) {
            if (isServer()) {
                rescan();
                Slot slot = slots.get(slotId);
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    slot.set(network.insert(stack.copy()));
                }
                rescan();
                sendContentsIfChanged();
            }
            return;
        }

        // Qualquer outra coisa no inventário do jogador funciona normalmente
        super.clicked(slotId, button, input, player);
    }

    /** Clique na grade da rede (chega pelo HubActionPayload). Roda no servidor. */
    public void handleAction(ServerPlayer player, ItemVariant variant, HubActionPayload.Action action) {
        rescan(); // sempre usa a rede atualizada antes de mexer em itens

        ItemStack carried = getCarried();
        switch (action) {
            case INSERT_CARRIED -> {
                if (!carried.isEmpty()) setCarried(network.insert(carried));
            }
            case INSERT_ONE -> {
                if (!carried.isEmpty()) {
                    ItemStack rest = network.insert(carried.copyWithCount(1));
                    if (rest.isEmpty()) {
                        carried.shrink(1);
                        setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                    }
                }
            }
            case TAKE_STACK, TAKE_HALF, TAKE_TO_INVENTORY -> {
                if (carried.isEmpty()) take(player, variant, action);
            }
        }

        rescan();
        sendContentsIfChanged();
        super.broadcastChanges(); // manda já o cursor e o inventário atualizados
    }

    private void take(ServerPlayer player, ItemVariant variant, HubActionPayload.Action action) {
        if (variant.isBlank()) return;
        StorageNetwork.Entry entry = entries.stream()
                .filter(e -> e.variant().equals(variant))
                .findFirst()
                .orElse(null);
        if (entry == null) return;

        int want = (int) Math.min(entry.maxStackSize(), entry.amount());
        if (action == HubActionPayload.Action.TAKE_HALF) {
            want = (want + 1) / 2; // botão direito: metade
        }
        int got = network.extract(variant, want);
        if (got <= 0) return;

        ItemStack taken = variant.toStack(got);
        if (action == HubActionPayload.Action.TAKE_TO_INVENTORY) {
            // Shift+clique: vai direto para o inventário do jogador
            player.getInventory().add(taken); // 'taken' fica só com o que não coube
            if (!taken.isEmpty()) {
                ItemStack back = network.insert(taken);
                if (!back.isEmpty()) player.getInventory().placeItemBackInInventory(back, Prediction.SERVER_ONLY);
            }
        } else {
            setCarried(taken);
        }
    }

    // ------------------------------------------------------------------
    // Sincronização com o cliente
    // ------------------------------------------------------------------

    private void rescan() {
        if (level == null) return;
        network = StorageNetwork.scan(level, hubPos);
        entries = network.sortedEntries();
    }

    /** Manda a lista para o cliente, mas só se mudou desde a última vez. */
    private void sendContentsIfChanged() {
        if (owner instanceof ServerPlayer serverPlayer && !entries.equals(lastSent)) {
            lastSent = entries;
            ServerPlayNetworking.send(serverPlayer, new HubContentsPayload(containerId, entries));
        }
    }

    /** Recebido do servidor (HubContentsPayload). Roda no cliente. */
    public void setClientEntries(List<StorageNetwork.Entry> entries) {
        this.clientEntries = entries;
        this.clientRevision++;
    }

    public List<StorageNetwork.Entry> getClientEntries() {
        return clientEntries;
    }

    /** Muda toda vez que chega uma lista nova (a tela usa para saber quando refiltrar). */
    public int getClientRevision() {
        return clientRevision;
    }

    // ------------------------------------------------------------------
    // Ciclo de vida do menu
    // ------------------------------------------------------------------

    /** Chamado todo tick no servidor: logo ao abrir e depois a cada 1 s relê os baús. */
    @Override
    public void broadcastChanges() {
        if (isServer() && (lastSent == null || --ticksUntilRefresh <= 0)) {
            ticksUntilRefresh = REFRESH_INTERVAL;
            rescan();
            sendContentsIfChanged();
        }
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY; // tratado em clicked()
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null) return true;
        return level.getBlockState(hubPos).getBlock() instanceof HubBlock
                && player.distanceToSqr(hubPos.getX() + 0.5, hubPos.getY() + 0.5, hubPos.getZ() + 0.5) <= 64.0;
    }
}
