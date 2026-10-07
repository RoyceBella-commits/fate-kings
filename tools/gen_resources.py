#!/usr/bin/env python3
"""Writes the JSON resources of the mod: item model definitions, models, equipment assets,
sounds.json, damage types, tags and both language files. Run after editing any table below."""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "src" / "main" / "resources"
A = RES / "assets" / "fatekings"
D = RES / "data"
NS = "fatekings"


def write(path: pathlib.Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def model(name):
    return {"type": "minecraft:model", "model": f"{NS}:item/{name}"}


GENERATED = ["gate_of_babylon", "enkidu", "vimana", "treasury_elixir", "grail_mud", "warhorse", "knight_barding",
             "golden_crown", "golden_chestplate", "golden_greaves", "golden_sabatons",
             "knight_ribbon", "knight_breastplate", "knight_skirt", "knight_boots",
             "gilgamesh_spawn_egg", "artoria_spawn_egg"]
HANDHELD = ["bab_ilu", "ea", "ea_charging", "excalibur_air", "excalibur_revealed", "excalibur_release"]


def items():
    for name in GENERATED:
        write(A / "items" / f"{name}.json", {"model": model(name)})
        write(A / "models" / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
    for name in HANDHELD:
        write(A / "models" / "item" / f"{name}.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/{name}"}})
    write(A / "items" / "bab_ilu.json", {"model": model("bab_ilu")})
    write(A / "items" / "ea.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_true": model("ea_charging"), "on_false": model("ea")}})
    # Excalibur: gathering light while in use; otherwise Invisible Air in a knight's hand, revealed elsewhere.
    write(A / "items" / "excalibur.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_true": model("excalibur_release"),
        "on_false": {"type": "minecraft:select", "property": "minecraft:display_context",
                     # In an item frame, on a shelf or on the ground it is always the revealed sword.
                     "cases": [{"when": ["fixed", "ground", "on_shelf"], "model": model("excalibur_revealed")}],
                     "fallback": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                                  "cases": [{"when": "air", "model": model("excalibur_air")}],
                                  "fallback": model("excalibur_revealed")}}}})


def equipment():
    write(A / "equipment" / "golden_regalia.json", {"layers": {
        "humanoid": [{"texture": f"{NS}:golden_regalia"}], "humanoid_leggings": [{"texture": f"{NS}:golden_regalia"}]}})
    write(A / "equipment" / "knight_regalia.json", {"layers": {
        "humanoid": [{"texture": f"{NS}:knight_regalia"}], "humanoid_leggings": [{"texture": f"{NS}:knight_regalia"}]}})
    write(A / "equipment" / "knight_barding.json", {"layers": {"horse_body": [{"texture": f"{NS}:knight_barding"}]}})


VOICES = ["gil_spawn", "gil_arrogant", "gil_displeased", "gil_serious", "gil_volley", "gil_chain", "gil_unlock", "gil_ea_drawn",
          "gil_ea_chant", "gil_ea_release", "gil_laugh", "gil_hurt", "gil_defeat", "gil_defeat_saber", "gil_victory",
          "saber_spawn", "saber_salute", "saber_full_power", "saber_strike_air", "saber_release_call", "saber_excalibur_chant",
          "saber_excalibur_release", "saber_vs_gil", "saber_vs_sukuna", "saber_avalon", "saber_last_stand", "saber_hurt",
          "saber_defeat", "saber_victory"]


def sounds():
    write(A / "sounds.json", {f"voice.{v}": {"category": "voice", "sounds": [{"name": f"{NS}:voice/{v}"}]} for v in VOICES})


DAMAGE = {
    "enuma_elish": {"message_id": f"{NS}.enuma_elish", "scaling": "never", "exhaustion": 0.0, "effects": "hurt"},
    "excalibur": {"message_id": f"{NS}.excalibur", "scaling": "never", "exhaustion": 0.0, "effects": "burning"},
    # On creatures (1000): same death messages, but not in bypasses_armor.
    "enuma_elish_mob": {"message_id": f"{NS}.enuma_elish", "scaling": "never", "exhaustion": 0.0, "effects": "hurt"},
    "excalibur_mob": {"message_id": f"{NS}.excalibur", "scaling": "never", "exhaustion": 0.0, "effects": "burning"},
    "excalibur_judgement": {"message_id": f"{NS}.excalibur_judgement", "scaling": "never", "exhaustion": 0.0, "effects": "burning"},
    "heavens_chain": {"message_id": f"{NS}.heavens_chain", "scaling": "never", "exhaustion": 0.0, "effects": "hurt"},
    "mana_burst": {"message_id": f"{NS}.mana_burst", "scaling": "never", "exhaustion": 0.1, "effects": "hurt"},
    "sword_qi": {"message_id": f"{NS}.sword_qi", "scaling": "never", "exhaustion": 0.0, "effects": "hurt"},
}


