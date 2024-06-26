package com.andersmmg.lockandblock.block.custom;

import com.andersmmg.lockandblock.LockAndBlock;
import com.andersmmg.lockandblock.util.VoxelUtils;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.tick.TickPriority;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TripMineBlock extends Block {
    public static final DirectionProperty FACING = Properties.FACING;
    public static final BooleanProperty SET = LockAndBlock.SET;
    private static final VoxelShape VOXEL_SHAPE = Block.createCuboidShape(5, 5, 14, 11, 11, 16);
    private static final VoxelShape VOXEL_SHAPE_UP = Block.createCuboidShape(5, 0, 5, 11, 2, 11);
    private static final VoxelShape VOXEL_SHAPE_DOWN = Block.createCuboidShape(5, 14, 5, 11, 16, 11);

    public TripMineBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH).with(SET, false));
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        world.scheduleBlockTick(pos, this, 40, TickPriority.NORMAL);
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (state.get(SET)) {
            boolean shouldPower = this.shouldPower(world, pos, state);
            if (shouldPower) {
                world.createExplosion(null, pos.getX(), pos.getY(), pos.getZ(), 2.0f, World.ExplosionSourceType.NONE);
                world.removeBlock(pos, false);
            }
        } else {
            world.setBlockState(pos, state.with(SET, true), 3);
        }
        world.scheduleBlockTick(pos, this, 1, TickPriority.byIndex(1));
    }

    private boolean shouldPower(World world, BlockPos pos, BlockState state) {
        Direction direction = state.get(TripMineBlock.FACING);
        int distance = 0;

        for (int i = 1; i <= LockAndBlock.CONFIG.maxTripMineDistance() + 1; i++) {
            BlockState blockState = world.getBlockState(pos.offset(direction, i));
            if (blockState.isSideSolid(world, pos.offset(direction, i), direction.getOpposite(), SideShapeType.FULL)) {
                distance = i;
                break;
            }
        }

        // spawn a line of particles
        for (int i = 1; i <= distance; i++) {
            spawnParticles(state, world, pos.offset(direction, i), 0.5f);
        }

        if (distance == 0) {
            return false;
        }

        // check if there are players in the area
        Box detectionBox = new Box(pos).expand(direction.getOffsetX() * distance, direction.getOffsetY() * distance, direction.getOffsetZ() * distance);

        List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, detectionBox, player -> true);
        return !players.isEmpty();
    }

    private static void spawnParticles(BlockState state, World world, BlockPos pos, float alpha) {
        if (!(world instanceof ServerWorld)) return; // Ensure we are on the server side

        Direction direction = state.get(FACING).getOpposite();
        Direction direction2 = getDirection(state).getOpposite();
        double d = (double) pos.getX() + 0.5 + 0.0 * (double) direction.getOffsetX() + 0.4 * (double) direction2.getOffsetX();
        double e = (double) pos.getY() + 0.5 + 0.0 * (double) direction.getOffsetY() + 0.4 * (double) direction2.getOffsetY();
        double f = (double) pos.getZ() + 0.5 + 0.0 * (double) direction.getOffsetZ() + 0.4 * (double) direction2.getOffsetZ();
        float steps = 10f;
        for (int i = 0; i < (int) steps; i++) { // Change the number of particles here
            ((ServerWorld) world).spawnParticles(
                    new DustParticleEffect(Vec3d.unpackRgb(0x00FF00).toVector3f(), alpha), // Green color
                    d + (double) direction.getOffsetX() * (i / steps), // Change the offset here
                    e + (double) direction.getOffsetY() * (i / steps), // Change the offset here
                    f + (double) direction.getOffsetZ() * (i / steps), // Change the offset here
                    1, // Number of particles
                    0.0, 0.0, 0.0, // Offsets
                    0.0 // Speed
            );
        }
    }

    protected static Direction getDirection(BlockState state) {
        return state.get(FACING);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (getDirection(state)) {
            case UP -> VOXEL_SHAPE_UP;
            case DOWN -> VOXEL_SHAPE_DOWN;
            default -> VoxelUtils.rotateShape(getDirection(state), VOXEL_SHAPE);
        };
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, SET);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getSide());
    }
}
