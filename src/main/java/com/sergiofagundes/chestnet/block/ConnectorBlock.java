package com.sergiofagundes.chestnet.block;

import com.sergiofagundes.chestnet.network.StorageNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Conector. FACING aponta para o bloco "alvo" (o baú).
 * Nos outros 5 lados ele ganha um braço igual ao do cabo quando o vizinho é um
 * cabo, a Mesa Hub ou outro conector, então a ligação fica contínua, sem vão.
 *
 * Como colocar: segure o conector e clique com o botão direito NA FACE DO BAÚ.
 */
public class ConnectorBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /** Cache: [direção do FACING][lados conectados (2^6)]. */
    private static final VoxelShape[][] SHAPES = new VoxelShape[6][64];

    static {
        for (Direction dir : Direction.values()) {
            // Placa encostada no baú + carcaça + núcleo no centro (igual ao do cabo)
            VoxelShape plate = box(dir, 3, 3, 0, 13, 13, 2);
            VoxelShape housing = box(dir, 4, 4, 2, 12, 12, 5);
            VoxelShape core = box(dir, 5, 5, 5, 11, 11, 11);
            VoxelShape base = Shapes.or(plate, housing, core);
            for (int mask = 0; mask < 64; mask++) {
                VoxelShape shape = base;
                for (Direction side : Direction.values()) {
                    if ((mask & (1 << side.ordinal())) != 0) {
                        shape = Shapes.or(shape, CableBlock.arm(side));
                    }
                }
                SHAPES[dir.ordinal()][mask] = shape;
            }
        }
    }

    public ConnectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = defaultBlockState().setValue(FACING, Direction.NORTH);
        for (BooleanProperty property : CableBlock.PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CableBlock.NORTH, CableBlock.SOUTH, CableBlock.EAST, CableBlock.WEST, CableBlock.UP, CableBlock.DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Clicou na face do baú -> o conector fica "olhando" para o baú
        Direction facing = context.getClickedFace().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        for (Direction dir : Direction.values()) {
            BlockState neighbor = context.getLevel().getBlockState(context.getClickedPos().relative(dir));
            state = state.setValue(CableBlock.PROPERTY_BY_DIRECTION.get(dir), dir != facing && StorageNetwork.isNetworkBlock(neighbor));
        }
        return state;
    }

    /** Chamado quando um vizinho muda: atualiza o braço daquele lado (o lado do baú nunca tem braço). */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighborPos, BlockState neighborState,
                                     RandomSource random) {
        boolean connected = directionToNeighbour != state.getValue(FACING) && StorageNetwork.isNetworkBlock(neighborState);
        return state.setValue(CableBlock.PROPERTY_BY_DIRECTION.get(directionToNeighbour), connected);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).ordinal()][CableBlock.connectionMask(state)];
    }

    /**
     * Cria uma caixa descrita como se o conector apontasse para o NORTE
     * (z = 0 é o lado do baú) e gira para a direção pedida.
     */
    private static VoxelShape box(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case NORTH -> Block.box(x1, y1, z1, x2, y2, z2);
            case SOUTH -> Block.box(x1, y1, 16 - z2, x2, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, x1, z2, y2, x2);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case DOWN -> Block.box(x1, z1, y1, x2, z2, y2);
            case UP -> Block.box(x1, 16 - z2, y1, x2, 16 - z1, y2);
        };
    }
}
