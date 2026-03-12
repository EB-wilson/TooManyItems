package tmi.recipe.types

import arc.struct.ObjectMap
import arc.struct.Seq
import tmi.recipe.RecipeItemStack

/**
 * 炮台弹药管理器（单例）
 * 管理所有弹药数据，提供查询接口
 */
object TurretAmmoManager {
  private val ammoToTurrets = ObjectMap<RecipeItem<*>, Seq<TurretAmmoInfo>>()
  
  var initialized = false
    internal set
  
  /**
   * 注册弹药数据
   */
  fun registerAmmoData(ammo: RecipeItem<*>, turrets: Seq<TurretAmmoInfo>) {
    ammoToTurrets.put(ammo, turrets)
  }
  
  /**
   * 获取可以使用该弹药的所有炮台
   */
  fun getTurretsForAmmo(ammo: RecipeItem<*>): Seq<TurretAmmoInfo> {
    return ammoToTurrets.get(ammo, Seq())
  }
  
  /**
   * 检查物品是否为弹药
   */
  fun isAmmo(ammo: RecipeItem<*>): Boolean {
    return ammoToTurrets.containsKey(ammo) && ammoToTurrets.get(ammo).size > 0
  }
}
