package tmi.ui

import arc.graphics.Color
import arc.scene.ui.layout.Table
import arc.util.Scaling
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.graphics.Pal
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
      
      // DPS（每秒伤害 = 单发伤害 × 射速）
      stats.add("DPS:").color(Color.lightGray)
      stats.add(String.format("%.1f", info.dps)).color(Pal.ammo)
      
      // 单发伤害
      stats.add("伤害:").color(Color.lightGray)
      stats.add(String.format("%.1f", info.ammoDamage)).color(Color.valueOf("ff6b6b"))
      
      // 射程（格）- 使用实际射程（考虑修正后）
      stats.add("射程:").color(Color.lightGray)
      stats.add(String.format("%.1f", info.actualRange)).color(Color.valueOf("6b6bff"))  // 已经是格数
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
      
      // 射速（发/秒）- 显示实际射速（考虑修正后）
      details.add("射速：${String.format("%.2f/s", info.fireRate)}").color(Color.gray)
      
      // 装填时间（考虑修正后）
      details.add("装填：${String.format("%.2fs", info.actualReload / 60f)}").color(Color.gray)
      
      // 装填倍率提示（新增 - 帮助理解修正效果）
      if (info.reloadTime != 1f) {
        val multiplierText = if (info.reloadTime > 1f) "×${String.format("%.1f", info.reloadTime)}慢" else "×${String.format("%.1f", 1f/info.reloadTime)}快"
        details.add("[gray]装填修正：$multiplierText[]").color(if (info.reloadTime < 1f) Color.valueOf("00ff00") else Color.valueOf("ff6b6b"))
      }
      
      // 散布（越小越准）
      details.add("散布：${String.format("%.1f°", info.baseInaccuracy)}").color(Color.gray)
      
      // 击退值（新增）
      if (info.knockback > 0) {
        details.add("击退：${String.format("%.1f", info.knockback)}").color(Color.valueOf("ffa500"))
      }
      
      // 特殊效果（高亮显示）
      if (info.isHoming) {
        details.add("[accent]追踪[]").color(Pal.ammo)
      }
      if (info.pierceCap > 1) {
        details.add("[accent]穿透×${info.pierceCap}[]").color(Pal.ammo)
      }
      if (info.splashDamage > 0) {
        details.add("[accent]溅射[]").color(Pal.ammo)
      }
    }.growX()
    row()
    
    // === 点击事件：查看炮台详情 ===
    clicked {
      Vars.ui.content.show(info.turretBlock)
    }
  }
}
