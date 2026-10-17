package com.jinlan.moreornplants.client;

import com.jinlan.moreornplants.MoreOrnPlants;
import com.jinlan.moreornplants.block.QunfangPortalBlock;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Objects;

@EventBusSubscriber(modid = MoreOrnPlants.MODID, value = Dist.CLIENT)
public class ClientPortalOverlay {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MoreOrnPlants.MODID, "textures/gui/qunfang_scroll.png");

    private static final int TEX_SIZE = 512;

    private static final int FADE_IN_TICKS = 80;
    private static final int MAX_ALPHA = 220;

    private static int portalTicks = 0;

    // ---------- 每 tick 检测 ----------
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (isPlayerInsidePortal(mc)) {
            portalTicks = Math.min(portalTicks + 1, FADE_IN_TICKS);
        } else {
            portalTicks = Math.max(portalTicks - 2, 0);
        }
    }

    private static boolean isPlayerInsidePortal(Minecraft mc) {
        Objects.requireNonNull(mc.player);
        Objects.requireNonNull(mc.level);
        AABB box = mc.player.getBoundingBox().deflate(0.001);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (mc.level.getBlockState(pos).getBlock() instanceof QunfangPortalBlock) {
                return true;
            }
        }
        return false;
    }

    // ---------- 渲染 overlay ----------
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (portalTicks <= 0) return;

        float progress = portalTicks / (float) FADE_IN_TICKS;
        float alpha = progress * (MAX_ALPHA / 255.0F);
        if (alpha <= 0) return;

        int width  = event.getGuiGraphics().guiWidth();
        int height = event.getGuiGraphics().guiHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);

        // 全屏拉伸纹理：屏幕 (0,0) 到 (width,height)，纹理 (0,0) 到 (TEX_SIZE,TEX_SIZE)
        event.getGuiGraphics().blit(
                TEXTURE,
                0, 0,
                width, height,
                0, 0,
                TEX_SIZE, TEX_SIZE,
                TEX_SIZE, TEX_SIZE
        );

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
