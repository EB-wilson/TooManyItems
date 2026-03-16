package tmi.ui

import arc.graphics.Color
import arc.scene.ui.layout.Table
import arc.struct.Seq
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.TurretAmmoInfo

/**
 * 炮台弹药列表视图（简化版）
 * 显示所有使用该弹药的炮台，支持排序和过滤
 */
class TurretAmmoListView(
  val ammo: RecipeItem<*>,
  val turrets: Seq<TurretAmmoInfo>
) : Table() {
  
  private var currentSort = SortType.DPS
  private var filteredTurrets: List<TurretAmmoInfo> = turrets.toList()
  private var currentPage = 0
  private val turretsPerPage = 8  // 每页显示 8 个炮台
  
  private val totalPages: Int
    get() = (filteredTurrets.size + turretsPerPage - 1) / turretsPerPage
  
  private val pageCountText: String
    get() {
      if (totalPages == 0) return "0 / 0"
      return "${currentPage + 1} / $totalPages"
    }
  
  enum class SortType {
    DPS,        // 按 DPS 排序
    DAMAGE,     // 按伤害排序
    RANGE,      // 按射程排序
    FIRE_RATE,  // 按射速排序
    ACCURACY    // 按精度排序
  }
  
  init {
    defaults().fillX().pad(4f)
    
    // === 标题 ===
    add("${ammo.localizedName} - 可用炮台 (${turrets.size})").colspan(2).padBottom(8f)
    row()
    
    // === 分类统计 ===
    val airOnly = turrets.count { it.targetsAir && !it.targetsGround }
    val groundOnly = turrets.count { it.targetsGround && !it.targetsAir }
    val both = turrets.count { it.targetsAir && it.targetsGround }
    
    table { stats ->
      stats.defaults().padRight(12f)
      stats.add("[lightgray]仅对空：[]$airOnly")
      stats.add("[lightgray]仅对地：[]$groundOnly")
      stats.add("[lightgray]全能：[]$both")
    }.colspan(2).padBottom(8f)
    row()
    
    // === 炮台列表（可滚动 + 分页）===
    table { list ->
      list.left().top()
      
      val fromIndex = currentPage * turretsPerPage
      val toIndex = minOf(fromIndex + turretsPerPage, filteredTurrets.size)
      
      for (i in fromIndex until toIndex) {
        val turretInfo = filteredTurrets[i]
        list.add(TurretAmmoCell(turretInfo)).growX()
        
        if (i < toIndex - 1) {
          list.row()
          list.image().color(Color.darkGray).height(2f).growX()
          list.row()
        }
      }
    }.grow().scrollY(false)
    row()
    
    // === 分页控制 ===
    table { pagination ->
      pagination.defaults().pad(4f)
      
      // 上一页
      pagination.button({ t ->
        t.add("◀")
      }, Styles.clearNonei) {
        if (currentPage > 0) {
          currentPage--
          rebuild()
        }
      }.disabled { currentPage <= 0 }.size(40f)
      
      // 页码显示
      pagination.add("$pageCountText").width(100f).align(1).color(Color.lightGray)
      
      // 下一页
      pagination.button({ t ->
        t.add("▶")
      }, Styles.clearNonei) {
        if (currentPage < totalPages - 1) {
          currentPage++
          rebuild()
        }
      }.disabled { currentPage >= totalPages - 1 }.size(40f)
      
      // 跳转到首页
      pagination.button({ t ->
        t.add("|◀")
      }, Styles.clearNonei) {
        currentPage = 0
        rebuild()
      }.disabled { currentPage <= 0 }.size(32f)
      
      // 跳转到末页
      pagination.button({ t ->
        t.add("▶|")
      }, Styles.clearNonei) {
        currentPage = totalPages - 1
        rebuild()
      }.disabled { currentPage >= totalPages - 1 }.size(32f)
    }.growX().padTop(4f)
    
    // === 排序和过滤控制 ===
    row()
    table { controls ->
      controls.right().defaults().padLeft(6f)
      
      controls.add("排序：").color(Color.gray)
      controls.button("DPS", Icon.up) { sortBy(SortType.DPS) }
      controls.button("伤害", Icon.tree) { sortBy(SortType.DAMAGE) }
      controls.button("射程", Icon.zoom) { sortBy(SortType.RANGE) }
      controls.button("射速", Icon.menu) { sortBy(SortType.FIRE_RATE) }
      
      controls.row()
      controls.add("过滤：").color(Color.gray)
      controls.button("对空", Icon.upOpen) { filterByTarget(true, false) }
      controls.button("对地", Icon.downOpen) { filterByTarget(false, true) }
      controls.button("全能", Icon.production) { filterByTarget(true, true) }
    }.growX().padTop(8f)
  }
  
  /**
   * 按指定类型排序
   */
  private fun sortBy(type: SortType) {
    currentSort = type
    filteredTurrets = when (type) {
      SortType.DPS -> turrets.sortedByDescending { it.dps }
      SortType.DAMAGE -> turrets.sortedByDescending { it.actualDamage }  // 使用实际伤害
      SortType.RANGE -> turrets.sortedByDescending { it.actualRange }    // 使用实际射程
      SortType.FIRE_RATE -> turrets.sortedByDescending { it.fireRate }
      SortType.ACCURACY -> turrets.sortedBy { it.baseInaccuracy }         // 使用基础散布
    }
    currentPage = 0  // 重置到第一页
    rebuild()
  }
  
  /**
   * 按目标类型过滤
   */
  private fun filterByTarget(air: Boolean, ground: Boolean) {
    filteredTurrets = turrets.filter { turret ->
      (air && turret.targetsAir) || (ground && turret.targetsGround)
    }
    currentPage = 0  // 重置到第一页
    rebuild()
  }
  
  /**
   * 重建 UI
   */
  private fun rebuild() {
    clearChildren()
    
    // === 重新构建 UI ===
    add("${ammo.localizedName} - 可用炮台 (${filteredTurrets.size})").colspan(2).padBottom(8f)
    row()
    
    // 重新添加统计信息
    val airOnly = filteredTurrets.count { it.targetsAir && !it.targetsGround }
    val groundOnly = filteredTurrets.count { it.targetsGround && !it.targetsAir }
    val both = filteredTurrets.count { it.targetsAir && it.targetsGround }
    
    table { stats ->
      stats.defaults().padRight(12f)
      stats.add("[lightgray]仅对空：[]$airOnly")
      stats.add("[lightgray]仅对地：[]$groundOnly")
      stats.add("[lightgray]全能：[]$both")
    }.colspan(2).padBottom(8f)
    row()
    
    // 重新添加炮台列表（带分页）
    table { list ->
      list.left().top()
      
      val fromIndex = currentPage * turretsPerPage
      val toIndex = minOf(fromIndex + turretsPerPage, filteredTurrets.size)
      
      for (i in fromIndex until toIndex) {
        val turretInfo = filteredTurrets[i]
        list.add(TurretAmmoCell(turretInfo)).growX()
        
        if (i < toIndex - 1) {
          list.row()
          list.image().color(Color.darkGray).height(2f).growX()
          list.row()
        }
      }
    }.grow().scrollY(false)
    row()
    
    // 重新添加分页控制
    table { pagination ->
      pagination.defaults().pad(4f)
      
      pagination.button({ t ->
        t.add("◀")
      }, Styles.clearNonei) {
        if (currentPage > 0) {
          currentPage--
          rebuild()
        }
      }.disabled { currentPage <= 0 }.size(40f)
      
      pagination.add("$pageCountText").width(100f).align(1).color(Color.lightGray)
      
      pagination.button({ t ->
        t.add("▶")
      }, Styles.clearNonei) {
        if (currentPage < totalPages - 1) {
          currentPage++
          rebuild()
        }
      }.disabled { currentPage >= totalPages - 1 }.size(40f)
      
      pagination.button({ t ->
        t.add("|◀")
      }, Styles.clearNonei) {
        currentPage = 0
        rebuild()
      }.disabled { currentPage <= 0 }.size(32f)
      
      pagination.button({ t ->
        t.add("▶|")
      }, Styles.clearNonei) {
        currentPage = totalPages - 1
        rebuild()
      }.disabled { currentPage >= totalPages - 1 }.size(32f)
    }.growX().padTop(4f)
    
    // 重新添加控制按钮
    row()
    table { controls ->
      controls.right().defaults().padLeft(6f)
      
      controls.add("排序：").color(Color.gray)
      controls.button("DPS", Icon.up) { sortBy(SortType.DPS) }
      controls.button("伤害", Icon.tree) { sortBy(SortType.DAMAGE) }
      controls.button("射程", Icon.zoom) { sortBy(SortType.RANGE) }
      controls.button("射速", Icon.menu) { sortBy(SortType.FIRE_RATE) }
      
      controls.row()
      controls.add("过滤：").color(Color.gray)
      controls.button("对空", Icon.upOpen) { filterByTarget(true, false) }
      controls.button("对地", Icon.downOpen) { filterByTarget(false, true) }
      controls.button("全能", Icon.production) { filterByTarget(true, true) }
    }.growX().padTop(8f)
    
    invalidateHierarchy()
  }
}
