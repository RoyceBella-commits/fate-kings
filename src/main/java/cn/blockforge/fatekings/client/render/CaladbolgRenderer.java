package cn.blockforge.fatekings.client.render;

import cn.blockforge.fatekings.FateKings;
import cn.blockforge.fatekings.archer.ArcherRules;
import cn.blockforge.fatekings.client.FateClient;
import cn.blockforge.fatekings.entity.BeamEntity;
import cn.blockforge.fatekings.entity.CaladbolgEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Caladbolg II: the twisted drill-sword at the front, two spirals of red and white winding behind
 * it, rings of shocked air every few blocks; at the end, the burst of the Broken Phantasm.
 */
public class CaladbolgRenderer extends FxEntityRenderer<CaladbolgEntity> {
    private static final Vector3f BLADE = new Vector3f(1.0f, 1.0f, 0.0f).normalize();
    private static final ItemStack DRILL = drill();

    public CaladbolgRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static ItemStack drill() {
        ItemStack s = new ItemStack(Items.ARROW);
        s.set(DataComponents.ITEM_MODEL, FateKings.id("caladbolg_arrow"));
        return s;
    }

    @Override
    protected void build(CaladbolgEntity e, State state, float partial, PoseStack pose, PortBuffers b, Vec3 toCamera) {
        boolean low = FateClient.prefs().lowFx();
        Vec3 dir = e.dir();
        float front = e.front() + (e.state() == BeamEntity.ADVANCING ? (float)ArcherRules.CALADBOLG_SPEED * partial : 0.0f);
        Vec3 end = dir.scale(front);
        float t = e.tickCount + partial;
        if (e.state() == BeamEntity.ENDING) {
            float age = e.stateAge(partial);
            float fade = Math.max(0.0f, 1.0f - age / 24.0f);
            FxDraw.sphere(pose, b, end, 1.5f + age * 0.5f, 8, 12, FxDraw.alpha(0.55f * fade, 0xFF5A3A));
            FxDraw.sphere(pose, b, end, 0.8f + age * 0.3f, 6, 10, FxDraw.alpha(0.7f * fade, 0xFFF0E8));
            return;
        }
        Quaternionf rot = new Quaternionf().rotationTo(BLADE, new Vector3f((float)dir.x, (float)dir.y, (float)dir.z));
        rot.rotateAxis(t * 0.9f, (float)dir.x, (float)dir.y, (float)dir.z);
        setItem(e, state, DRILL, rot, end, 2.0f);
        Vec3 back = end.subtract(dir.scale(Math.min(front, 24.0)));
        FxDraw.tube(pose, b, back, end, 0.15f, 0.9f, low ? 6 : 10, FxDraw.argb(0, 0xE8302A), FxDraw.argb(200, 0xE8302A), 6.0f + t * 0.3f);
        if (!low) FxDraw.tube(pose, b, back, end, 0.1f, 0.6f, 8, FxDraw.argb(0, 0xFFF0E8), FxDraw.argb(180, 0xFFF0E8), -6.0f - t * 0.3f);
        double ringStep = low ? 16.0 : 8.0;
        for (double d = Math.max(0.0, front - 48.0); d < front; d += ringStep) {
            double phase = (d % ringStep) / ringStep;
            Vec3 c = dir.scale(d - (t * 2.0 % ringStep));
            if (c.dot(dir) < 0.0) continue;
            float age = (float)((front - d) / 48.0);
            FxDraw.ring(pose, b, c, dir, 0.8f + age * 2.5f, 1.0f + age * 2.6f, 20, FxDraw.argb((int)(150 * (1.0f - age)), 0xFFD0C0),
                FxDraw.argb(0, 0xFFD0C0));
        }
    }
}
