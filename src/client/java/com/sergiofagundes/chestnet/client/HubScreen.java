package com.sergiofagundes.chestnet.client;

import static com.sergiofagundes.chestnet.menu.HubMenu.COLUMNS;
import static com.sergiofagundes.chestnet.menu.HubMenu.GRID_X;
import static com.sergiofagundes.chestnet.menu.HubMenu.GRID_Y;
import static com.sergiofagundes.chestnet.menu.HubMenu.VISIBLE_ROWS;

import com.mojang.blaze3d.platform.InputConstants;
import com.sergiofagundes.chestnet.ChestNet;
import com.sergiofagundes.chestnet.menu.HubMenu;
import com.sergiofagundes.chestnet.network.StorageNetwork;
import com.sergiofagundes.chestnet.packet.HubActionPayload;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Tela da Mesa Hub: busca no topo, grade com todos os itens da rede (com o
 * total real de cada um) e barra de rolagem. A grade não usa slots: é
 * desenhada aqui a partir da lista que o servidor manda para o HubMenu.
 */
public class HubScreen extends AbstractContainerScreen<HubMenu> {
    private static final Identifier TEXTURE = ChestNet.id("textures/gui/storage_hub.png");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    private static final Identifier SLOT_HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
    private static final Identifier SLOT_HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");

    private static final int SLOT_SIZE = 18;

    // No Minecraft 26.x os botões do mouse são 1 = esquerdo e 3 = direito (não mais 0 e 1 como no GLFW)
    private static final int MOUSE_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    private static final int MOUSE_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;

    // Campo de busca: retângulo escuro desenhado na textura (todo ele é clicável) e o texto dentro dele
    private static final int SEARCH_FIELD_X = 7;
    private static final int SEARCH_FIELD_Y = 4;
    private static final int SEARCH_FIELD_WIDTH = 122;
    private static final int SEARCH_FIELD_HEIGHT = 13;
    private static final int SEARCH_X = 10;
    private static final int SEARCH_Y = 6;
    private static final int SEARCH_WIDTH = 116;

    // Botões de ordenação, à direita da busca
    private static final int SORT_BUTTONS_X = 131;
    private static final int SORT_BUTTON_HEIGHT = 13;

    /** Ordem da grade. Fica lembrada enquanto o jogo estiver aberto. */
    private enum SortMode {
        NAME("A-Z", 20, "gui.chestnet.sort.name"),
        AMOUNT_DESC("↓", 17, "gui.chestnet.sort.amount_desc"),
        AMOUNT_ASC("↑", 17, "gui.chestnet.sort.amount_asc");

        final String label;
        final int width;
        final String tooltipKey;

        SortMode(String label, int width, String tooltipKey) {
            this.label = label;
            this.width = width;
            this.tooltipKey = tooltipKey;
        }
    }

    private static SortMode sortMode = SortMode.AMOUNT_DESC;

    // Barra de rolagem (o trilho está na textura; o "puxador" é o sprite do modo criativo)
    private static final int SCROLLBAR_X = 175;
    private static final int SCROLLBAR_Y = GRID_Y;
    private static final int SCROLLBAR_HEIGHT = VISIBLE_ROWS * SLOT_SIZE - 2;
    private static final int SCROLLER_WIDTH = 12;
    private static final int SCROLLER_HEIGHT = 15;

    private EditBox searchBox;
    private final Map<SortMode, Button> sortButtons = new EnumMap<>(SortMode.class);
    private List<StorageNetwork.Entry> filtered = List.of();
    /** Nome de cada item já preparado para a busca (minúsculo, sem acento). */
    private final Map<ItemVariant, String> searchNames = new HashMap<>();
    private int seenRevision = -1;
    private int scrollRow = 0;
    private boolean draggingScrollbar = false;
    private boolean gridPressed = false;

