package tmi.recipe

import arc.struct.ObjectMap
import mindustry.type.Item

/**
 * 弹药修正配置管理器
 * 存储各种弹药对炮台的修正系数
 * 
 * 注意：这些修正系数基于 Mindustry 游戏实际数据和官方 API
 * 不同弹药类型会影响炮台的装填时间、射程、伤害等属性
 * 
 * 官方 API文档参考：
 * https://mindustrygame.github.io/docs/mindustry/entities/bullet/BulletType.html
 */
object AmmoModifierConfig {
  
  /**
   * 弹药特殊效果类型
   * 基于官方 API 的 BulletType  subclasses 和 StatusEffect
   */
  enum class AmmoSpecialEffect {
    NONE,           // 无特殊效果
    EXPLOSION,      // 爆炸 (ExplosionBulletType)
    FIRE,           // 燃烧 (FireBulletType)
    LIGHTNING,      // 闪电/连锁 (LightningBulletType)
    SAP,            // 能量吸取 (SapBulletType)
    SHRAPNEL,       // 弹片散射 (ShrapnelBulletType)
    LIQUID,         // 液体喷洒 (LiquidBulletType)
    LASER,          // 持续激光 (LaserBulletType/ContinuousLaserBulletType)
    FLAME,          // 持续火焰 (ContinuousFlameBulletType)
    MISSILE,        // 导弹追踪 (MissileBulletType)
    ARTILLERY,      // 抛物线火炮 (ArtilleryBulletType)
    BOMB,           // 定时炸弹 (BombBulletType)
    EMP,            // 电磁脉冲 (EmpBulletType)
    INTERCEPTOR,    // 拦截导弹 (InterceptorBulletType)
    RAIL,           // 轨道炮 (RailBulletType)
    FRAG,           // 分裂子弹 (MultiBulletType - 分裂)
    PIERCE,         // 高穿透 (BasicBulletType with high pierce)
    HOMING,         // 强追踪 (BasicBulletType with high homing)
    SLOW,           // 减速效果 (Status: Slowed)
    FREEZE,         // 冰冻效果 (Status: Frozen)
    MELTING,        // 熔化效果 (Status: Melting)
    BLASTING,       // 爆破效果 (Status: Blasting)
    ELECTRIC,       // 触电效果 (Status: Electrified)
    DISARMED,       // 禁用武器 (Status: Disarmed)
    OVERCLOCK,      // 过载加速 (Status: Overclock)
    SHIELDED,       // 护盾保护 (Status: Shielded)
    ARMOR_BREAK,    // 破甲效果 (armorMultiplier < 1)
    ARMOR_PIERCE,   // 穿甲效果 (armorMultiplier > 1)
    HEAL_SUPPRESS   // 治疗压制 (suppressionDuration > 0)
  }
  
  /**
   * 弹药修正数据类
   * 基于官方 API BulletType 的字段设计
   * https://mindustrygame.github.io/docs/mindustry/entities/bullet/BulletType.html
   */
  data class AmmoModifiers(
    val reloadTime: Float = 1f,          // 装填时间倍率（对应 reloadMultiplier）
    val rangeOffset: Float = 0f,         // 射程偏移（像素），对应 rangeChange + extraRangeMargin
    val damageMultiplier: Float = 1f,    // 伤害倍率，对应 buildingDamageMultiplier
    val speedMultiplier: Float = 1f,     // 速度倍率
    val knockback: Float = 0f,           // 击退值（对应 knockback）
    val homingPower: Float = 0f,         // 追踪能力强度（对应 homingPower，0-1 之间）
    val pierce: Boolean = false,         // 是否穿透单位（对应 pierce）
    val pierceBuilding: Boolean = false, // 是否穿透建筑（对应 pierceBuilding）
    val splashDamage: Float = 0f,        // 溅射伤害（对应 splashDamage）
    val splashDamageRadius: Float = 0f,  // 溅射半径（对应 splashDamageRadius）
    val statusEffect: AmmoSpecialEffect = AmmoSpecialEffect.NONE,  // 主要状态效果
    val secondaryEffects: List<AmmoSpecialEffect> = emptyList(), // 次要效果列表
    val armorMultiplier: Float = 1f,     // 护甲修正倍率
    val suppressionDuration: Float = 0f  // 治疗压制持续时间（60 帧=1 秒）
  )
  
  /**
   * 弹药修正映射表
   * Key: 弹药物品
   * Value: 修正系数
   */
  private val ammoModifiers = ObjectMap<String, AmmoModifiers>()
  
