package com.sergiofagundes.chestnet;

import com.sergiofagundes.chestnet.block.CableBlock;
import com.sergiofagundes.chestnet.block.ConnectorBlock;
import com.sergiofagundes.chestnet.block.HubBlock;
import com.sergiofagundes.chestnet.menu.HubMenu;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Tudo que o mod adiciona ao jogo fica registrado aqui. */
public final class ModRegistry {

    // ---------- Blocos ----------

    /** A "Mesa Hub": clique com o botão direito para ver todos os itens da rede. */
    public static final HubBlock STORAGE_HUB = registerBlock("storage_hub", HubBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD));

    /** Cabo: liga a mesa aos conectores. Conecta visualmente nos 6 lados. */
    public static final CableBlock NETWORK_CABLE = registerBlock("network_cable", CableBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(0.5F).sound(SoundType.METAL).noOcclusion());

    /** Conector: coloque encostado no baú (clicando na face do baú). A "boca" aponta para o baú. */
    public static final ConnectorBlock CHEST_CONNECTOR = registerBlock("chest_connector", ConnectorBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(1.0F).sound(SoundType.METAL).noOcclusion());

    // ---------- Itens dos blocos ----------

    public static final BlockItem STORAGE_HUB_ITEM = registerBlockItem("storage_hub", STORAGE_HUB);
    public static final BlockItem NETWORK_CABLE_ITEM = registerBlockItem("network_cable", NETWORK_CABLE);
    public static final BlockItem CHEST_CONNECTOR_ITEM = registerBlockItem("chest_connector", CHEST_CONNECTOR);

    // ---------- Menu (GUI) da Mesa Hub ----------

    public static final MenuType<HubMenu> HUB_MENU = Registry.register(BuiltInRegistries.MENU, ChestNet.id("storage_hub"),
            new MenuType<>(HubMenu::new, FeatureFlags.VANILLA_SET));

    // ---------- Aba no modo criativo ----------

    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ChestNet.id("main"),
            FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.chestnet"))
                    .icon(() -> new ItemStack(STORAGE_HUB_ITEM))
                    .displayItems((context, output) -> {
                        output.accept(STORAGE_HUB_ITEM);
                        output.accept(CHEST_CONNECTOR_ITEM);
                        output.accept(NETWORK_CABLE_ITEM);
                    })
                    .build());

    private ModRegistry() {}

    /** Chamado pelo ChestNet.onInitialize(): só de carregar a classe, tudo acima é registrado. */
    public static void init() {}

    // ---------- Ajudantes ----------

    private static <B extends Block> B registerBlock(String name, Function<BlockBehaviour.Properties, B> factory,
                                                      UnaryOperator<BlockBehaviour.Properties> settings) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ChestNet.id(name));
        // Desde o Minecraft 1.21.2 o bloco precisa saber o próprio ID antes de ser criado (setId)
        B block = factory.apply(settings.apply(BlockBehaviour.Properties.of()).setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }

    private static BlockItem registerBlockItem(String name, Block block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ChestNet.id(name));
        BlockItem item = new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix());
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