def data():
    for name, obj in DAMAGE.items():
        write(D / NS / "damage_type" / f"{name}.json", obj)
    judged = [f"{NS}:excalibur_judgement"]
    for tag in ("bypasses_armor", "bypasses_enchantments", "bypasses_resistance", "bypasses_shield", "bypasses_invulnerability",
                "bypasses_effects", "no_knockback"):
        write(D / "minecraft" / "tags" / "damage_type" / f"{tag}.json", {"replace": False, "values": judged})
    write(D / "minecraft" / "tags" / "damage_type" / "bypasses_armor.json", {"replace": False,
          "values": judged + [f"{NS}:enuma_elish", f"{NS}:excalibur"]})
    write(D / NS / "tags" / "item" / "no_repair.json", {"replace": False, "values": []})
    write(D / NS / "tags" / "entity_type" / "gojo_side.json", {"replace": False, "values": [{"id": "sukuna:gojo", "required": False}]})
    write(D / NS / "tags" / "entity_type" / "sukuna_side.json", {"replace": False, "values": [
        {"id": "sukuna:sukuna", "required": False}, {"id": "sukuna:mahoraga", "required": False}]})


ZH = {
    "itemGroup.fatekings.main": "Fate · 英雄王与骑士王",
    "item.fatekings.golden_crown": "英雄王之冠",
    "item.fatekings.golden_chestplate": "黄金铠甲·胸铠",
    "item.fatekings.golden_greaves": "黄金铠甲·腿铠",
    "item.fatekings.golden_sabatons": "黄金铠甲·战靴",
    "item.fatekings.gate_of_babylon": "王之财宝",
    "item.fatekings.gate_of_babylon.desc": "宝库之门。人世间一切财宝的原典都在其中。",
    "item.fatekings.gate_of_babylon.use": "短按：点射 · 按住：齐射（松开发射）· 潜行+右键：环形包围 · 按下右键时定好方向和目标，多个敌人时轮流射向每个，不追踪",
    "item.fatekings.bab_ilu": "王律键·巴布·伊鲁",
    "item.fatekings.bab_ilu.desc": "开启宝库最深处的钥匙之剑。",
    "item.fatekings.bab_ilu.use": "英雄王按住右键 2 秒：开锁，取出乖离剑 Ea",
    "item.fatekings.ea": "乖离剑·Ea",
    "item.fatekings.ea.desc": "天、地、冥界，三段对转的圆柱。离开主手即消散。",
    "item.fatekings.ea.use": "按住右键 3 秒后松开：天地乖离开辟之星",
    "item.fatekings.enkidu": "天之锁",
    "item.fatekings.enkidu.desc": "以唯一挚友之名命名的锁链，神性越高锁得越紧。",
    "item.fatekings.enkidu.use": "短按：钩拉 · 按住 1 秒：束缚",
    "item.fatekings.vimana": "维摩那",
    "item.fatekings.vimana.desc": "黄金与翡翠打造的飞行王座。",
    "item.fatekings.vimana.use": "右键召唤并乘坐；视线控制方向，前进加速，跳跃上升，潜行下来",
    "item.fatekings.treasury_elixir": "宝库秘药",
    "item.fatekings.treasury_elixir.desc": "3 秒内回复 20 颗红心。冷却 60 秒。",
    "item.fatekings.knight_ribbon": "骑士王的缎带",
    "item.fatekings.knight_breastplate": "骑士王铠甲·胸甲",
    "item.fatekings.knight_skirt": "骑士王铠甲·裙甲",
    "item.fatekings.knight_boots": "骑士王铠甲·铁靴",
    "item.fatekings.excalibur": "誓约胜利之剑 · Excalibur",
    "item.fatekings.excalibur.desc": "星之内海锻造的圣剑。只回应王的召唤。",
    "item.fatekings.excalibur.use": "短按：风王铁锤（之后剑身显现 10 秒，左键释放金色剑气）· 潜行+右键：魔力放出冲锋 · 按住 1.5 秒后松开：真名解放",
    "item.fatekings.warhorse": "骑士王的战马",
    "item.fatekings.warhorse.desc": "召唤披着银蓝马铠的白色战马；再次使用收回。",
    "item.fatekings.knight_barding": "骑士王的马铠",
    "item.fatekings.gilgamesh_spawn_egg": "英雄王·吉尔伽美什刷怪蛋",
    "item.fatekings.artoria_spawn_egg": "骑士王·阿尔托利亚刷怪蛋",
    "item.fatekings.grail_mud": "圣杯之泥",
    "item.fatekings.grail_mud.desc": "管理员道具：一击击败英雄王或骑士王 NPC（单人世界或管理员）。",
    "entity.fatekings.gilgamesh": "吉尔伽美什",
    "entity.fatekings.artoria": "阿尔托利亚",
    "entity.fatekings.treasure": "宝具",
    "entity.fatekings.gate_portal": "王之财宝",
    "entity.fatekings.chain": "天之锁",
    "entity.fatekings.vimana": "维摩那",
    "entity.fatekings.enuma_elish": "天地乖离开辟之星",
    "entity.fatekings.excalibur_wave": "誓约胜利之剑",
    "entity.fatekings.strike_air": "风王铁锤",
    "entity.fatekings.sword_qi": "金色剑气",
    "effect.fatekings.heavens_chain": "天之锁·束缚",
    "effect.fatekings.excalibur_wound": "圣剑之创",
    "effect.fatekings.disbelief": "难以置信",
    "death.attack.fatekings.enuma_elish": "%1$s 被天地乖离开辟之星吞没了",
    "death.attack.fatekings.enuma_elish.player": "%1$s 被 %2$s 的天地乖离开辟之星吞没了",
    "death.attack.fatekings.excalibur": "%1$s 被誓约胜利之剑斩断了",
    "death.attack.fatekings.excalibur.player": "%1$s 被 %2$s 的誓约胜利之剑斩断了",
    "death.attack.fatekings.excalibur_judgement": "%1$s 在星之光中消失，连一个原子都没有留下",
    "death.attack.fatekings.excalibur_judgement.player": "%1$s 在 %2$s 的星之光中消失，连一个原子都没有留下",
    "death.attack.fatekings.heavens_chain": "%1$s 被天之锁勒紧了",
    "death.attack.fatekings.heavens_chain.player": "%1$s 被 %2$s 的天之锁勒紧了",
    "death.attack.fatekings.mana_burst": "%1$s 被魔力放出撞飞了",
    "death.attack.fatekings.mana_burst.player": "%1$s 被 %2$s 的魔力放出撞飞了",
    "death.attack.fatekings.sword_qi": "%1$s 被金色剑气斩断了",
    "death.attack.fatekings.sword_qi.player": "%1$s 被 %2$s 的金色剑气斩断了",
    "fatekings.speaker.gilgamesh": "吉尔伽美什",
    "fatekings.speaker.artoria": "阿尔托利亚",
    "fatekings.title.hero": "英雄王，降临",
    "fatekings.title.knight": "骑士王，应召而来",
    "fatekings.hud.hero": "英雄王 · 吉尔伽美什",
    "fatekings.hud.knight": "骑士王 · 阿尔托利亚",
    "fatekings.hud.ready": "就绪",
    "fatekings.hud.reorg": "宝库整理 %s 秒",
    "fatekings.hud.depletion": "魔力枯竭 %s 秒",
    "fatekings.hud.counter": "理想乡反击 %s 秒",
    "fatekings.hud.dome": "理想乡·完全展开 %s 秒",
    "fatekings.hud.regen_paused": "理想乡之愈暂停 %s 秒",
    "fatekings.hud.swap_lock": "换装冷却 %s 秒",
    "fatekings.hud.target": "生命 %s · 护甲 %s",
    "fatekings.skill.gob_tap": "王之财宝·点射",
    "fatekings.skill.gob_volley": "王之财宝·齐射",
    "fatekings.skill.gob_ring": "王之财宝·环形包围",
    "fatekings.skill.bab_ilu": "王律键（乖离剑）",
    "fatekings.skill.enkidu_hook": "天之锁·钩拉",
    "fatekings.skill.enkidu_bind": "天之锁·束缚",
    "fatekings.skill.vimana": "维摩那",
    "fatekings.skill.elixir": "宝库秘药",
    "fatekings.skill.autodefender": "自动防御宝具",
    "fatekings.skill.strike_air": "风王铁锤",
    "fatekings.skill.mana_burst": "魔力放出·冲锋",
    "fatekings.skill.excalibur": "誓约胜利之剑",
    "fatekings.skill.warhorse": "骑士王的战马",
    "fatekings.skill.avalon_lethal": "理想乡·不流之血",
    "fatekings.skill.avalon_dome": "理想乡·完全展开",
    "fatekings.side.vanilla": "原版生物",
    "fatekings.side.vanilla_boss": "原版首领",
    "fatekings.side.player": "普通玩家",
    "fatekings.side.gojo": "五条悟一方",
    "fatekings.side.sukuna": "宿傩一方",
    "fatekings.side.mahoraga": "魔虚罗",
    "fatekings.side.hero": "英雄王",
    "fatekings.side.knight": "骑士王",
    "fatekings.side.other_mod": "其他",
    "fatekings.hint.swap_lock": "换装冷却中：%s 秒后才能成为另一位王",
    "fatekings.hint.treasury_closed": "宝库不会为凡人开启",
    "fatekings.hint.treasury_reorg": "宝库整理中（%s 秒）",
    "fatekings.hint.cooldown": "%s 冷却中：%s 秒",
    "fatekings.hint.no_target": "准星处没有目标",
    "fatekings.hint.chained": "被天之锁束缚，无法行动",
    "fatekings.hint.air_used": "空中的冲刺已经用过了",
    "fatekings.hint.sword_answers_king": "圣剑只回应王的召唤",
    "fatekings.hint.depleted": "魔力枯竭（%s 秒）",
    "fatekings.hint.two_hands": "骑乘时无法双手解放圣剑",
    "fatekings.hint.interrupted": "蓄力被打断了！",
    "fatekings.hint.wheel_cannot_turn": "法轮来不及转动",
    "fatekings.hint.infinity_ea": "无下限被「乖离剑」撕裂",
    "fatekings.hint.infinity_starlight": "无下限被「星之光」贯穿",
    "fatekings.hint.admin_only": "仅限单人世界或管理员使用",
    "fatekings.warn.bab_ilu": "直感：王律键开锁（乖离剑）",
    "fatekings.warn.ea": "直感：天地乖离开辟之星",
    "fatekings.warn.excalibur": "直感：誓约胜利之剑",
    "fatekings.warn.world_cut": "直感：世界斩",
    "fatekings.warn.murasaki": "直感：虚式·茈",
    "fatekings.warn.shrine": "直感：领域展开·伏魔御厨子",
    "fatekings.warn.void": "直感：领域展开·无量空处",
    "fatekings.cmd.terrain.on": "Fate 地形破坏：开启",
    "fatekings.cmd.terrain.off": "Fate 地形破坏：关闭",
    "fatekings.cmd.cooldowns_reset": "已重置 %s 名玩家的冷却",
    "fatekings.cmd.cast_failed": "无法施放：%s",
    "fatekings.cmd.client_option": "Fate 客户端选项 %s = %s",
    "tag.item.fatekings.no_repair": "无法修复",
    "gamerule.fatekings.excalibur_boss_instakill": "誓约胜利之剑秒杀原版首领",
    "gamerule.fatekings.knight_spares_pets": "誓约胜利之剑不伤发动者的宠物",
    "gamerule.fatekings.keep_regalia": "死亡时保留两套王之铠甲",
    "gamerule.fatekings:excalibur_boss_instakill": "誓约胜利之剑秒杀原版首领",
    "gamerule.fatekings:knight_spares_pets": "誓约胜利之剑不伤发动者的宠物",
    "gamerule.fatekings:keep_regalia": "死亡时保留两套王之铠甲",
    # Voice subtitles (Chinese lines from fgo.wiki; the two signature lines by the clip content).
    "fatekings.voice.gil_spawn": "哈哈哈哈哈哈！居然胆敢召唤本王，你的好运也就到此为止了，杂种！",
    "fatekings.voice.gil_arrogant": "区区杂种，还真能吠叫呢。",
    "fatekings.voice.gil_displeased": "你就好好地垂死挣扎吧！",
    "fatekings.voice.gil_serious": "来兴致了！本王要亲手干掉你！",
    "fatekings.voice.gil_volley": "试试看能不能接下吧！",
    "fatekings.voice.gil_chain": "给你上脚镣！",
    "fatekings.voice.gil_unlock": "打开宝物库的门锁吧。",
    "fatekings.voice.gil_ea_drawn": "轮到你出场了。醒来吧，EA。",
    "fatekings.voice.gil_ea_chant": "以吾之乖离剑，撕裂世界——",
    "fatekings.voice.gil_ea_release": "天地乖离——开辟之星！",
    "fatekings.voice.gil_laugh": "呼哈哈哈哈！",
    "fatekings.voice.gil_hurt": "该死该死该死该死……！",
    "fatekings.voice.gil_defeat": "居然被区区杂种给……！",
    "fatekings.voice.gil_defeat_saber": "……唔。有些东西正因为得不到才显得格外美丽。",
    "fatekings.voice.gil_victory": "不高傲无以为王！",
    "fatekings.voice.gil_proposal": "Saber，成为本王的妻子吧。",
    "fatekings.voice.gil_saber_name": "……Saber——！",
    "fatekings.voice.gil_second_ea": "竟然让本王第二次拔出 Ea……！",
    "fatekings.voice.gil_kill_mahoraga": "所谓适应，不过是弱者的挣扎。",
    "fatekings.voice.gil_disdain": "杂种，连武器都不拿，也配站在本王面前？滚吧。",
    "fatekings.voice.saber_spawn": "试问。你是我的御主吗？",
    "fatekings.voice.saber_salute": "拼尽所有实力放马过来吧。",
    "fatekings.voice.saber_full_power": "让你见识我的全力……！",
    "fatekings.voice.saber_strike_air": "风王铁锤！",
    "fatekings.voice.saber_release_call": "圣剑，解放——",
    "fatekings.voice.saber_excalibur_chant": "集结的星之吐息，闪耀的生命奔流——",
    "fatekings.voice.saber_excalibur_release": "誓约胜利之剑——！",
    "fatekings.voice.saber_vs_gil": "嗯，让我们分个胜负吧——",
    "fatekings.voice.saber_vs_sukuna": "决不放过……！",
    "fatekings.voice.saber_avalon": "还差得远……！",
    "fatekings.voice.saber_last_stand": "为这条道路带来胜利！",
    "fatekings.voice.saber_hurt": "区区这种水平……！",
    "fatekings.voice.saber_defeat": "居然……在这种地方……",
    "fatekings.voice.saber_victory": "不违背骑士的誓言。",
    "fatekings.voice.saber_reply": "我首先是王。这一点永远不会改变。",
    "fatekings.voice.saber_crippled_gojo": "胜负已分。我不会对无力再战的人挥剑。",
    "fatekings.voice.saber_kill_mahoraga": "无论你适应什么，都适应不了星之光。",
}

