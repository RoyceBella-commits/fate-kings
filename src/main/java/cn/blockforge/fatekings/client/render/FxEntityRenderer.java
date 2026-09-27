package cn.blockforge.fatekings.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Effect entity renderer: geometry is recorded during extraction (in entity-local coordinates) and
 * replayed at submit, optionally with one item model (a treasure, a weapon) placed on top.
 */
public abstract class FxEntityRenderer<T extends Entity> extends EntityRenderer<T, FxEntityRenderer.State> {
    public static final class State extends EntityRenderState {
        public List<PortBuffers.Batch> geometry = List.of();
        public final ItemStackRenderState item = new ItemStackRenderState();
        public boolean hasItem;
        public final Quaternionf itemRotation = new Quaternionf();
        public Vec3 itemOffset = Vec3.ZERO;
        public float itemScale = 1.0f;
    }

    private final ItemModelResolver items;

    protected FxEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemModelResolver();
        this.shadowRadius = 0.0f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        PortBuffers buffers = new PortBuffers();
        Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        Vec3 pos = entity.getPosition(partial);
        state.hasItem = false;
        build(entity, state, partial, new PoseStack(), buffers, camera.subtract(pos));
        state.geometry = buffers.snapshot();
    }

    /** Records geometry relative to the entity; {@code toCamera} is the camera position relative to it. */
    protected abstract void build(T entity, State state, float partial, PoseStack pose, PortBuffers buffers, Vec3 toCamera);

    protected void setItem(T entity, State state, ItemStack stack, Quaternionf rotation, Vec3 offset, float scale) {
        this.items.updateForNonLiving(state.item, stack, ItemDisplayContext.FIXED, entity);
        state.hasItem = !stack.isEmpty();
        state.itemRotation.set(rotation);
        state.itemOffset = offset;
        state.itemScale = scale;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
        PortBuffers.submit(state.geometry, pose, collector);
        if (state.hasItem) {
            pose.pushPose();
            pose.translate(state.itemOffset.x, state.itemOffset.y, state.itemOffset.z);
            pose.rotate(state.itemRotation);
            pose.scale(state.itemScale, state.itemScale, state.itemScale);
            state.item.submit(pose, collector, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
        }
    }

    /** Big effects are drawn whenever they are within reach, whatever their small bounding box says. */
    @Override
    public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z, float partial) {
        return entity.distanceToSqr(x, y, z) < 300.0 * 300.0;
    }
}