    public HubScreen(HubMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 195, 222);
        this.inventoryLabelY = HubMenu.INVENTORY_Y - 12;
    }

    @Override
    protected void init() {
        super.init();
        String previous = searchBox == null ? "" : searchBox.getValue(); // mantém a busca ao redimensionar a janela
        searchBox = new EditBox(font, leftPos + SEARCH_X, topPos + SEARCH_Y, SEARCH_WIDTH, font.lineHeight,
                Component.translatable("gui.chestnet.search"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setHint(Component.translatable("gui.chestnet.search"));
        searchBox.setValue(previous);
        searchBox.setResponder(text -> {
            scrollRow = 0;
            refilter();
        });
        addRenderableWidget(searchBox); // sem foco ao abrir: clique na busca para digitar

        // [A-Z] [↓] [↑]: o botão da ordem atual fica "apertado" (desativado)
        sortButtons.clear();
        int x = leftPos + SORT_BUTTONS_X;
        for (SortMode mode : SortMode.values()) {
            Button button = Button.builder(Component.literal(mode.label), b -> setSortMode(mode))
                    .bounds(x, topPos + SEARCH_FIELD_Y, mode.width, SORT_BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable(mode.tooltipKey)))
                    .build();
            sortButtons.put(mode, addRenderableWidget(button));
            x += mode.width + 1;
        }
        updateSortButtons();
        refilter();
    }

    private void setSortMode(SortMode mode) {
        sortMode = mode;
        updateSortButtons();
        refilter();
    }

    private void updateSortButtons() {
        sortButtons.forEach((mode, button) -> button.active = mode != sortMode);
    }

    /** Chegou lista nova do servidor? Refiltra. */
    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.getClientRevision() != seenRevision) {
            seenRevision = menu.getClientRevision();
            refilter();
        }
    }

    // ------------------------------------------------------------------
    // Busca
    // ------------------------------------------------------------------

    private void refilter() {
        String query = normalize(searchBox == null ? "" : searchBox.getValue().trim());
        filtered = menu.getClientEntries().stream()
                .filter(e -> query.isEmpty() || searchName(e.variant()).contains(query))
                .sorted(sortComparator())
                .toList();
        setScrollRow(scrollRow);
    }

    private Comparator<StorageNetwork.Entry> sortComparator() {
        Comparator<StorageNetwork.Entry> byName = Comparator.comparing(e -> searchName(e.variant()));
        Comparator<StorageNetwork.Entry> byAmount = Comparator.comparingLong(StorageNetwork.Entry::amount);
        return switch (sortMode) {
            case NAME -> byName.thenComparing(byAmount.reversed());
            case AMOUNT_DESC -> byAmount.reversed().thenComparing(byName);
            case AMOUNT_ASC -> byAmount.thenComparing(byName);
        };
    }

    /** Usado pelos testes automáticos (src/gametest). */
    public List<StorageNetwork.Entry> getFilteredEntries() {
        return filtered;
    }

    private String searchName(ItemVariant variant) {
        return searchNames.computeIfAbsent(variant, v -> normalize(v.toStack().getHoverName().getString()));
    }

    /** "Tábua de Carvalho" -> "tabua de carvalho": a busca ignora maiúsculas e acentos. */
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Desenho
    // ------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    /** Só o label "Inventário": o topo da tela é da busca. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040, false);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(graphics, mouseX, mouseY, partialTick);

        int hovered = hoveredIndex(mouseX, mouseY);
        for (int i = 0; i < COLUMNS * VISIBLE_ROWS; i++) {
            int index = scrollRow * COLUMNS + i;
            int x = leftPos + GRID_X + (i % COLUMNS) * SLOT_SIZE;
            int y = topPos + GRID_Y + (i / COLUMNS) * SLOT_SIZE;
            boolean isHovered = index == hovered;

            if (isHovered) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK, x - 4, y - 4, 24, 24);
            if (index < filtered.size()) {
                StorageNetwork.Entry entry = filtered.get(index);
                graphics.item(entry.variant().toStack(), x, y);
                extractAmount(graphics, formatAmount(entry.amount()), x, y);
            }
            if (isHovered) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT, x - 4, y - 4, 24, 24);
        }

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, canScroll() ? SCROLLER : SCROLLER_DISABLED,
                leftPos + SCROLLBAR_X, topPos + SCROLLBAR_Y + scrollerOffset(), SCROLLER_WIDTH, SCROLLER_HEIGHT);
    }

    /** Quantidade em fonte pequena (metade do tamanho), no canto de baixo à direita do item. */
    private void extractAmount(GuiGraphicsExtractor graphics, String text, int x, int y) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 16, y + 16);
        graphics.pose().scale(0.5f, 0.5f);
        graphics.text(font, text, -font.width(text), -8, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }

    /** 812 -> "812", 3456 -> "3,4K", 45678 -> "45K", 1234567 -> "1,2M". */
    static String formatAmount(long amount) {
        if (amount < 1000) return Long.toString(amount);
        String[] units = {"K", "M", "B", "T"};
        double value = amount;
        int unit = -1;
        while (value >= 1000 && unit < units.length - 1) {
            value /= 1000;
            unit++;
        }
        if (value < 10) {
            int tenths = (int) Math.floor(value * 10); // arredonda para baixo: nunca mostra mais do que tem
            return (tenths % 10 == 0 ? Integer.toString(tenths / 10) : (tenths / 10) + "," + (tenths % 10)) + units[unit];
        }
        return (long) Math.floor(value) + units[unit];
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (!menu.getCarried().isEmpty()) return;
        int index = hoveredIndex(mouseX, mouseY);
        if (index < 0 || index >= filtered.size()) return;

        StorageNetwork.Entry entry = filtered.get(index);
        List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, entry.variant().toStack()));
        lines.add(Component.translatable("gui.chestnet.total", String.format("%,d", entry.amount()))
                .withStyle(ChatFormatting.YELLOW));
        graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    // ------------------------------------------------------------------
    // Grade e rolagem
    // ------------------------------------------------------------------

    private boolean isOverGrid(double mouseX, double mouseY) {
        double x = mouseX - (leftPos + GRID_X - 1);
        double y = mouseY - (topPos + GRID_Y - 1);
        return x >= 0 && y >= 0 && x < COLUMNS * SLOT_SIZE && y < VISIBLE_ROWS * SLOT_SIZE;
    }

    /** Índice (em {@link #filtered}) da célula sob o mouse, ou -1 se o mouse está fora da grade. */
    private int hoveredIndex(double mouseX, double mouseY) {
        if (!isOverGrid(mouseX, mouseY)) return -1;
        int col = (int) ((mouseX - (leftPos + GRID_X - 1)) / SLOT_SIZE);
        int row = (int) ((mouseY - (topPos + GRID_Y - 1)) / SLOT_SIZE);
        return (scrollRow + row) * COLUMNS + col;
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        double x = mouseX - (leftPos + SCROLLBAR_X);
        double y = mouseY - (topPos + SCROLLBAR_Y);
        return x >= 0 && y >= 0 && x < SCROLLER_WIDTH && y < SCROLLBAR_HEIGHT;
    }

    private int maxScrollRow() {
        int rows = (filtered.size() + COLUMNS - 1) / COLUMNS;
        return Math.max(0, rows - VISIBLE_ROWS);
    }

    private boolean canScroll() {
        return maxScrollRow() > 0;
    }

    private void setScrollRow(int row) {
        scrollRow = Mth.clamp(row, 0, maxScrollRow());
    }

    private int scrollerOffset() {
        int max = maxScrollRow();
        return max == 0 ? 0 : Math.round((float) scrollRow / max * (SCROLLBAR_HEIGHT - SCROLLER_HEIGHT));
    }

    private void scrollTo(double mouseY) {
        float position = (float) ((mouseY - (topPos + SCROLLBAR_Y) - SCROLLER_HEIGHT / 2.0) / (SCROLLBAR_HEIGHT - SCROLLER_HEIGHT));
        setScrollRow(Math.round(Mth.clamp(position, 0.0f, 1.0f) * maxScrollRow()));
    }

    // ------------------------------------------------------------------
    // Mouse e teclado
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        // Busca: o retângulo inteiro é clicável. Esquerdo foca (e posiciona o cursor); direito limpa.
        if (isOverSearchField(mouseX, mouseY)) {
            if (button == MOUSE_RIGHT) searchBox.setValue("");
            setFocused(searchBox);
            if (button == MOUSE_LEFT && searchBox.isMouseOver(mouseX, mouseY)) searchBox.mouseClicked(event, doubleClick);
            return true;
        }
        // Clicou fora da busca: ela perde o foco (assim o "E" volta a fechar a tela)
        if (getFocused() == searchBox) setFocused(null);

        if (button == MOUSE_LEFT && isOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = canScroll();
            if (draggingScrollbar) scrollTo(mouseY);
            return true;
        }
        if ((button == MOUSE_LEFT || button == MOUSE_RIGHT) && isOverGrid(mouseX, mouseY)) {
            gridPressed = true;
            clickGrid(event);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean isOverSearchField(double mouseX, double mouseY) {
        double x = mouseX - (leftPos + SEARCH_FIELD_X);
        double y = mouseY - (topPos + SEARCH_FIELD_Y);
        return x >= 0 && y >= 0 && x < SEARCH_FIELD_WIDTH && y < SEARCH_FIELD_HEIGHT;
    }

    private void clickGrid(MouseButtonEvent event) {
        boolean rightButton = event.button() == MOUSE_RIGHT;
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            // Segurando algo no cursor -> guarda na rede (em qualquer lugar da grade)
            send(ItemVariant.of(carried), rightButton ? HubActionPayload.Action.INSERT_ONE : HubActionPayload.Action.INSERT_CARRIED);
            return;
        }
        int index = hoveredIndex(event.x(), event.y());
        if (index < 0 || index >= filtered.size()) return;
        HubActionPayload.Action action = event.hasShiftDown() ? HubActionPayload.Action.TAKE_TO_INVENTORY
                : rightButton ? HubActionPayload.Action.TAKE_HALF
                : HubActionPayload.Action.TAKE_STACK;
        send(filtered.get(index).variant(), action);
    }

    private void send(ItemVariant variant, HubActionPayload.Action action) {
        ClientPlayNetworking.send(new HubActionPayload(menu.containerId, variant, action));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        // O clique começou na grade ou na barra: não deixa o vanilla tratar a soltura como clique em slot
        if (draggingScrollbar || gridPressed) {
            draggingScrollbar = false;
            gridPressed = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (canScroll() && (isOverGrid(mouseX, mouseY) || isOverScrollbar(mouseX, mouseY))) {
            setScrollRow(scrollRow - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchBox.keyPressed(event)) return true;
        // Digitando na busca: "E" e outras teclas não fecham a tela (só ESC)
        if (searchBox.isFocused() && !event.isEscape()) return true;
        return super.keyPressed(event);
    }
}
