package com.sergiofagundes.chestnet;

import com.sergiofagundes.chestnet.packet.ModPackets;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classe principal do mod "Chest Network" (Fabric).
 *
 * Ideia: Mesa Hub > Conector > Cabo > Cabo > ... > Conector > Baú
 * A Mesa Hub mostra (e deixa tirar/colocar) todos os itens de todos os baús ligados à rede.
 */
public class ChestNet implements ModInitializer {
    public static final String MOD_ID = "chestnet";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Registra blocos, itens, menu e a aba do modo criativo
        ModRegistry.init();
        // Pacotes da tela da Mesa Hub (lista de itens e cliques na grade)
        ModPackets.init();
        LOGGER.info("Chest Network carregado!");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
