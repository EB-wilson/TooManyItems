package tmi.ui.calculator

import arc.Core
import arc.graphics.Color
import arc.graphics.g2d.Lines
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.ObjectMap
import arc.struct.Seq
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.ui.Styles
import tmi.recipe.AmountFormatter
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.RecipeItemType
import tmi.ui.CellType
import tmi.ui.CellType.BLOCK
import tmi.ui.CellType.PRODUCTION
import tmi.ui.RecipeItemCell
import tmi.ui.addEventBlocker
import tmi.ui.calculator.RecipeGraphLayout.ShadowKind
import tmi.util.Consts
import tmi.util.ProviderWrap
import tmi.util.enterSt
import tmi.util.exitSt
import tmi.util.fillRect
import tmi.util.wrap

/**影子节点的卡片：与真身共用同一个 [RecipeGraphNode]，但只画方块与产出。
 *
 * 它不是 [RecipeTab]：影子不参与编辑，所以**材料格、可选项 / 环境项（ATTRIBUTE）以及平衡数量都不建**，
 * 只保留两件事 —— 「看起来像真身」和给连线用的挂点：
 *
 * - [centerOffset] 给「影子连回真身」的虚线用；
 * - [outputOffset] 给「影子 → 消费者」那条线用（[inputOffset] 恒为 null，影子没有材料格）。
 *
 * 所有格子都不带点击回调，[RecipeItemCell] 因此把 touchable 设成 disabled，影子上的格子天然不可交互；
 * 整张卡片只响应一次点击（聚焦到真身），由 `CalculatorView.buildGraph` 挂上。
 *
 * [ShadowKind.LOOP] 与 [ShadowKind.USER] 目前只在方块底色与角标图标上区分。*/
