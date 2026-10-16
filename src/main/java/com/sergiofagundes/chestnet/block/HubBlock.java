package com.sergiofagundes.chestnet.block;

import com.sergiofagundes.chestnet.menu.HubMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A Mesa Hub. Não guarda itens: ao abrir, ela percorre os cabos/conectores
 * e mostra o conteúdo de todos os baús ligados.
 *
 * FACING é para onde a face da "tela" aponta: para fora da superfície em que
 * foi colocada (chão = para cima, teto = para baixo, parede = para frente).
 */
public class HubBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    private static final Component TITLE = Component.translatable("container.chestnet.storage_hub");

    public HubBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockPos hubPos = pos.immutable();
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, p) -> new HubMenu(containerId, inventory, level, hubPos),
                    TITLE));
        }
        return InteractionResult.SUCCESS;
    }
}
