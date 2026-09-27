package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.client.KingPoses;
import cn.blockforge.fatekings.npc.KingNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Player-shaped renderer with a fixed skin for the two kings (Artoria uses the slim arms). */
public class KingNpcRenderer<T extends KingNpcEntity> extends HumanoidMobRenderer<T, KingNpcRenderer.State, KingNpcRenderer.Model> {
    public static class State extends HumanoidRenderState {
        public int pose;
        public int entityId = -1;
    }

    public static class Model extends HumanoidModel<State> {
        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
            KingPoses.apply(state.pose, this.head, this.leftArm, this.rightArm);
        }
    }

    private final Identifier texture;

    public KingNpcRenderer(EntityRendererProvider.Context ctx, ModelLayerLocation layer, Identifier texture) {
        super(ctx, new Model(ctx.bakeLayer(layer)), 0.5f);
        this.texture = texture;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.pose = KingPoses.of(entity);
        state.entityId = entity.getId();
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return this.texture;
    }
}