class ShadowTab(
  override val node: RecipeGraphLayout.ShadowNode,
  /**留给显示效果分支用（真身的 [RecipeTab] 也是这么拿视图的）。*/
  val view: CalculatorView,
): Table(), RecipeGraphElement, CalculatorDialog.TipsProvider {
  val graphNode = node.targetNode
  val kind: ShadowKind get() = node.kind

  private val allCells = Seq<RecipeItemCell>()

  private val productionCells = ObjectMap<RecipeItem<*>, RecipeItemCell>()
  private val productionPos = ObjectMap<RecipeItem<*>, ProviderWrap<Vec2>>()
  private lateinit var blockPos : ProviderWrap<Vec2>
  private lateinit var centerUp : ProviderWrap<Vec2>

  override val nodeWidth: Float by::width
  override val nodeHeight: Float by::height
  override var nodeX: Float by this::x
  override var nodeY: Float by this::y

  init {
    build()
    validate()
    pack()

    addEventBlocker()

    // initial pos
    productionPos.forEach { it.value }
    blockPos.value
    centerUp.value
  }

  override fun layout() {
    super.layout()

    productionPos.values().forEach { it.refresh() }
    blockPos.refresh()
    centerUp.refresh()
  }

  override fun getTip(): String =
    if (kind == ShadowKind.LOOP) Core.bundle["calculator.tips.loopShadowed"]
    else Core.bundle["calculator.tips.shadowed"]

  /**产量 / 平衡数量变了之后刷新一遍：格子的文字是缓存的，卡片尺寸也可能跟着变。*/
  fun refreshLayout() {
    allCells.forEach { it.updateText() }

    invalidate()
    validate()
    pack()
  }

  override fun centerOffset() = blockPos.value

  override fun outputOffset(item: RecipeItem<*>): Vec2? = productionPos.get(item)?.value

  /**影子没有材料格，也就没有输入端的挂点。*/
  override fun inputOffset(item: RecipeItem<*>): Vec2? = null

  override fun setupInputOverListener(line: CalculatorLayout.LinkLine) {
    //影子不建材料格，输入端不会有线连过来。
  }

  override fun setupOutputOverListener(line: CalculatorLayout.LinkLine) {
    val cell = productionCells.get(line.item) ?: run {
      CalculatorLayout.warnOnce("No such output line found with item：${line.item.name}")
      return
    }

    cell.enterSt { line.isOver = true }
    cell.exitSt { line.isOver = false }
  }

  /**建一个格子。不传 `clickListener`：格子因此不可交互，影子上也就不会发生任何连接操作。*/
  private fun buildCell(type: CellType, vararg groupItems: RecipeItemStack<*>): RecipeItemCell {
    val cell = RecipeItemCell(type, *groupItems)

    cell.addEventBlocker()
    cell.isDisabled = true
    allCells.add(cell)

    val posProv = wrap {
      val cent = Vec2(cell.width/2f, cell.height/2f)
      cell.localToAscendantCoordinates(this, cent)
    }

    when (type) {
      PRODUCTION -> {
        cell.setFormatter(AmountFormatter.timedAmountFormatter())
        cell.setMultiplier {
          graphNode.balanceAmount*if (groupItems.first().itemType == RecipeItemType.ISOLATED) 1f else graphNode.efficiency
        }

        groupItems.forEach { i -> productionCells.put(i.item, cell) }
        groupItems.forEach { i -> productionPos.put(i.item, posProv) }
      }
      BLOCK -> {
        cell.style = Styles.cleart
        cell.margin(6f)
      }
      else -> {}
    }

    return cell
  }

  private fun build() {
    val recipe = graphNode.recipe

    val blockStack = RecipeItemStack(recipe.ownerBlock, 1f)
    val prod = recipe.productions.filter { it.itemType != RecipeItemType.POWER }

    //方块到产出格的引线。影子没有材料格，所以只有上面这一半。
    fillRect { x, y, _, _ ->
      val from = blockPos.value
      val centUp = centerUp.value

      Lines.stroke(Scl.scl(5f), Color.gray)
      productionPos.values().forEach {
        val off = it.value

        Lines.line(
          x + from.x, y + from.y,
          x + from.x, y + centUp.y,
        )
        Lines.line(
          x + from.x, y + centUp.y,
          x + off.x, y + centUp.y,
        )
        Lines.line(
          x + off.x, y + centUp.y,
          x + off.x, y + off.y,
        )
      }
    }

    table{ outputs ->
      outputs.table { main ->
        main.left()
        if (prod.size <= 5) {
          prod.forEach { stack ->
            main.add(buildCell(PRODUCTION, stack)).size(80f).pad(8f)
          }
        }
        else {
          val up = mutableListOf<RecipeItemStack<*>>()
          val down = mutableListOf<RecipeItemStack<*>>()
          prod.forEachIndexed { i, s ->
            if (i % 2 == 0) up.add(s)
            else down.add(s)
          }

          main.table { upTab ->
            upTab.left().add().width(40f).pad(8f)

            up.forEach { stack ->
              upTab.add(buildCell(PRODUCTION, stack)).size(80f).pad(8f)
            }
          }
          main.row()
          main.table { downTab ->
            downTab.left()
            down.forEach { stack ->
              downTab.add(buildCell(PRODUCTION, stack)).size(80f).pad(8f)
            }
          }
        }
      }
    }
    row()
    add(Element().also {
      centerUp = wrap { it.localToAscendantCoordinates(this, Vec2(it.width/2, it.height/2)) }
    }).height(64f).fill()
    row()
    table{ center ->
      //断环替身沿用「不可用」的底，用户登记的影子用普通底，两者一眼能分开。
      val t = center.table(if (kind == ShadowKind.LOOP) Tex.buttonDisabled else Consts.darkGrayUI){ cent ->
        cent.table { c ->
          c.add(buildCell(BLOCK, blockStack)).size(80f).pad(8f)
        }.fill()

        cent.fill { over ->
          over.top().left()
          over.image(if (kind == ShadowKind.LOOP) Icon.copy else Icon.zoom).size(42f).pad(8f)
        }
      }.margin(8f).get()

      blockPos = wrap {
        val cent = Vec2(t.width/2f, t.height/2f)
        t.localToAscendantCoordinates(this, cent)
      }
    }.fill()
    row()
    //真身卡片在这个位置还有一格材料排线的空间；影子没有材料格，但留着同样的留白，
    //这样两种卡片的外形一致，方块也落在同样的相对高度上。
    add(Element()).height(64f).fill()
  }
}
