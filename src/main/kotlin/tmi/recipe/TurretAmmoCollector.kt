package tmi.recipe

import arc.struct.ObjectMap
import arc.struct.Seq
import mindustry.Vars
import mindustry.type.Item
import mindustry.world.blocks.defense.turrets.ItemTurret
import tmi.TooManyItems
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.TurretAmmoInfo

/**
 * 炮台弹药数据收集器
 * 负责遍历所有炮台并收集弹药数据
 */
object TurretAmmoCollector {
  
  /**
   * 收集所有炮台的弹药数据
   * @return Map<弹药，炮台列表>
   */
  fun collectAllTurretAmmoData(): ObjectMap<tmi.recipe.types.RecipeItem<*>, Seq<TurretAmmoInfo>> {
    val result = ObjectMap<tmi.recipe.types.RecipeItem<*>, Seq<TurretAmmoInfo>>()
    
    Vars.content.blocks().forEach { block ->
      if (block !is ItemTurret) return@forEach
      
      try {
        // 使用公共字段 ammoTypes 访问弹药数据
        val ammoMap = block.ammoTypes ?: return@forEach
        
        ammoMap.forEach { entry ->
          val ammoItem = entry.key
          val bulletType = entry.value
          
          val recipeAmmo = TooManyItems.itemsManager.getItem(ammoItem)
          
          if (!result.containsKey(recipeAmmo)) {
            result.put(recipeAmmo, Seq())
          }
          
          val turretInfo = TurretAmmoInfo.create(block, ammoItem)
          if (turretInfo != null) {
            result.get(recipeAmmo).add(turretInfo)
          }
        }
      } catch (e: Exception) {
        // 忽略单个炮台的错误，继续处理其他炮台
      }
    }
    
    return result
  }
}
