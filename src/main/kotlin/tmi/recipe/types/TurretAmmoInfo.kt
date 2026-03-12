package tmi.recipe.types

import arc.graphics.g2d.TextureRegion
import mindustry.Vars
import mindustry.type.Item
import mindustry.world.blocks.defense.turrets.ItemTurret
import tmi.recipe.AmmoModifierConfig

/**
 * 炮台弹药信息数据类
 * 存储炮台使用特定弹药时的所有属性
 * 基于官方 API 精准计算弹药对炮台的影响
 */
data class TurretAmmoInfo(
  val turretBlock: ItemTurret,         // 炮台 Block 对象
  val turretName: String,              // 炮台内部名称
  val turretDisplayName: String,       // 炮台显示名称
  val turretIcon: TextureRegion,       // 炮台图标
  
  val ammoItem: Item,                  // 弹药物品
  
  // 弹药基础属性（从 BulletType 直接获取）
  val ammoDamage: Float,               // 弹药基础伤害
  val bulletSpeed: Float,              // 子弹基础速度
  val isHoming: Boolean,               // 是否追踪
  val pierceCap: Int,                  // 穿透上限
  
  // 炮台基础属性
  val baseReload: Float,               // 炮台基础装填时间（帧）
  val baseRange: Float,                // 炮台基础射程
  val baseInaccuracy: Float,           // 炮台基础散布
  val targetsAir: Boolean,             // 对空能力
  val targetsGround: Boolean,          // 对地能力
  
  // 弹药修正系数（关键！从 BulletType 获取）
  val reloadTime: Float = 1f,          // 装填时间倍率
  val rangeOffset: Float = 0f,         // 射程偏移（像素）
  val damageMultiplier: Float = 1f,    // 伤害倍率
  val speedMultiplier: Float = 1f,     // 速度倍率
  val knockback: Float = 0f,           // 击退值
  val homingPower: Float = 0f,         // 追踪能力强度
  val pierce: Boolean = false,         // 是否穿透单位
  val pierceBuilding: Boolean = false, // 是否穿透建筑
  
  // 特殊效果字段（新增）
  val splashDamage: Float = 0f,        // 溅射伤害
  val splashDamageRadius: Float = 0f,  // 溅射半径
  val statusEffect: AmmoModifierConfig.AmmoSpecialEffect = AmmoModifierConfig.AmmoSpecialEffect.NONE,  // 主要状态效果
  val secondaryEffects: List<AmmoModifierConfig.AmmoSpecialEffect> = emptyList(), // 次要效果
  val armorMultiplier: Float = 1f,     // 护甲修正倍率
  val suppressionDuration: Float = 0f, // 治疗压制持续时间（60 帧=1 秒）
  
  // 计算后的实际属性（考虑所有修正）
  val actualReload: Float,             // 实际装填时间 = baseReload × reloadTime
  val actualRange: Float,              // 实际射程 = (baseRange + rangeOffset) / 8（转换为格）
  val actualDamage: Float,             // 实际伤害 = ammoDamage × damageMultiplier
  val actualSpeed: Float,              // 实际速度 = bulletSpeed × speedMultiplier
  val fireRate: Float,                 // 射速（发/秒）= 60/actualReload
  val dps: Float,                      // DPS = actualDamage × fireRate
  
  // 其他属性
  val shots: Int = 1,                  // 连射次数
  val ammoPerShot: Int = 1,            // 每次射击消耗
  val modName: String? = null          // MOD 名称
) {
  companion object {
    /**
     * 从炮台和弹药创建 TurretAmmoInfo
     * 基于官方 API 精准计算弹药对炮台的影响
     */
    fun create(turret: ItemTurret, ammo: Item): TurretAmmoInfo? {
      return try {
        // 使用公共字段 ammoTypes 访问弹药数据
        val bulletType = turret.ammoTypes?.get(ammo) ?: return null
        
        // === 获取炮台基础属性 ===
        val baseReload = turret.reload
        val baseRange = turret.range
        val baseInaccuracy = turret.inaccuracy
        val targetsAir = turret.targetAir
        val targetsGround = turret.targetGround
        
        // === 获取子弹基础属性（从 BulletType）===
        val baseDamage = bulletType.damage
        val baseSpeed = bulletType.speed
        val isHoming = bulletType.homingPower > 0f
        val pierceCap = bulletType.pierceCap
        
        // === 获取弹药修正系数（关键！）===
        // 从配置管理器获取该弹药的修正系数
        val modifiers = AmmoModifierConfig.getModifiers(ammo)
        
        // reloadTime: 装填时间倍率（例如石墨=4.0 表示装填时间×4）
        val reloadTime = modifiers.reloadTime
        
        // rangeOffset: 射程偏移（像素），例如石墨 +4 格 = +32 像素
        val rangeOffset = modifiers.rangeOffset
        
        // damageMultiplier: 伤害倍率
        val damageMultiplier = modifiers.damageMultiplier
        
        // speedMultiplier: 速度倍率
        val speedMultiplier = modifiers.speedMultiplier
        
        // knockback: 击退值
        val knockback = modifiers.knockback
        
        // homingPower: 追踪能力强度
        val homingPower = modifiers.homingPower
        
        // pierce: 是否穿透单位
        val pierce = modifiers.pierce || bulletType.pierce
        
        // pierceBuilding: 是否穿透建筑
        val pierceBuilding = modifiers.pierceBuilding || bulletType.pierceBuilding
        
        // splashDamage: 溅射伤害
        val splashDamage = if (modifiers.splashDamage > 0) modifiers.splashDamage else bulletType.splashDamage
        
        // splashDamageRadius: 溅射半径
        val splashDamageRadius = if (modifiers.splashDamageRadius > 0) modifiers.splashDamageRadius else bulletType.splashDamageRadius
        
        // statusEffect: 主要状态效果
        val statusEffect = modifiers.statusEffect
        
        // secondaryEffects: 次要效果列表
        val secondaryEffects = modifiers.secondaryEffects
        
        // armorMultiplier: 护甲修正倍率
        val armorMultiplier = modifiers.armorMultiplier
        
        // suppressionDuration: 治疗压制持续时间
        val suppressionDuration = modifiers.suppressionDuration
        
        // === 计算实际属性 ===
        val actualReload = baseReload * reloadTime
        val actualRange = (baseRange + rangeOffset) / 8f  // 转换为格数
        val actualDamage = baseDamage * damageMultiplier
        val actualSpeed = baseSpeed * speedMultiplier
        
        // === 计算派生属性 ===
        val fireRate = 60f / actualReload  // 射速（发/秒）
        val dps = actualDamage * fireRate  // DPS
        
        return TurretAmmoInfo(
          turretBlock = turret,
          turretName = turret.name,
          turretDisplayName = turret.localizedName,
          turretIcon = turret.uiIcon,
          ammoItem = ammo,
          ammoDamage = baseDamage,
          bulletSpeed = baseSpeed,
          isHoming = isHoming,
          pierceCap = pierceCap,
          baseReload = baseReload,
          baseRange = baseRange,
          baseInaccuracy = baseInaccuracy,
          targetsAir = targetsAir,
          targetsGround = targetsGround,
          reloadTime = reloadTime,
          rangeOffset = rangeOffset,
          damageMultiplier = damageMultiplier,
          speedMultiplier = speedMultiplier,
          knockback = knockback,
          homingPower = homingPower,
          pierce = pierce,
          pierceBuilding = pierceBuilding,
          splashDamage = splashDamage,
          splashDamageRadius = splashDamageRadius,
          statusEffect = statusEffect,
          secondaryEffects = secondaryEffects,
          armorMultiplier = armorMultiplier,
          suppressionDuration = suppressionDuration,
          actualReload = actualReload,
          actualRange = actualRange,
          actualDamage = actualDamage,
          actualSpeed = actualSpeed,
          fireRate = fireRate,
          dps = dps,
          shots = 1,
          ammoPerShot = 1,
          modName = null
        )
      } catch (e: Exception) {
        null
      }
    }
  }
}
