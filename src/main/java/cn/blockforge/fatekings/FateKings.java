package cn.blockforge.fatekings;

import cn.blockforge.fatekings.clash.NoblePhantasmClash;
import cn.blockforge.fatekings.combat.DamageHooks;
import cn.blockforge.fatekings.combat.Judgement;
import cn.blockforge.fatekings.combat.Terrain;
import cn.blockforge.fatekings.config.FateCommands;
import cn.blockforge.fatekings.config.FateConfig;
import cn.blockforge.fatekings.hero.Enkidu;
import cn.blockforge.fatekings.hero.GateOfBabylon;
import cn.blockforge.fatekings.hero.HeroPassives;
import cn.blockforge.fatekings.king.Kings;
import cn.blockforge.fatekings.king.Regalia;
import cn.blockforge.fatekings.knight.ExcaliburSkill;
import cn.blockforge.fatekings.knight.Instinct;
import cn.blockforge.fatekings.knight.KnightLeap;
import cn.blockforge.fatekings.knight.WarhorseItem;
import cn.blockforge.fatekings.net.FateNet;
import cn.blockforge.fatekings.npc.ArtoriaEntity;
import cn.blockforge.fatekings.registry.FateEffects;
import cn.blockforge.fatekings.registry.FateEntities;
import cn.blockforge.fatekings.registry.FateItems;
import cn.blockforge.fatekings.registry.FateRules;
import cn.blockforge.fatekings.registry.FateSounds;
import cn.blockforge.fatekings.voice.VoicePlayer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

public final class FateKings implements ModInitializer {
    public static final String MOD_ID = "fatekings";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        FateConfig.load();
        FateSounds.init();
        FateEffects.init();
        FateEntities.init();
        FateItems.init();
        FateRules.init();
        Kings.STATE.identifier();
        FateNet.registerTypes();
        FateNet.receive(FateNet.C2S_LEAP, (server, player, buf) -> KnightLeap.handle(player, buf.readFloat(), buf.readFloat(), buf.readBoolean()));
        FateNet.receive(FateNet.C2S_SLASH, (server, player, buf) -> cn.blockforge.fatekings.knight.Slashes.handle(player, buf.readFloat(), buf.readByte()));
        FateNet.receive(FateNet.C2S_TWIN, (server, player, buf) -> cn.blockforge.fatekings.archer.TwinBlades.handle(player, buf.readByte()));
        FateNet.receive(FateNet.C2S_TRIPLE, (server, player, buf) -> cn.blockforge.fatekings.archer.ArcherBow.triple(player));
        FateNet.receive(FateNet.C2S_PROJECT, (server, player, buf) -> cn.blockforge.fatekings.archer.UnlimitedBladeWorks.fromScreen(player, buf.readVarInt(), buf.readByte()));
        cn.blockforge.fatekings.archer.ProjectionGuards.register();
        DamageHooks.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> FateCommands.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Kings.tick(p);
                KnightLeap.tick(p);
                ExcaliburSkill.tickDash(p.level(), p);
                if (p.onGround()) Kings.of(p).airDashUsed = false;
                WarhorseItem.tickRider(p);
            }
            for (ServerLevel level : server.getAllLevels()) {
                Enkidu.tick(level);
                NoblePhantasmClash.tick(level);
                cn.blockforge.fatekings.archer.RhoAias.tick(level);
                if (server.getTickCount() % 20 == 0) {
                    for (Entity e : level.getAllEntities()) {
                        if (e instanceof AbstractHorse horse && WarhorseItem.isWarhorse(horse)) WarhorseItem.tickHorse(level, horse);
                    }
                }
            }
            Instinct.tickJjk(server);
            cn.blockforge.fatekings.archer.TwinBlades.tick(server);
            cn.blockforge.fatekings.archer.CraneWing.tick(server);
            Terrain.tick(server);
        });
        ServerEntityEvents.ENTITY_LOAD.register(Instinct::onEntityLoad);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) Kings.onRespawn(newPlayer);
        });
        ServerPlayerEvents.COPY_FROM.register(Regalia::restore);
        ServerPlayerEvents.LEAVE.register(p -> {
            GateOfBabylon.cancelVolley(p);
            HeroPassives.forget(p);
            cn.blockforge.fatekings.archer.Archer.leave(p);
        });
        // Riding A: the King of Knights mounts an untamed horse and it simply obeys.
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!level.isClientSide() && entity instanceof AbstractHorse horse && !horse.isTamed() && !horse.isBaby()
                && Kings.isKnight(player) && !player.isSecondaryUseActive()) {
                horse.tameWithName(player);
            }
            return InteractionResult.PASS;
        });
        // Artoria and EMIYA protect villagers, iron golems and players near them (never turning on each other).
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
            if (!(victim instanceof AbstractVillager || victim instanceof IronGolem || victim instanceof Player)) return;
            if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == victim
                || attacker instanceof ArtoriaEntity || attacker instanceof cn.blockforge.fatekings.npc.EmiyaEntity) return;
            if (attacker instanceof Player p && (p.isCreative() || p.isSpectator())) return;
            for (ArtoriaEntity a : victim.level().getEntitiesOfClass(ArtoriaEntity.class, victim.getBoundingBox().inflate(16.0))) {
                if (a.getTarget() == null && a != attacker) a.setTarget(attacker);
            }
            for (var e : victim.level().getEntitiesOfClass(cn.blockforge.fatekings.npc.EmiyaEntity.class, victim.getBoundingBox().inflate(24.0))) {
                if (e.getTarget() == null && e != attacker) e.setTarget(attacker);
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> clearTransient());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearTransient());
    }

    private static void clearTransient() {
        Terrain.clear();
        VoicePlayer.clear();
        Judgement.clear();
        GateOfBabylon.clear();
        Enkidu.clear();
        HeroPassives.clear();
        KnightLeap.clear();
        cn.blockforge.fatekings.knight.Slashes.clear();
        ExcaliburSkill.clear();
        Instinct.clear();
        WarhorseItem.clear();
        Regalia.clear();
        NoblePhantasmClash.clear();
        cn.blockforge.fatekings.archer.Archer.clear();
        cn.blockforge.fatekings.compat.Restraint.clear();
    }
}
