package com.jinlan.moreornplants.block;

import com.jinlan.moreornplants.init.ModParticleTypes;
import com.jinlan.moreornplants.util.ModTags;
import com.jinlan.moreornplants.worldgen.dimension.ModDimension;
import com.jinlan.moreornplants.worldgen.dimension.PortalAnchorData;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class QunfangPortalBlock extends HorizontalDirectionalBlock implements Portal {
    public static final MapCodec<QunfangPortalBlock> CODEC = simpleCodec(QunfangPortalBlock::new);

    public QunfangPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void entityInside(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, Entity entity) {
        if (!entity.canUsePortal(false)) return;

        if (entity instanceof Player player) {
            boolean hasFlower = player.getMainHandItem().is(ItemTags.FLOWERS)
                    || player.getOffhandItem().is(ItemTags.FLOWERS);
            if (!hasFlower) return;
        } else {
            if (!entity.getType().is(ModTags.EntityTypes.QUNFANG_ALLOWED)) {
                return;
            }
        }

        entity.setAsInsidePortal(this, pos);
    }

    // ---------- 2. 需要站多久才传送 ----------
    @Override
    public int getPortalTransitionTime(@NotNull ServerLevel level, @NotNull Entity entity) {
        return entity instanceof Player player
                ? Math.max(1, level.getGameRules().getInt(
                player.getAbilities().invulnerable
                        ? GameRules.RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY
                        : GameRules.RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY))
                : 0;
    }

    // ---------- 3. 决定传到哪个维度、哪个坐标 ----------
    @Nullable
    @Override
    public DimensionTransition getPortalDestination(@NotNull ServerLevel level, @NotNull Entity entity, @NotNull BlockPos pos) {
        boolean isInQunfang = level.dimension().equals(ModDimension.THE_QUNFANG_LEVEL);
        ResourceKey<Level> targetKey = level.dimension().equals(ModDimension.THE_QUNFANG_LEVEL)
                ? Level.OVERWORLD
                : ModDimension.THE_QUNFANG_LEVEL;

        ServerLevel targetLevel = level.getServer().getLevel(targetKey);
        if (targetLevel == null) return null;

        PortalAnchorData data = PortalAnchorData.get(level);
        UUID id = entity.getUUID();

        Vec3 destination;
        if (isInQunfang) {
            // 离开群芳 → 记下群芳侧位置；落到主世界锚点
            data.setQunfangAnchor(id, entity.blockPosition());
            BlockPos anchor = data.getOverworldAnchor(id);
            destination = anchor != null
                    ? findSafePosition(level.getServer().overworld(), anchor, entity)
                    : Vec3.atBottomCenterOf(level.getServer().overworld().getSharedSpawnPos());
        } else {
            // 离开主世界 → 记下主世界侧位置；落到群芳锚点
            data.setOverworldAnchor(id, entity.blockPosition());
            BlockPos anchor = data.getQunfangAnchor(id);
            destination = anchor != null
                    ? findSafePosition(targetLevel, anchor, entity)
                    : Vec3.atBottomCenterOf(targetLevel.getSharedSpawnPos());
        }

        return new DimensionTransition(
                targetLevel,
                destination,
                entity.getDeltaMovement(),
                entity.getYRot(),
                entity.getXRot(),
                DimensionTransition.PLAY_PORTAL_SOUND
        );
    }

    private static Vec3 findSafePosition(ServerLevel level, BlockPos target, Entity entity) {
        if (isSafe(level, target)) return Vec3.atBottomCenterOf(target);

        // 从内到外螺旋搜索，先在原地上下找，再扩大半径
        for (int r = 1; r <= 3; r++) {
            for (int dy = 0; dy <= 3; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                        BlockPos candidate = target.offset(dx, dy, dz);
                        if (isSafe(level, candidate)) return Vec3.atBottomCenterOf(candidate);
                    }
                }
            }
        }
        // 兜底：出生点
        return Vec3.atBottomCenterOf(level.getSharedSpawnPos());
    }

    private static boolean isSafe(ServerLevel level, BlockPos pos) {
        if (pos.getY() <= level.getMinBuildHeight() + 1
                || pos.getY() >= level.getMaxBuildHeight() - 1) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        BlockState atState    = level.getBlockState(pos);
        BlockState aboveState = level.getBlockState(pos.above());

        boolean standable = belowState.isFaceSturdy(level, below, Direction.UP);
        boolean bodyFree  = atState.getCollisionShape(level, pos).isEmpty();
        boolean headFree  = aboveState.getCollisionShape(level, pos.above()).isEmpty();
        boolean noFluid   = level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty();

        return standable && bodyFree && headFree && noFluid;
    }

    // ---------- 4. 屏幕效果 ----------
    @Override
    public Portal.@NotNull Transition getLocalTransition() {
        return Transition.NONE;
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level,
                            @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                    0.5F, random.nextFloat() * 0.4F + 0.8F, false);
        }

        Direction back = state.getValue(FACING).getOpposite();

        double xMin = pos.getX();
        double xMax = pos.getX() + 1.0;
        double zMin = pos.getZ();
        double zMax = pos.getZ() + 1.0;

        if (back.getStepX() > 0) {
            xMin = pos.getX() + 0.5;
        } else if (back.getStepX() < 0) {
            xMax = pos.getX() + 0.5;
        } else if (back.getStepZ() > 0) {
            zMin = pos.getZ() + 0.5;
        } else if (back.getStepZ() < 0) {
            zMax = pos.getZ() + 0.5;
        }

        for (int i = 0; i < 1; i++) {
            double x = xMin + random.nextDouble() * (xMax - xMin);
            double y = pos.getY() + random.nextDouble();
            double z = zMin + random.nextDouble() * (zMax - zMin);

            double vx = (random.nextFloat() - 0.5) * 0.5;
            double vy = (random.nextFloat() - 0.5) * 0.5;
            double vz = (random.nextFloat() - 0.5) * 0.5;

            level.addParticle(ModParticleTypes.BAIHUA_CAT.get(), x, y, z, vx, vy, vz);
        }
    }
}
