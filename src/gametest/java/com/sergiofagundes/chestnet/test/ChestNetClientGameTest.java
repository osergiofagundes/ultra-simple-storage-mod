package com.sergiofagundes.chestnet.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.sergiofagundes.chestnet.ChestNet;
import com.sergiofagundes.chestnet.ModRegistry;
import com.sergiofagundes.chestnet.block.ConnectorBlock;
import com.sergiofagundes.chestnet.block.HubBlock;
import com.sergiofagundes.chestnet.client.HubScreen;
import com.sergiofagundes.chestnet.menu.HubMenu;
import com.sergiofagundes.chestnet.network.StorageNetwork;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Teste automático: abre o jogo, cria um mundo e confere, simulando o jogador:
 * as receitas na mesa de trabalho, o desbloqueio no livro de receitas, a busca
 * e a ordenação da Mesa Hub. Também tira screenshots da tela e dos blocos.
 *
 * Rodar com: gradlew runClientGameTest
 * Screenshots em: build/run/clientGameTest/screenshots
 */
public class ChestNetClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("chestnet-test");

    // No Minecraft 26.x os botões do mouse são 1 = esquerdo e 3 = direito (a API de teste usa essa numeração)
    private static final int MOUSE_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    private static final int MOUSE_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    private static final int KEY_ESCAPE = InputConstants.KEY_ESCAPE; // 41 no 26.x (não mais 256)

    // Medidas da tela da Mesa Hub (iguais às de HubScreen)
    private static final int GUI_WIDTH = 195;
    private static final int GUI_HEIGHT = 222;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitTicks(40);
            TestServerContext server = world.getServer();
            BlockPos base = server.computeOnServer(s -> firstPlayer(s.getPlayerList().getPlayers()).blockPosition());

            checkRecipeBook(context, server);
            checkCraftingTable(server, base);
            checkHubScreen(context, server, base);
            takeBlockScreenshots(context, server, base);
            LOG.info("[teste] TUDO OK");
        }
    }

    private static ServerPlayer firstPlayer(List<ServerPlayer> players) {
        return players.getFirst();
    }

    // ------------------------------------------------------------------
    // Receitas
    // ------------------------------------------------------------------

    /** Pegar redstone libera o cabo e a Mesa no livro; pegar cabo libera o conector. */
    private static void checkRecipeBook(ClientGameTestContext context, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer player = firstPlayer(s.getPlayerList().getPlayers());
            player.getInventory().add(new ItemStack(Items.REDSTONE));
            player.getInventory().add(new ItemStack(ModRegistry.NETWORK_CABLE_ITEM));
        });
        context.waitTicks(10);
        server.runOnServer(s -> {
            ServerPlayer player = firstPlayer(s.getPlayerList().getPlayers());
            for (String id : List.of("network_cable", "chest_connector", "storage_hub")) {
                boolean unlocked = player.getRecipeBook().contains(ResourceKey.create(Registries.RECIPE, ChestNet.id(id)));
                LOG.info("[teste] Livro de receitas: {} liberada = {}", id, unlocked);
                check(unlocked, "Receita " + id + " não foi liberada no livro de receitas");
            }
            player.getInventory().clearContent();
        });
    }

    /** Monta cada receita na grade de uma mesa de trabalho de verdade e confere o resultado. */
    private static void checkCraftingTable(TestServerContext server, BlockPos base) {
        server.runOnServer(s -> {
            ServerPlayer player = firstPlayer(s.getPlayerList().getPlayers());
            ServerLevel level = player.level();
            BlockPos tablePos = base.west(2);
            level.setBlock(tablePos, Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
            player.openMenu(level.getBlockState(tablePos).getMenuProvider(level, tablePos));
            check(player.containerMenu instanceof CraftingMenu, "A mesa de trabalho não abriu");
            CraftingMenu menu = (CraftingMenu) player.containerMenu;

            Item iron = Items.IRON_INGOT, redstone = Items.REDSTONE, stone = Items.SMOOTH_STONE;
            Item chest = Items.CHEST, cable = ModRegistry.NETWORK_CABLE_ITEM, none = Items.AIR;

            craft(menu, "cabo", new Item[] {iron, redstone, iron, stone, stone, stone, none, none, none},
                    ModRegistry.NETWORK_CABLE_ITEM, 16);
            // a linha de cima vazia (como na imagem) também tem que funcionar
            craft(menu, "cabo (linhas de baixo)", new Item[] {none, none, none, iron, redstone, iron, stone, stone, stone},
                    ModRegistry.NETWORK_CABLE_ITEM, 16);
            craft(menu, "conector", new Item[] {cable, cable, cable, cable, redstone, cable, cable, cable, cable},
                    ModRegistry.CHEST_CONNECTOR_ITEM, 4);
            craft(menu, "mesa", new Item[] {stone, iron, stone, redstone, chest, redstone, stone, iron, stone},
                    ModRegistry.STORAGE_HUB_ITEM, 1);

            for (int i = 1; i <= 9; i++) menu.getSlot(i).set(ItemStack.EMPTY);
            player.closeContainer();
            level.removeBlock(tablePos, false);
        });
    }

    private static void craft(CraftingMenu menu, String name, Item[] grid, Item expected, int expectedCount) {
        for (int i = 0; i < 9; i++) {
            menu.getSlot(1 + i).set(grid[i] == Items.AIR ? ItemStack.EMPTY : new ItemStack(grid[i]));
        }
        ItemStack result = menu.getResultSlot().getItem();
        LOG.info("[teste] Mesa de trabalho, {} -> {} x{}", name, BuiltInRegistries.ITEM.getKey(result.getItem()), result.getCount());
        check(result.getItem() == expected && result.getCount() == expectedCount,
                "Mesa de trabalho: " + name + " deu " + result + ", esperado " + expectedCount + "x " + expected);
    }

    // ------------------------------------------------------------------
    // Tela da Mesa Hub: busca e ordenação
    // ------------------------------------------------------------------

    private static void checkHubScreen(ClientGameTestContext context, TestServerContext server, BlockPos base) {
        server.runOnServer(s -> {
            ServerPlayer player = firstPlayer(s.getPlayerList().getPlayers());
            ServerLevel level = player.level();
            BlockPos hubPos = base.east(2);
            BlockPos connectorPos = base.east(3);
            BlockPos chestPos = base.east(4);
            place(level, hubPos, ModRegistry.STORAGE_HUB.defaultBlockState());
            place(level, chestPos, Blocks.CHEST.defaultBlockState());
            place(level, connectorPos, ModRegistry.CHEST_CONNECTOR.defaultBlockState().setValue(ConnectorBlock.FACING, Direction.EAST));
            if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chestEntity) {
                chestEntity.setItem(0, new ItemStack(Items.STONE, 64));
                chestEntity.setItem(1, new ItemStack(Items.DIRT, 32));
                chestEntity.setItem(2, new ItemStack(Items.OAK_PLANKS, 10));
                chestEntity.setItem(3, new ItemStack(Items.COBBLESTONE, 5));
                chestEntity.setItem(4, new ItemStack(Items.REDSTONE, 3));
            }
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new HubMenu(id, inventory, level, hubPos), Component.literal("Hub")));
        });

        context.waitForScreen(HubScreen.class);
        context.runOnClient(client -> client.gui.toastManager().clear()); // tira o aviso "receita desbloqueada" das fotos
        context.waitTicks(30); // espera a lista de itens chegar do servidor
        context.takeScreenshot("chestnet_hub_aberta");

        // Ordem padrão: maior quantidade primeiro
        checkOrder(context, "padrão (maior primeiro)", List.of("stone", "dirt", "oak_planks", "cobblestone", "redstone"));

        // Busca: clica no campo, digita, confere o texto e o filtro
        click(context, 60, 10, MOUSE_LEFT);
        context.getInput().typeChars("dirt");
        context.waitTicks(2);
        String typed = searchText(context);
        List<String> found = visibleItems(context);
        LOG.info("[teste] Busca: campo='{}' itens={}", typed, found);
        context.takeScreenshot("chestnet_hub_busca");
        check("dirt".equals(typed), "A busca não recebeu o texto digitado (campo='" + typed + "')");
        check(found.equals(List.of("dirt")), "A busca por 'dirt' devia mostrar só terra, mostrou " + found);

        // Botão direito no campo limpa a busca
        click(context, 60, 10, MOUSE_RIGHT);
        check(searchText(context).isEmpty(), "Botão direito não limpou a busca");

        // Ordenação pelos 3 botões: [A-Z] x=131..150, [↓] x=152..168, [↑] x=170..186, y=4..16
        click(context, 140, 10, MOUSE_LEFT);
        checkOrder(context, "A-Z", List.of("cobblestone", "dirt", "oak_planks", "redstone", "stone"));
        click(context, 178, 10, MOUSE_LEFT);
        checkOrder(context, "menor primeiro", List.of("redstone", "cobblestone", "oak_planks", "dirt", "stone"));
        context.takeScreenshot("chestnet_hub_ordem_menor");
        click(context, 160, 10, MOUSE_LEFT);
        checkOrder(context, "maior primeiro", List.of("stone", "dirt", "oak_planks", "cobblestone", "redstone"));

        context.getInput().pressKey(KEY_ESCAPE);
        context.waitTicks(5);
        check(context.computeOnClient(client -> client.gui.screen() == null), "ESC não fechou a tela da Mesa Hub");
    }

    /** Clica num ponto da tela da Mesa Hub (coordenadas relativas ao canto da GUI). */
    private static void click(ClientGameTestContext context, int guiX, int guiY, int button) {
        double[] pixel = context.computeOnClient(client -> {
            Screen screen = client.gui.screen();
            int scale = client.getWindow().getGuiScale();
            int left = (screen.width - GUI_WIDTH) / 2;
            int top = (screen.height - GUI_HEIGHT) / 2;
            return new double[] {(left + guiX) * scale + scale / 2.0, (top + guiY) * scale + scale / 2.0};
        });
        context.getInput().setCursorPos(pixel[0], pixel[1]);
        context.getInput().pressMouse(button);
        context.waitTicks(2);
    }

    private static String searchText(ClientGameTestContext context) {
        return context.computeOnClient(client -> client.gui.screen().children().stream()
                .filter(c -> c instanceof EditBox).map(c -> ((EditBox) c).getValue()).findFirst().orElse("?"));
    }

    private static List<String> visibleItems(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            List<String> ids = new ArrayList<>();
            if (client.gui.screen() instanceof HubScreen screen) {
                for (StorageNetwork.Entry entry : screen.getFilteredEntries()) {
                    ids.add(BuiltInRegistries.ITEM.getKey(entry.variant().getItem()).getPath());
                }
            }
            return ids;
        });
    }

    private static void checkOrder(ClientGameTestContext context, String name, List<String> expected) {
        List<String> actual = visibleItems(context);
        LOG.info("[teste] Ordem {}: {}", name, actual);
        check(actual.equals(expected), "Ordem " + name + " devia ser " + expected + ", veio " + actual);
    }

    // ------------------------------------------------------------------
    // Screenshots dos blocos
    // ------------------------------------------------------------------

    /** Mesa no chão, na parede e no teto + conector ligado a cabos por trás, por cima e pelo lado. */
    private static void takeBlockScreenshots(ClientGameTestContext context, TestServerContext server, BlockPos base) {
        BlockPos origin = base.north(6);
        server.runOnServer(s -> {
            ServerLevel level = firstPlayer(s.getPlayerList().getPlayers()).level();
            BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
            BlockState hub = ModRegistry.STORAGE_HUB.defaultBlockState();
            // Mesa no chão (tela para cima)
            place(level, origin.west(3), hub.setValue(HubBlock.FACING, Direction.UP));
            // Mesa na parede (tela para o sul, saindo da parede)
            place(level, origin.west(1).above().north(), stone);
            place(level, origin.west(1).above(), hub.setValue(HubBlock.FACING, Direction.SOUTH));
            // Mesa no teto (tela para baixo)
            place(level, origin.east(1).above(3), stone);
            place(level, origin.east(1).above(2), hub.setValue(HubBlock.FACING, Direction.DOWN));
            // Baú + conector apontando para ele + cabos chegando pelo lado, por cima e por trás
            BlockPos chest = origin.east(4);
            BlockPos connector = origin.east(3);
            place(level, chest, Blocks.CHEST.defaultBlockState());
            place(level, connector, ModRegistry.CHEST_CONNECTOR.defaultBlockState().setValue(ConnectorBlock.FACING, Direction.EAST));
            place(level, connector.west(), ModRegistry.NETWORK_CABLE.defaultBlockState());
            place(level, connector.west(2), ModRegistry.NETWORK_CABLE.defaultBlockState());
            place(level, connector.above(), ModRegistry.NETWORK_CABLE.defaultBlockState());
            place(level, connector.south(), ModRegistry.NETWORK_CABLE.defaultBlockState());
            for (BlockPos pos : List.of(connector, connector.west(), connector.west(2), connector.above(), connector.south())) {
                level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), Block.UPDATE_ALL);
            }
        });
        server.runCommand("tp @a " + (origin.getX() + 0.5) + " " + (origin.getY() + 1) + " " + (origin.getZ() + 5.5) + " 180 25");
        context.waitTicks(20);
        context.getInput().lookAt(origin.above());
        context.waitTicks(10);
        context.takeScreenshot("chestnet_blocos_frente");

        server.runCommand("tp @a " + (origin.getX() + 3.5) + " " + (origin.getY() + 1.6) + " " + (origin.getZ() + 2.5) + " 180 25");
        context.waitTicks(10);
        context.getInput().lookAt(origin.east(3));
        context.waitTicks(10);
        context.takeScreenshot("chestnet_conector_perto");
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("[teste] " + message);
    }
}
