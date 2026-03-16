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
  
  // 炮台扩展属性（新增）
  val turretSize: Int = 1,             // 炮台大小（格）
  val maxAmmo: Int = 0,                // 最大弹药容量
  val ammoPerShot: Int = 1,            // 每次射击消耗
  val shootWarmupSpeed: Float = 0f,    // 预热速度
  val minWarmup: Float = 0f,           // 最小预热值（0-1）
  val linearWarmup: Boolean = false,   // 是否线性预热
  val warmupMaintainTime: Float = 0f,  // 预热维持时间（秒）
  val turretReload: Float = 0f,        // 炮台基础装填时间（帧，从炮台对象获取）
  
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
  
  // 分裂弹头相关
  val fragBullet: Boolean = false,     // 是否分裂弹头
  val fragBullets: Int = 0,            // 分裂子弹数量
  val fragDamageMultiplier: Float = 1f, // 分裂子弹伤害倍率
  
  // 状态效果
  val statusEffect: AmmoModifierConfig.AmmoSpecialEffect = AmmoModifierConfig.AmmoSpecialEffect.NONE,  // 主要状态效果
  val statusDuration: Float = 0f,      // 状态持续时间（秒）
  val secondaryEffects: List<AmmoModifierConfig.AmmoSpecialEffect> = emptyList(), // 次要效果列表
  
  // 计算后的实际属性（考虑所有修正）
  val actualReload: Float,             // 实际装填时间 = baseReload / reloadTime
  val actualRange: Float,              // 实际射程 = (baseRange + rangeOffset) / 8（转换为格）
  val actualDamage: Float,             // 实际伤害 = ammoDamage × damageMultiplier
  val actualSpeed: Float,              // 实际速度 = bulletSpeed × speedMultiplier
  
  // 射速分级系统（新增）
  val fireRate: Float,                 // 理论射速（发/秒）= 60/actualReload
  val fireRate5s: Float,               // 5 秒内平均射速（考虑预热）
  val fireRate10s: Float,              // 10 秒内平均射速
  val fireRate20s: Float,              // 20 秒内平均射速
  
  // 持续射击能力（新增）
  val sustainedFireTime: Float,        // 持续射击时间（秒）= 弹药容量 / (射速 × 每次消耗)
  val totalShots: Int,                 // 总射击次数 = 弹药容量 / 每次消耗
  
  // DPS 计算（考虑分裂弹头和预热）
  val dps: Float,                      // 理论 DPS（无预热）
  val warmupDps: Float,                // 预热期间实际 DPS
  val effectiveDps: Float,             // 考虑预热的综合 DPS
  
  // 其他属性
  val shots: Int = 1,                  // 连射次数
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
        
        // === 获取炮台扩展属性（新增）===
        val turretSize = turret.size
        
        // 尝试通过反射获取真实值（因为官方 API 未直接暴露）
        // TODO: 后续需要通过反射或其他方式获取这些字段的真实值
        val maxAmmoVal = 0  // 暂时使用默认值
        val ammoPerShotVal = 1
        val shootWarmupSpeedVal = 0f
        val minWarmupVal = 0f
        val linearWarmupVal = false
        val warmupMaintainTimeVal = 0f
        
        val turretReloadVal = turret.reload
        
        // === 获取子弹基础属性（从 BulletType）===
        val baseDamage = bulletType.damage
        val baseSpeed = bulletType.speed
        val isHoming = bulletType.homingPower > 0f
        val pierceCap = bulletType.pierceCap
        
        // === 直接从 BulletType 获取弹药修正系数（官方 API）===
        
        // reloadMultiplier: 装填时间倍率（官方 API 字段）
        // 例如：石墨的 reloadMultiplier = 4.0，钍的 reloadMultiplier = 0.5
        val reloadTime = bulletType.reloadMultiplier
        
        // rangeChange + extraRangeMargin: 射程修正（官方 API 字段）
        // 直接相加得到总射程偏移（像素）
        val rangeOffset = (bulletType.rangeChange ?: 0f) + (bulletType.extraRangeMargin ?: 0f)
        
        // buildingDamageMultiplier: 建筑伤害倍率（官方 API 字段）
        val damageMultiplier = bulletType.buildingDamageMultiplier
        
        // speed: 已经是实际速度，无需额外倍率
        val speedMultiplier = 1f  // 速度已经在 baseSpeed 中体现
        
        // knockback: 击退值（官方 API 字段）
        val knockback = bulletType.knockback
        
        // homingPower: 追踪能力强度（官方 API 字段）
        val homingPower = bulletType.homingPower
        
        // pierce: 是否穿透单位（官方 API 字段）
        val pierce = bulletType.pierce
        
        // pierceBuilding: 是否穿透建筑（官方 API 字段）
        val pierceBuilding = bulletType.pierceBuilding
        
        // splashDamage: 溅射伤害（官方 API 字段）
        val splashDamage = bulletType.splashDamage
        
        // splashDamageRadius: 溅射半径（官方 API 字段）
        val splashDamageRadius = bulletType.splashDamageRadius
        
        // === 分裂弹头相关字段 ===
        // fragBullet: 是否有分裂弹头
        val hasFragBullet = bulletType.fragBullet != null
        
        // fragBullets: 分裂子弹数量
        val fragBulletsCount = bulletType.fragBullets
        
        // 分裂子弹伤害倍率（简化处理）
        val fragDamageMult = if (hasFragBullet) 1f else 0f
        
        // === 状态效果字段 ===
        // status: 状态效果对象（需要映射到我们的枚举）
        val statusObj = bulletType.status
        val statusDurFrames = bulletType.statusDuration  // 持续时间（帧）
        
        // 根据 status 对象类型映射到我们的枚举
        val mappedStatusEffect = mapStatusEffect(statusObj)
        
        // secondaryEffects: 次要效果列表 - 暂不支持
        val secondaryEffectsList = emptyList<AmmoModifierConfig.AmmoSpecialEffect>()
        
        // === 计算实际属性 ===
        // 注意：reloadMultiplier 是乘以 "reload speed"，所以要反过来计算
        // reloadMultiplier > 1 表示装填速度更快，装填时间更短
        val actualReload = baseReload / reloadTime  // 实际装填时间（帧）
        val actualRange = (baseRange + rangeOffset) / 8f  // 转换为格数
        val actualDamage = baseDamage * damageMultiplier
        val actualSpeed = baseSpeed * speedMultiplier
        
        // === 计算射速分级系统 ===
        // 理论射速（无预热）
        val theoreticalFireRate = 60f / actualReload  // 发/秒
        
        // === 计算预热时间 ===
        // 根据 Mindustry 的预热机制：
        // 1. 线性预热：warmupTime = minWarmup / shootWarmupSpeed
        // 2. 曲线预热：warmupTime = -ln(1 - minWarmup) / shootWarmupSpeed
        val warmupSeconds = if (shootWarmupSpeedVal > 0f) {
          if (linearWarmupVal) {
            // 线性预热：时间 = 最小预热值 / 预热速度
            minWarmupVal / shootWarmupSpeedVal
          } else {
            // 曲线预热（指数增长）：时间 = -ln(1 - minWarmup) / 预热速度
            // 简化处理：假设 minWarmup=0.5 时需要 1 秒
            if (minWarmupVal >= 1f) Float.MAX_VALUE
            else -kotlin.math.ln(1f - minWarmupVal) / shootWarmupSpeedVal
          }
        } else {
          0f  // 无预热要求
        }
        
        // 考虑预热维持时间
        val totalWarmupMaintainSeconds = warmupSeconds + warmupMaintainTimeVal
        
        // 5 秒内平均射速 = (预热时间内的射击次数 + 剩余时间的射击次数) / 5
        val fireRate5s = calculateAverageFireRate(theoreticalFireRate, warmupSeconds, 5.0f)
        
        // 10 秒内平均射速
        val fireRate10s = calculateAverageFireRate(theoreticalFireRate, warmupSeconds, 10.0f)
        
        // 20 秒内平均射速
        val fireRate20s = calculateAverageFireRate(theoreticalFireRate, warmupSeconds, 20.0f)
        
        // === 计算持续射击能力 ===
        // 总射击次数 = 最大弹药容量 / 每次射击消耗
        val totalShotsCount = if (ammoPerShotVal > 0 && maxAmmoVal > 0) maxAmmoVal / ammoPerShotVal else 0
        
        // 持续射击时间（秒）= 总射击次数 / 射速
        val sustainedFireTimeSecs = if (theoreticalFireRate > 0.0f && totalShotsCount > 0) {
          totalShotsCount.toFloat() / theoreticalFireRate
        } else {
          Float.POSITIVE_INFINITY  // 无限弹药或无射击能力
        }
        
        // === 计算 DPS ===
        // 总伤害 = 本体伤害 + 分裂弹头伤害
        val totalDamagePerShot = if (hasFragBullet && fragBulletsCount > 0) {
          actualDamage + (actualDamage * fragDamageMult * fragBulletsCount)
        } else {
          actualDamage
        }
        
        // 理论 DPS（无预热）
        val theoreticalDps = totalDamagePerShot * theoreticalFireRate
        
        // === 预热期间效率计算 ===
        // 根据 Mindustry 机制，预热期间效率从 0 增长到 100%
        // 线性预热：平均效率 = minWarmup / 2
        // 曲线预热：平均效率 ≈ minWarmup * 0.6（经验值）
        val warmupAverageEfficiency = if (linearWarmupVal) {
          minWarmupVal / 2f
        } else {
          minWarmupVal * 0.6f
        }
        
        // 预热期间的实际 DPS
        val warmupDpsVal = theoreticalDps * warmupAverageEfficiency
        
        // === 综合 DPS（考虑整个射击周期）===
        // 周期 = 预热时间 + 维持时间 + 全功率射击时间
        val effectiveDpsVal = if (warmupSeconds > 0.0f && sustainedFireTimeSecs > 0.0f && !sustainedFireTimeSecs.isInfinite()) {
          val warmupDamage = warmupDpsVal * warmupSeconds
          val maintainDamage = theoreticalDps * warmupMaintainTimeVal
          val fullPowerTime = if (sustainedFireTimeSecs > totalWarmupMaintainSeconds) {
            sustainedFireTimeSecs - totalWarmupMaintainSeconds
          } else {
            0.0f
          }
          val fullPowerDamage = theoreticalDps * fullPowerTime
          
          val totalTime = warmupSeconds + warmupMaintainTimeVal + fullPowerTime
          if (totalTime > 0) (warmupDamage + maintainDamage + fullPowerDamage) / totalTime else theoreticalDps
        } else {
          theoreticalDps
        }
        
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
          turretSize = turretSize,
          maxAmmo = maxAmmoVal,
          ammoPerShot = ammoPerShotVal,
          shootWarmupSpeed = shootWarmupSpeedVal,
          minWarmup = minWarmupVal,
          linearWarmup = linearWarmupVal,
          warmupMaintainTime = warmupMaintainTimeVal,
          turretReload = turretReloadVal,
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
          fragBullet = hasFragBullet,
          fragBullets = fragBulletsCount,
          fragDamageMultiplier = fragDamageMult,
          statusEffect = mappedStatusEffect,
          statusDuration = statusDurFrames / 60f,  // 转换为秒
          secondaryEffects = secondaryEffectsList,
          actualReload = actualReload,
          actualRange = actualRange,
          actualDamage = actualDamage,
          actualSpeed = actualSpeed,
          fireRate = theoreticalFireRate,
          fireRate5s = fireRate5s,
          fireRate10s = fireRate10s,
          fireRate20s = fireRate20s,
          sustainedFireTime = sustainedFireTimeSecs,
          totalShots = totalShotsCount,
          dps = theoreticalDps,
          warmupDps = warmupDpsVal,
          effectiveDps = effectiveDpsVal,
          shots = 1,
          modName = null
        )
      } catch (e: Exception) {
        null
      }
    }
    
    /**
     * 将官方 API 的 StatusEffect 对象映射到我们的 AmmoSpecialEffect 枚举
     */
    private fun mapStatusEffect(statusObj: mindustry.type.StatusEffect?): AmmoModifierConfig.AmmoSpecialEffect {
      if (statusObj == null) return AmmoModifierConfig.AmmoSpecialEffect.NONE
      
      // 使用 toString() 获取名称
      val statusName = statusObj.toString().lowercase()
      return when {
        statusName.contains("burning") -> AmmoModifierConfig.AmmoSpecialEffect.FIRE
        statusName.contains("freezing") -> AmmoModifierConfig.AmmoSpecialEffect.FREEZE
        statusName.contains("shocked") || statusName.contains("shock") -> AmmoModifierConfig.AmmoSpecialEffect.ELECTRIC
        statusName.contains("sapped") || statusName.contains("sap") -> AmmoModifierConfig.AmmoSpecialEffect.SAP
        statusName.contains("melting") -> AmmoModifierConfig.AmmoSpecialEffect.MELTING
        statusName.contains("blasting") -> AmmoModifierConfig.AmmoSpecialEffect.BLASTING
        statusName.contains("slowed") -> AmmoModifierConfig.AmmoSpecialEffect.SLOW
        statusName.contains("disarmed") -> AmmoModifierConfig.AmmoSpecialEffect.DISARMED
        statusName.contains("overclock") -> AmmoModifierConfig.AmmoSpecialEffect.OVERCLOCK
        statusName.contains("shielded") -> AmmoModifierConfig.AmmoSpecialEffect.SHIELDED
        else -> AmmoModifierConfig.AmmoSpecialEffect.NONE
      }
    }
    
    /**
     * 计算平均射速（考虑预热时间）
     * @param theoreticalRate 理论最大射速（发/秒）
     * @param warmupTime 预热时间（秒）
     * @param totalTime 总评估时间（秒）
     */
    private fun calculateAverageFireRate(theoreticalRate: Float, warmupTime: Float, totalTime: Float): Float {
      if (warmupTime <= 0) return theoreticalRate
      if (totalTime <= warmupTime) {
        // 如果评估时间短于预热时间，只能发挥部分火力
        return theoreticalRate * (totalTime / warmupTime) * 0.5f
      }
      
      // 预热期间平均射速是最大射速的一半（线性增长）
      val warmupShots = (theoreticalRate * 0.5f) * warmupTime
      val fullPowerShots = theoreticalRate * (totalTime - warmupTime)
      
      return (warmupShots + fullPowerShots) / totalTime
    }
  }
}