EN = {
    "itemGroup.fatekings.main": "Fate · King of Heroes & King of Knights",
    "item.fatekings.golden_crown": "Crown of the King of Heroes",
    "item.fatekings.golden_chestplate": "Golden Armor Cuirass",
    "item.fatekings.golden_greaves": "Golden Armor Greaves",
    "item.fatekings.golden_sabatons": "Golden Armor Sabatons",
    "item.fatekings.gate_of_babylon": "Gate of Babylon",
    "item.fatekings.gate_of_babylon.desc": "The treasury holding the originals of every treasure in the world.",
    "item.fatekings.gate_of_babylon.use": "Tap: shots · Hold: volley on release · Sneak: ring around the target · Aimed when pressed; several foes are taken in turn; no homing",
    "item.fatekings.bab_ilu": "Bab-ilu, Key of the King's Law",
    "item.fatekings.bab_ilu.desc": "The key sword to the deepest vault.",
    "item.fatekings.bab_ilu.use": "King of Heroes, hold 2 s: unlock and draw Ea",
    "item.fatekings.ea": "Ea, Sword of Rupture",
    "item.fatekings.ea.desc": "Heaven, earth and underworld turning against each other. Gone once out of the main hand.",
    "item.fatekings.ea.use": "Hold 3 s and release: Enuma Elish",
    "item.fatekings.enkidu": "Enkidu, Chains of Heaven",
    "item.fatekings.enkidu.desc": "The higher the divinity, the tighter it binds.",
    "item.fatekings.enkidu.use": "Tap: hook · Hold 1 s: bind",
    "item.fatekings.vimana": "Vimana",
    "item.fatekings.vimana.desc": "A flying throne of gold and emerald.",
    "item.fatekings.vimana.use": "Summon and ride; steer with your view, forward to speed up, jump to climb, sneak to get off",
    "item.fatekings.treasury_elixir": "Treasury Elixir",
    "item.fatekings.treasury_elixir.desc": "Restores 20 hearts over 3 s. 60 s cooldown.",
    "item.fatekings.knight_ribbon": "Ribbon of the King of Knights",
    "item.fatekings.knight_breastplate": "Knight King's Breastplate",
    "item.fatekings.knight_skirt": "Knight King's Skirt Armor",
    "item.fatekings.knight_boots": "Knight King's Sabatons",
    "item.fatekings.excalibur": "Excalibur, Sword of Promised Victory",
    "item.fatekings.excalibur.desc": "A holy sword forged in the planet's inner sea. It answers only the king.",
    "item.fatekings.excalibur.use": "Tap: Strike Air (blade revealed 10 s: swings loose golden crescents) · Sneak: Mana Burst charge · Hold 1.5 s and release: true name",
    "item.fatekings.warhorse": "Warhorse of the King of Knights",
    "item.fatekings.warhorse.desc": "Summons a white warhorse in silver-blue barding; use again to dismiss it.",
    "item.fatekings.knight_barding": "Knight King's Barding",
    "item.fatekings.gilgamesh_spawn_egg": "Gilgamesh Spawn Egg",
    "item.fatekings.artoria_spawn_egg": "Artoria Spawn Egg",
    "item.fatekings.grail_mud": "Mud of the Holy Grail",
    "item.fatekings.grail_mud.desc": "Admin item: defeats the Gilgamesh or Artoria NPC at once (single player or operators).",
    "entity.fatekings.gilgamesh": "Gilgamesh",
    "entity.fatekings.artoria": "Artoria",
    "entity.fatekings.treasure": "Noble Phantasm",
    "entity.fatekings.gate_portal": "Gate of Babylon",
    "entity.fatekings.chain": "Enkidu",
    "entity.fatekings.vimana": "Vimana",
    "entity.fatekings.enuma_elish": "Enuma Elish",
    "entity.fatekings.excalibur_wave": "Excalibur",
    "entity.fatekings.strike_air": "Strike Air",
    "entity.fatekings.sword_qi": "Golden Sword Light",
    "effect.fatekings.heavens_chain": "Chains of Heaven",
    "effect.fatekings.excalibur_wound": "Wound of the Holy Sword",
    "effect.fatekings.disbelief": "Disbelief",
    "death.attack.fatekings.enuma_elish": "%1$s was swallowed by Enuma Elish",
    "death.attack.fatekings.enuma_elish.player": "%1$s was swallowed by %2$s's Enuma Elish",
    "death.attack.fatekings.excalibur": "%1$s was cut down by Excalibur",
    "death.attack.fatekings.excalibur.player": "%1$s was cut down by %2$s's Excalibur",
    "death.attack.fatekings.excalibur_judgement": "%1$s vanished in the light of the stars, not an atom left",
    "death.attack.fatekings.excalibur_judgement.player": "%1$s vanished in %2$s's light of the stars, not an atom left",
    "death.attack.fatekings.heavens_chain": "%1$s was crushed by the Chains of Heaven",
    "death.attack.fatekings.heavens_chain.player": "%1$s was crushed by %2$s's Chains of Heaven",
    "death.attack.fatekings.mana_burst": "%1$s was blown away by a Mana Burst",
    "death.attack.fatekings.mana_burst.player": "%1$s was blown away by %2$s's Mana Burst",
    "death.attack.fatekings.sword_qi": "%1$s was cut by a crescent of golden light",
    "death.attack.fatekings.sword_qi.player": "%1$s was cut by %2$s's crescent of golden light",
    "fatekings.speaker.gilgamesh": "Gilgamesh",
    "fatekings.speaker.artoria": "Artoria",
    "fatekings.title.hero": "The King of Heroes descends",
    "fatekings.title.knight": "The King of Knights answers the call",
    "fatekings.hud.hero": "King of Heroes · Gilgamesh",
    "fatekings.hud.knight": "King of Knights · Artoria",
    "fatekings.hud.ready": "Ready",
    "fatekings.hud.reorg": "Treasury reorganising %s s",
    "fatekings.hud.depletion": "Mana depleted %s s",
    "fatekings.hud.counter": "Avalon counter %s s",
    "fatekings.hud.dome": "Avalon unfolded %s s",
    "fatekings.hud.regen_paused": "Avalon healing paused %s s",
    "fatekings.hud.swap_lock": "Armor swap cooldown %s s",
    "fatekings.hud.target": "Health %s · Armor %s",
    "fatekings.skill.gob_tap": "Gate of Babylon · Shots",
    "fatekings.skill.gob_volley": "Gate of Babylon · Volley",
    "fatekings.skill.gob_ring": "Gate of Babylon · Ring",
    "fatekings.skill.bab_ilu": "Bab-ilu (Ea)",
    "fatekings.skill.enkidu_hook": "Enkidu · Hook",
    "fatekings.skill.enkidu_bind": "Enkidu · Bind",
    "fatekings.skill.vimana": "Vimana",
    "fatekings.skill.elixir": "Treasury Elixir",
    "fatekings.skill.autodefender": "Autodefender",
    "fatekings.skill.strike_air": "Strike Air",
    "fatekings.skill.mana_burst": "Mana Burst · Charge",
    "fatekings.skill.excalibur": "Excalibur",
    "fatekings.skill.warhorse": "Warhorse",
    "fatekings.skill.avalon_lethal": "Avalon · No Blood Shed",
    "fatekings.skill.avalon_dome": "Avalon · Full Unfolding",
    "fatekings.side.vanilla": "Vanilla creature",
    "fatekings.side.vanilla_boss": "Vanilla boss",
    "fatekings.side.player": "Plain player",
    "fatekings.side.gojo": "Gojo's side",
    "fatekings.side.sukuna": "Sukuna's side",
    "fatekings.side.mahoraga": "Mahoraga",
    "fatekings.side.hero": "King of Heroes",
    "fatekings.side.knight": "King of Knights",
    "fatekings.side.other_mod": "Other",
    "fatekings.hint.swap_lock": "Armor swap cooldown: %s s before becoming the other king",
    "fatekings.hint.treasury_closed": "The treasury does not open for commoners",
    "fatekings.hint.treasury_reorg": "Treasury reorganising (%s s)",
    "fatekings.hint.cooldown": "%s on cooldown: %s s",
    "fatekings.hint.no_target": "No target under the crosshair",
    "fatekings.hint.chained": "Bound by the Chains of Heaven",
    "fatekings.hint.air_used": "The mid-air charge is already spent",
    "fatekings.hint.sword_answers_king": "The holy sword answers only the king",
    "fatekings.hint.depleted": "Mana depleted (%s s)",
    "fatekings.hint.two_hands": "Both hands are needed for the true name; not while riding",
    "fatekings.hint.interrupted": "The charge was broken!",
    "fatekings.hint.wheel_cannot_turn": "The wheel had no time to turn",
    "fatekings.hint.infinity_ea": "Infinity torn apart by the Sword of Rupture",
    "fatekings.hint.infinity_starlight": "Infinity pierced by the light of the stars",
    "fatekings.hint.admin_only": "Single player or operators only",
    "fatekings.warn.bab_ilu": "Instinct: Bab-ilu unlocking (Ea)",
    "fatekings.warn.ea": "Instinct: Enuma Elish",
    "fatekings.warn.excalibur": "Instinct: Excalibur",
    "fatekings.warn.world_cut": "Instinct: World Cut",
    "fatekings.warn.murasaki": "Instinct: Hollow Purple",
    "fatekings.warn.shrine": "Instinct: Domain Expansion, Malevolent Shrine",
    "fatekings.warn.void": "Instinct: Domain Expansion, Unlimited Void",
    "fatekings.cmd.terrain.on": "Fate terrain destruction: on",
    "fatekings.cmd.terrain.off": "Fate terrain destruction: off",
    "fatekings.cmd.cooldowns_reset": "Cooldowns reset for %s player(s)",
    "fatekings.cmd.cast_failed": "Cannot cast: %s",
    "fatekings.cmd.client_option": "Fate client option %s = %s",
    "tag.item.fatekings.no_repair": "Cannot be repaired",
    "gamerule.fatekings.excalibur_boss_instakill": "Excalibur kills vanilla bosses outright",
    "gamerule.fatekings.knight_spares_pets": "Excalibur spares the caster's pets",
    "gamerule.fatekings.keep_regalia": "Keep the kings' armor on death",
    "gamerule.fatekings:excalibur_boss_instakill": "Excalibur kills vanilla bosses outright",
    "gamerule.fatekings:knight_spares_pets": "Excalibur spares the caster's pets",
    "gamerule.fatekings:keep_regalia": "Keep the kings' armor on death",
    "fatekings.voice.gil_spawn": "Hahahahaha! Summoning me? Your luck ends here, mongrel!",
    "fatekings.voice.gil_arrogant": "A mere mongrel, yet how it barks.",
    "fatekings.voice.gil_displeased": "Struggle all you like!",
    "fatekings.voice.gil_serious": "Now I am interested! I will finish you with my own hands!",
    "fatekings.voice.gil_volley": "See if you can take it all!",
    "fatekings.voice.gil_chain": "Take these shackles!",
    "fatekings.voice.gil_unlock": "Let me unlock the treasury.",
    "fatekings.voice.gil_ea_drawn": "Your turn. Awaken, Ea.",
    "fatekings.voice.gil_ea_chant": "With my Sword of Rupture I split the world—",
    "fatekings.voice.gil_ea_release": "Enuma— Elish!",
    "fatekings.voice.gil_laugh": "Fuhahahaha!",
    "fatekings.voice.gil_hurt": "Curse you, curse you, curse you!",
    "fatekings.voice.gil_defeat": "By a mere mongrel...!",
    "fatekings.voice.gil_defeat_saber": "...Hm. Some things are beautiful precisely because they cannot be had.",
    "fatekings.voice.gil_victory": "What king is not arrogant!",
    "fatekings.voice.gil_proposal": "Saber, become my wife.",
    "fatekings.voice.gil_saber_name": "...Saber—!",
    "fatekings.voice.gil_second_ea": "To make me draw Ea a second time...!",
    "fatekings.voice.gil_kill_mahoraga": "Adaptation is but the struggle of the weak.",
    "fatekings.voice.gil_disdain": "Not even a weapon, mongrel, and you stand before the king? Begone.",
    "fatekings.voice.saber_spawn": "I ask of you. Are you my Master?",
    "fatekings.voice.saber_salute": "Come at me with everything you have.",
    "fatekings.voice.saber_full_power": "I will show you my full strength!",
    "fatekings.voice.saber_strike_air": "Strike Air!",
    "fatekings.voice.saber_release_call": "Holy sword, release—",
    "fatekings.voice.saber_excalibur_chant": "Breath of the stars gathered, torrent of shining life—",
    "fatekings.voice.saber_excalibur_release": "Ex—calibur!",
    "fatekings.voice.saber_vs_gil": "Yes, let us settle this—",
    "fatekings.voice.saber_vs_sukuna": "You will not escape!",
    "fatekings.voice.saber_avalon": "Not yet!",
    "fatekings.voice.saber_last_stand": "Victory to this road!",
    "fatekings.voice.saber_hurt": "Is that all?!",
    "fatekings.voice.saber_defeat": "In a place like this...",
    "fatekings.voice.saber_victory": "A knight's oath is not broken.",
    "fatekings.voice.saber_reply": "I am a king first. That will never change.",
    "fatekings.voice.saber_crippled_gojo": "It is decided. I do not raise my sword against one who cannot fight.",
    "fatekings.voice.saber_kill_mahoraga": "Whatever you adapt to, you cannot adapt to the light of the stars.",
}


def lang():
    missing = set(ZH) ^ set(EN)
    if missing:
        raise SystemExit(f"lang keys differ: {sorted(missing)}")
    write(A / "lang" / "zh_cn.json", ZH)
    write(A / "lang" / "en_us.json", EN)


if __name__ == "__main__":
    items()
    equipment()
    sounds()
    data()
    lang()
    print("resources written")
