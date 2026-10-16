import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Gera as texturas do mod (blocos 16x16 e a tela da Mesa Hub).
 *
 * Rodar a partir da pasta do projeto:  java tools/GenTextures.java
 *
 * Os blocos seguem o visual de ferro + pedra lisa (as receitas usam esses
 * materiais), com pouco vermelho (redstone) só nos detalhes "ligados".
 *
 * As coordenadas da tela (grade, busca, barra de rolagem, inventário) precisam
 * bater com HubMenu (GRID_X, GRID_Y, INVENTORY_Y) e HubScreen (SEARCH_*, SCROLLBAR_*).
 */
public class GenTextures {
    static final String OUT = "src/main/resources/assets/chestnet/textures/";

    // Pedra lisa: miolo claro e moldura um pouco mais escura (mesmos tons da smooth_stone vanilla)
    static final int[] STONE_IN = {0xD1D1D1, 0xD4D4D4, 0xD4D4D4, 0xD8D8D8};
    static final int[] STONE_EDGE = {0xB4B4B4, 0xBBBBBB, 0xC5C5C5};
    static final int STONE_DARK = 0xCECECE;
    // Ferro (tons do bloco de ferro vanilla) + tons mais escuros para frisos e sombras
    static final int IRON_HI = 0xF2F2F2, IRON_HI2 = 0xECECEC, IRON = 0xE6E6E6, IRON_LO = 0xDCDCDC, IRON_LO2 = 0xD6D6D6;
    static final int IRON_EDGE = 0xC1C1C1, IRON_EDGE2 = 0xB9B9B9, IRON_DEEP = 0xB0B0B0, IRON_TOP = 0xCFCFCF;
    static final int STEEL = 0x8C8C8C, STEEL_DARK = 0x6E6E6E, STEEL_DARKER = 0x4A4A4A;
    // Vermelho de redstone (só detalhes)
    static final int R1 = 0x6E0A0A, R2 = 0xC81E1E, R3 = 0xFF5A4A;
    // Tela apagada da Mesa e luzes desligadas
    static final int SCREEN = 0x1A1616, OFF_HI = 0x443434, OFF = 0x332626, OFF_LO = 0x281E1E;

    // Cores da GUI no estilo vanilla
    static final int GUI_BG = 0xC6C6C6, GUI_LIGHT = 0xFFFFFF, GUI_SHADOW = 0x555555, GUI_OUTLINE = 0x000000;
    static final int SLOT_DARK = 0x373737, SLOT_FILL = 0x8B8B8B, SLOT_LIGHT = 0xFFFFFF;
    static final int FIELD_FILL = 0x1E1E1E;

    public static void main(String[] args) throws IOException {
        save(storageHubTop(), "block/storage_hub_top.png");
        save(storageHubSide(), "block/storage_hub_side.png");
        save(storageHubBottom(), "block/storage_hub_bottom.png");
        save(networkCable(), "block/network_cable.png");
        save(chestConnector(), "block/chest_connector.png");
        save(chestConnectorFront(), "block/chest_connector_front.png");
        save(hubGui(), "gui/storage_hub.png");
        System.out.println("Texturas geradas em " + OUT);
    }

    // ------------------------------------------------------------------
    // Blocos
    // ------------------------------------------------------------------