  /**
   * 初始化所有弹药的修正系数
   * 基于 Mindustry 实际游戏数据
   */
  fun init() {
    // === 基础弹药（无修正）===
    registerModifier("copper", AmmoModifiers(
      reloadTime = 1f,
      rangeOffset = 0f,
      damageMultiplier = 1f,
      speedMultiplier = 1f,
      knockback = 0f
    ))
    
    registerModifier("lead", AmmoModifiers(
      reloadTime = 1f,
      rangeOffset = 0f,
      damageMultiplier = 1f,
      speedMultiplier = 1f,
      knockback = 0f
    ))
    
    // === 石墨弹药 ===
    // 效果：射程 +4 格（+32 像素），装填时间×4，-20% 开火速率，击退 +1
    registerModifier("graphite", AmmoModifiers(
      reloadTime = 4f,      // 装填时间×4
      rangeOffset = 32f,    // 射程 +4 格（32 像素）
      damageMultiplier = 1f,
      speedMultiplier = 1f,
      knockback = 1f        // 击退 +1
    ))
    
    // === 硅制弹药 ===
    registerModifier("silicon", AmmoModifiers(
      reloadTime = 1.5f,    // 装填稍慢
      rangeOffset = 16f,    // 射程 +2 格
      damageMultiplier = 1.2f, // 伤害 +20%
      speedMultiplier = 1.1f,  // 速度 +10%
      knockback = 0.5f
    ))
    
    // === 钍增强弹药 ===
    registerModifier("thorium", AmmoModifiers(
      reloadTime = 0.5f,    // 装填快 2 倍
      rangeOffset = 8f,     // 射程 +1 格
      damageMultiplier = 1.5f, // 伤害 +50%
      speedMultiplier = 1.2f,  // 速度 +20%
      knockback = 2f
    ))
    
    // === 爆炸性弹药 ===
    registerModifier("explosive", AmmoModifiers(
      reloadTime = 2f,      // 装填慢
      rangeOffset = 0f,
      damageMultiplier = 2f,   // 伤害×2
      speedMultiplier = 0.8f,  // 速度慢
      knockback = 3f,          // 高击退
      splashDamage = 50f,      // 溅射伤害
      splashDamageRadius = 48f, // 溅射半径（6 格）
      statusEffect = AmmoSpecialEffect.EXPLOSION,  // 爆炸效果
      secondaryEffects = listOf(AmmoSpecialEffect.BLASTING) // 爆破状态
    ))
    
    // === 燃烧弹 ===
    registerModifier("incendiary", AmmoModifiers(
      reloadTime = 1.5f,
      rangeOffset = 0f,
      damageMultiplier = 1.3f,
      speedMultiplier = 0.9f,
      knockback = 1f,
      splashDamage = 20f,
      splashDamageRadius = 32f,
      statusEffect = AmmoSpecialEffect.FIRE,  // 燃烧效果
      suppressionDuration = 180f  // 压制治疗 3 秒
    ))
    
    // === 电磁炮（EMP） ===
    registerModifier("emp", AmmoModifiers(
      reloadTime = 3f,
      rangeOffset = 40f,  // 射程 +5 格
      damageMultiplier = 1.5f,
      speedMultiplier = 2f,  // 高速
      knockback = 2f,
      statusEffect = AmmoSpecialEffect.EMP,  // 电磁脉冲
      secondaryEffects = listOf(AmmoSpecialEffect.ELECTRIC, AmmoSpecialEffect.DISARMED)
    ))
    
    // === 冷冻弹 ===
    registerModifier("cryogenic", AmmoModifiers(
      reloadTime = 1.2f,
      rangeOffset = 16f,  // 射程 +2 格
      damageMultiplier = 0.8f,  // 伤害较低
      speedMultiplier = 1.1f,
      knockback = 0.5f,
      statusEffect = AmmoSpecialEffect.FREEZE,  // 冰冻效果
      secondaryEffects = listOf(AmmoSpecialEffect.SLOW)
    ))
    
    // === 钍增强弹药（完善版）===
    registerModifier("thorium", AmmoModifiers(
      reloadTime = 0.5f,    // 装填快 2 倍
      rangeOffset = 8f,     // 射程 +1 格
      damageMultiplier = 1.5f, // 伤害 +50%
      speedMultiplier = 1.2f,  // 速度 +20%
      knockback = 2f,
      statusEffect = AmmoSpecialEffect.OVERCLOCK,  // 过载效果
      armorMultiplier = 0.7f  // 降低目标护甲效果
    ))
    
    // === 穿甲弹 ===
    registerModifier("armor-piercing", AmmoModifiers(
      reloadTime = 1.2f,
      rangeOffset = 24f,    // 射程 +3 格
      damageMultiplier = 1.8f, // 高伤害
      speedMultiplier = 1.5f,  // 高速度
      knockback = 1.5f
    ))
  }
  
  /**
   * 注册弹药修正系数
   */
  fun registerModifier(ammoName: String, modifiers: AmmoModifiers) {
    ammoModifiers.put(ammoName, modifiers)
  }
  
  /**
   * 获取弹药的修正系数
   * @param ammo 弹药物品
   * @return 修正系数，如果没有配置则返回默认值
   */
  fun getModifiers(ammo: Item): AmmoModifiers {
    return ammoModifiers.get(ammo.name, AmmoModifiers())
  }
  
  /**
   * 检查是否有该弹药的修正数据
   */
  fun hasModifiers(ammo: Item): Boolean {
    return ammoModifiers.containsKey(ammo.name)
  }
}
