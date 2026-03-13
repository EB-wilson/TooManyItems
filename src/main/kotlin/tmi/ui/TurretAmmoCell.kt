package tmi.ui

import arc.graphics.Color
import arc.scene.ui.layout.Table
import arc.util.Scaling
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.graphics.Pal
import tmi.recipe.AmmoModifierConfig
import tmi.recipe.types.TurretAmmoInfo

/**
 * 炮台弹药信息显示单元（简化版）
 * 参考 RecipeItemCell 的设计风格
 */
class TurretAmmoCell(val info: TurretAmmoInfo) : Table() {
  init {
    defaults().pad(4f)
    
    // === 第一行：炮台图标和名称 ===
    table { header ->
      header.defaults().padRight(8f)
      
      // 炮台图标
      header.image(info.turretIcon).size(40f).scaling(Scaling.fit)
      
      // 炮台名称
      header.add(info.turretDisplayName).color(Pal.accent).growX()
      
      // MOD 标识
      if (info.modName != null) {
        header.add("[gray][]${info.modName}").right().color(Color.gray)
      }
    }.growX()
    row()
    
    // === 第二行：核心战斗属性 ===
    table { stats ->
      stats.defaults().padRight(12f)
      
      // DPS（每秒伤害 = 单发伤害 × 射速）- 已包含分裂弹头和预热
      stats.add("DPS:").color(Color.lightGray)
      
      // 显示综合 DPS（考虑预热）和理论 DPS
      val dpsText = if (info.effectiveDps != info.dps) {
        "${String.format("%.1f", info.effectiveDps)}[gray]/${String.format("%.1f", info.dps)}[]"  // 综合/理论
      } else {
        String.format("%.1f", info.dps)
      }
      stats.add(dpsText).color(Pal.ammo)
      
      // 单发伤害（考虑修正后）
      val actualDamageText = if (info.damageMultiplier != 1f) {
        "${String.format("%.1f", info.actualDamage)}[gray]+${String.format("%.0f%%", (info.damageMultiplier - 1f) * 100)}[]".replace("+", " ")
      } else {
        String.format("%.1f", info.actualDamage)
      }
      stats.add("伤害:").color(Color.lightGray)
      stats.add(actualDamageText).color(Color.valueOf("ff6b6b"))
      
      // 射程（格）- 使用实际射程（考虑修正后）
      val rangeText = if (info.rangeOffset != 0f) {
        "${String.format("%.1f", info.actualRange)}[gray](${if (info.rangeOffset > 0) "+" else ""}${String.format("%.0f", info.rangeOffset / 8f)})[]"
      } else {
        String.format("%.1f", info.actualRange)
      }
      stats.add("射程:").color(Color.lightGray)
      stats.add(rangeText).color(Color.valueOf("6b6bff"))  // 已经是格数
    }.growX()
    row()
    
    // === 第三行：次要属性 ===
    table { details ->
      details.defaults().padRight(12f)
      
      // 目标类型
      val targetText = buildString {
        if (info.targetsAir && info.targetsGround) append("空 + 地")
        else if (info.targetsAir) append("对空")
        else if (info.targetsGround) append("对地")
        else append("无效")
      }
      details.add("目标：$targetText").color(Color.lightGray)
      
      // 射速分级系统（新增）
      details.add("理论：${String.format("%.2f/s(60t)", info.fireRate)}").color(Color.gray)
      
      // 如果有预热，显示不同级别的平均射速
      if (info.fireRate5s != info.fireRate || info.fireRate10s != info.fireRate) {
        details.add("5 秒:${String.format("%.2f", info.fireRate5s)}").color(if (info.fireRate5s < info.fireRate) Color.valueOf("ff6b6b") else Color.gray)
        details.add("10 秒:${String.format("%.2f", info.fireRate10s)}").color(if (info.fireRate10s < info.fireRate) Color.valueOf("ff6b6b") else Color.gray)
      }
      
      // 持续射击能力
      if (!info.sustainedFireTime.isInfinite() && info.sustainedFireTime > 0) {
        details.add("持续:${String.format("%.1fs", info.sustainedFireTime)}").color(Color.valueOf("6bff6b"))
      }
      
      // 装填时间（考虑修正后）
      details.add("装填：${String.format("%.2fs", info.actualReload / 60f)}").color(Color.gray)
      
      // 装填倍率提示（新增 - 注意现在的逻辑是 >1 表示更快）
      if (info.reloadTime != 1f) {
        val multiplierText = if (info.reloadTime > 1f) "×${String.format("%.1f", info.reloadTime)}快" else "×${String.format("%.1f", 1f/info.reloadTime)}慢"
        details.add("[gray]修正：$multiplierText[]").color(if (info.reloadTime > 1f) Color.valueOf("00ff00") else Color.valueOf("ff6b6b"))
      }
      
      // 散布（越小越准）
      details.add("散布：${String.format("%.1f°", info.baseInaccuracy)}").color(Color.gray)
      
      // 击退值（新增）
      if (info.knockback > 0) {
        details.add("击退：${String.format("%.1f", info.knockback)}").color(Color.valueOf("ffa500"))
      }
      
      // 特殊效果（高亮显示）- 新增更多效果展示
      if (info.isHoming) {
        details.add("[accent]追踪[]").color(Pal.ammo)
      }
      if (info.pierceCap > 1) {
        details.add("[accent]穿透×${info.pierceCap}[]").color(Pal.ammo)
      }
      if (info.splashDamage > 0) {
        val splashRadius = info.splashDamageRadius / 8f  // 转换为格数
        details.add("[accent]溅射 (${String.format("%.1f", splashRadius)}格)[]").color(Pal.ammo)
      }
      if (info.pierce) {
        details.add("[yellow]穿透单位[]").color(Color.yellow)
      }
      if (info.pierceBuilding) {
        details.add("[yellow]穿透建筑[]").color(Color.yellow)
      }
      
      // 分裂弹头（新增）
      if (info.fragBullet && info.fragBullets > 0) {
        details.add("[accent]分裂×${info.fragBullets}[]").color(Pal.ammo)
      }
      
      // 状态效果（新增）
      when (info.statusEffect) {
        AmmoModifierConfig.AmmoSpecialEffect.FIRE -> details.add("[orange]点燃[]")
        AmmoModifierConfig.AmmoSpecialEffect.ELECTRIC -> details.add("[yellow]电击[]")
        AmmoModifierConfig.AmmoSpecialEffect.FREEZE -> details.add("[cyan]冷冻[]")
        AmmoModifierConfig.AmmoSpecialEffect.EXPLOSION -> details.add("[red]爆炸[]")
        AmmoModifierConfig.AmmoSpecialEffect.SAP -> details.add("[purple]吸取[]")
        AmmoModifierConfig.AmmoSpecialEffect.BLASTING -> details.add("[orange]爆破[]")
        AmmoModifierConfig.AmmoSpecialEffect.SLOW -> details.add("[gray]减速[]")
        AmmoModifierConfig.AmmoSpecialEffect.DISARMED -> details.add("[red]禁用[]")
        AmmoModifierConfig.AmmoSpecialEffect.OVERCLOCK -> details.add("[green]过载[]")
        AmmoModifierConfig.AmmoSpecialEffect.SHIELDED -> details.add("[blue]护盾[]")
        AmmoModifierConfig.AmmoSpecialEffect.MELTING -> details.add("[orange]熔化[]")
        AmmoModifierConfig.AmmoSpecialEffect.LIGHTNING -> details.add("[yellow]闪电[]")
        AmmoModifierConfig.AmmoSpecialEffect.SHRAPNEL -> details.add("[accent]弹片[]")
        AmmoModifierConfig.AmmoSpecialEffect.LIQUID -> details.add("[blue]液体[]")
        AmmoModifierConfig.AmmoSpecialEffect.LASER -> details.add("[red]激光[]")
        AmmoModifierConfig.AmmoSpecialEffect.FLAME -> details.add("[orange]火焰[]")
        AmmoModifierConfig.AmmoSpecialEffect.MISSILE -> details.add("[accent]导弹[]")
        AmmoModifierConfig.AmmoSpecialEffect.ARTILLERY -> details.add("[red]火炮[]")
        AmmoModifierConfig.AmmoSpecialEffect.BOMB -> details.add("[red]炸弹[]")
        AmmoModifierConfig.AmmoSpecialEffect.EMP -> details.add("[yellow]EMP[]")
        AmmoModifierConfig.AmmoSpecialEffect.INTERCEPTOR -> details.add("[accent]拦截[]")
        AmmoModifierConfig.AmmoSpecialEffect.RAIL -> details.add("[accent]轨道炮[]")
        AmmoModifierConfig.AmmoSpecialEffect.FRAG -> details.add("[accent]分裂[]")
        AmmoModifierConfig.AmmoSpecialEffect.PIERCE -> details.add("[yellow]穿甲[]")
        AmmoModifierConfig.AmmoSpecialEffect.HOMING -> details.add("[accent]追踪[]")
        AmmoModifierConfig.AmmoSpecialEffect.ARMOR_BREAK -> details.add("[red]破甲[]")
        AmmoModifierConfig.AmmoSpecialEffect.ARMOR_PIERCE -> details.add("[yellow]装甲[]")
        AmmoModifierConfig.AmmoSpecialEffect.HEAL_SUPPRESS -> details.add("[purple]压制[]")
        AmmoModifierConfig.AmmoSpecialEffect.NONE -> {} // 无效果不显示
      }
      
      // 状态持续时间（如果有）
      if (info.statusDuration > 0) {
        details.add("[gray](${String.format("%.1f", info.statusDuration)}秒)[]").color(Color.gray)
      }
    }.growX()
    row()
    
    // === 点击事件：查看炮台详情 ===
    clicked {
      Vars.ui.content.show(info.turretBlock)
    }
  }
}