    /** Face da "tela" (gira conforme onde a Mesa foi colocada): moldura de ferro, tela escura e 3x3 luzes. */
    static BufferedImage storageHubTop() {
        BufferedImage img = ironPanel(new Random(1));
        // rebaixo da tela: sombra em cima/esquerda, brilho embaixo/direita
        for (int i = 2; i <= 13; i++) {
            set(img, i, 2, STEEL_DARKER);
            set(img, 2, i, STEEL_DARKER);
            set(img, i, 13, IRON_HI);
            set(img, 13, i, IRON_HI);
        }
        rect(img, 3, 3, 10, 10, SCREEN);
        // luzes 2x2: só algumas acesas (vermelho), o resto apagado
        int[] starts = {4, 7, 10};
        boolean[] lit = {true, false, false, false, true, false, false, true, true};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int x = starts[col], y = starts[row];
                boolean on = lit[row * 3 + col];
                set(img, x, y, on ? R3 : OFF_HI);
                set(img, x + 1, y, on ? R2 : OFF);
                set(img, x, y + 1, on ? R2 : OFF);
                set(img, x + 1, y + 1, on ? R1 : OFF_LO);
            }
        }
        return img;
    }

    /** Pedra lisa com uma faixa de ferro rebitada e um LED vermelho pequeno no meio. */
    static BufferedImage storageHubSide() {
        BufferedImage img = smoothStone(new Random(2));
        for (int x = 0; x < 16; x++) {
            set(img, x, 6, IRON_HI);
            set(img, x, 7, IRON);
            set(img, x, 8, IRON_LO);
            set(img, x, 9, IRON_DEEP);
        }
        set(img, 0, 7, IRON_EDGE);
        set(img, 0, 8, IRON_EDGE2);
        set(img, 15, 7, IRON_EDGE2);
        set(img, 15, 8, IRON_DEEP);
        for (int x : new int[] {2, 13}) {
            set(img, x, 7, STEEL);
            set(img, x, 8, STEEL_DARK);
        }
        set(img, 7, 7, R3);
        set(img, 8, 7, R2);
        set(img, 7, 8, R2);
        set(img, 8, 8, R1);
        return img;
    }

    /** Costas da Mesa: pedra lisa com moldura de ferro e rebites nos cantos. */
    static BufferedImage storageHubBottom() {
        BufferedImage img = smoothStone(new Random(3));
        for (int i = 0; i < 16; i++) {
            set(img, i, 0, IRON_TOP);
            set(img, 0, i, IRON_EDGE);
            set(img, i, 15, IRON_EDGE2);
            set(img, 15, i, IRON_DEEP);
        }
        for (int i = 1; i < 15; i++) {
            set(img, i, 1, IRON_HI);
            set(img, 1, i, IRON_HI2);
            set(img, i, 14, IRON_LO);
            set(img, 14, i, IRON_LO2);
        }
        for (int[] p : new int[][] {{3, 3}, {12, 3}, {3, 12}, {12, 12}}) {
            set(img, p[0], p[1], STEEL);
        }
        return img;
    }

    /**
     * Cabo: o modelo usa a faixa horizontal (linhas 5..10) nos lados dos braços,
     * a faixa vertical (colunas 5..10) em cima/embaixo, e o centro 6x6 no núcleo.
     * Capa de ferro, miolo de pedra lisa e uma linha vermelha.
     */
    static BufferedImage networkCable() {
        Random rnd = new Random(4);
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) set(img, x, y, rnd.nextInt(3) == 0 ? IRON_LO : IRON);
        // de cima para baixo (ou da esquerda para a direita): borda, brilho, pedra, linha vermelha, sombra, borda
        int[] band = {STEEL, IRON_HI, STONE_IN[1], R2, IRON_EDGE, STEEL_DARK};
        for (int i = 0; i < 16; i++) {
            for (int k = 0; k < 6; k++) {
                set(img, i, 5 + k, band[k]); // faixa horizontal
                set(img, 5 + k, i, band[k]); // faixa vertical
            }
        }
        // núcleo: anel de ferro, pedra no meio e o ponto vermelho do "conector"
        for (int i = 5; i <= 10; i++) {
            set(img, i, 5, STEEL);
            set(img, 5, i, STEEL);
            set(img, i, 10, STEEL_DARK);
            set(img, 10, i, STEEL_DARK);
        }
        rect(img, 6, 6, 4, 4, STONE_IN[1]);
        for (int i = 6; i <= 9; i++) {
            set(img, i, 6, IRON_HI);
            set(img, 6, i, IRON_HI);
            set(img, i, 9, IRON_EDGE);
            set(img, 9, i, IRON_EDGE);
        }
        set(img, 7, 7, R3);
        set(img, 8, 7, R2);
        set(img, 7, 8, R2);
        set(img, 8, 8, R1);
        return img;
    }

    /** Lados do conector (placa e carcaça): ferro com friso escuro. */
    static BufferedImage chestConnector() {
        BufferedImage img = ironPanel(new Random(5));
        for (int i = 3; i <= 12; i++) {
            set(img, i, 3, STEEL);
            set(img, 3, i, STEEL);
            set(img, i, 12, STEEL_DARK);
            set(img, 12, i, STEEL_DARK);
        }
        return img;
    }

    /** A "boca" que encosta no baú (área 3..13 da placa): abertura escura com borda vermelha fina. */
    static BufferedImage chestConnectorFront() {
        BufferedImage img = ironPanel(new Random(6));
        for (int i = 4; i <= 11; i++) {
            set(img, i, 4, R2);
            set(img, 4, i, R2);
            set(img, i, 11, R1);
            set(img, 11, i, R1);
        }
        rect(img, 5, 5, 6, 6, 0x141414);
        for (int i = 5; i <= 10; i++) {
            set(img, i, 5, 0x0A0A0A);
            set(img, 5, i, 0x0A0A0A);
        }
        return img;
    }

    /** Pedra lisa: miolo claro salpicado e moldura de 1 pixel um pouco mais escura. */
    static BufferedImage smoothStone(Random rnd) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
                int c = edge ? pick(rnd, STONE_EDGE) : pick(rnd, STONE_IN);
                if (!edge && rnd.nextInt(12) == 0) c = STONE_DARK;
                set(img, x, y, c);
            }
        }
        return img;
    }

    /** Chapa de ferro com as listras do bloco de ferro (brilho / meio / sombra a cada 3 linhas). */
    static BufferedImage ironPanel(Random rnd) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y < 15; y++) {
            int split1 = 5 + rnd.nextInt(3), split2 = 9 + rnd.nextInt(3);
            for (int x = 1; x < 15; x++) {
                int c = switch ((y - 1) % 3) {
                    case 0 -> x < split1 ? IRON_HI : x < split2 ? IRON_HI2 : IRON;
                    case 1 -> IRON;
                    default -> x < split1 + 2 ? IRON_LO : x < split2 + 1 ? 0xE0E0E0 : IRON_LO2;
                };
                set(img, x, y, c);
            }
            boolean shadowRow = (y - 1) % 3 == 2;
            set(img, 0, y, shadowRow ? IRON_DEEP : IRON_EDGE);
            set(img, 15, y, shadowRow ? IRON_DEEP : IRON_EDGE2);
        }
        for (int x = 0; x < 16; x++) {
            set(img, x, 0, rnd.nextInt(3) == 0 ? IRON_EDGE : IRON_TOP);
            set(img, x, 15, rnd.nextInt(2) == 0 ? IRON_EDGE : IRON_EDGE2);
        }
        return img;
    }

    // ------------------------------------------------------------------
    // Tela da Mesa Hub (256x256, painel 195x222 no canto)
    // ------------------------------------------------------------------

    static BufferedImage hubGui() {
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        panel(img, 195, 222);

        // Campo de busca (texto em x=10, y=6; mesmas medidas de HubScreen.SEARCH_FIELD_*)
        inset(img, 7, 4, 122, 13, FIELD_FILL); // à direita ficam os botões de ordenação

        // Grade 9x6 (itens em GRID_X=8, GRID_Y=20)
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) slot(img, 8 + col * 18, 20 + row * 18);
        }

        // Trilho da barra de rolagem (puxador 12x15 em x=175, entre y=20 e y=125)
        inset(img, 174, 19, 14, 108, SLOT_FILL);

        // Inventário do jogador (INVENTORY_Y=140) + hotbar
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) slot(img, 8 + col * 18, 140 + row * 18);
        }
        for (int col = 0; col < 9; col++) slot(img, 8 + col * 18, 198);
        return img;
    }

    /** Painel cinza do Minecraft: contorno preto com cantos arredondados, luz em cima/esquerda, sombra embaixo/direita. */
    static void panel(BufferedImage img, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int cx = Math.min(x, w - 1 - x);
                int cy = Math.min(y, h - 1 - y);
                if (cx + cy < 2) continue; // canto arredondado: transparente
                boolean outline = cx == 0 || cy == 0 || (cx == 1 && cy == 1);
                if (outline) {
                    set(img, x, y, GUI_OUTLINE);
                    continue;
                }
                boolean light = x <= 2 || y <= 2;
                boolean shadow = x >= w - 3 || y >= h - 3;
                set(img, x, y, light && shadow ? GUI_BG : light ? GUI_LIGHT : shadow ? GUI_SHADOW : GUI_BG);
            }
        }
    }

    /** Slot vanilla 18x18 para um item desenhado em (x, y). */
    static void slot(BufferedImage img, int x, int y) {
        inset(img, x - 1, y - 1, 18, 18, SLOT_FILL);
    }

    /** Retângulo "afundado": escuro em cima/esquerda, claro embaixo/direita. */
    static void inset(BufferedImage img, int x, int y, int w, int h, int fill) {
        rect(img, x, y, w, h, fill);
        for (int i = 0; i < w - 1; i++) set(img, x + i, y, SLOT_DARK);
        for (int i = 0; i < h - 1; i++) set(img, x, y + i, SLOT_DARK);
        for (int i = 1; i < w; i++) set(img, x + i, y + h - 1, SLOT_LIGHT);
        for (int i = 1; i < h; i++) set(img, x + w - 1, y + i, SLOT_LIGHT);
    }

    // ------------------------------------------------------------------
    // Ajudantes
    // ------------------------------------------------------------------

    static int pick(Random rnd, int[] colors) {
        return colors[rnd.nextInt(colors.length)];
    }

    static void rect(BufferedImage img, int x, int y, int w, int h, int rgb) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) set(img, x + i, y + j, rgb);
    }

    static void set(BufferedImage img, int x, int y, int rgb) {
        img.setRGB(x, y, 0xFF000000 | rgb);
    }

    static void save(BufferedImage img, String path) throws IOException {
        File file = new File(OUT + path);
        file.getParentFile().mkdirs();
        ImageIO.write(img, "png", file);
    }
}
