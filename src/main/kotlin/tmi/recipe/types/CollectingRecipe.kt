package tmi.recipe.types

import arc.Core
import arc.math.geom.Vec2
import arc.scene.Group
import arc.scene.ui.Label
import arc.scene.ui.layout.Scl
import arc.struct.ObjectMap
import arc.util.Align
import mindustry.ui.Styles
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.set
import tmi.ui.NODE_SIZE
import kotlin.math.max

open class CollectingRecipe : FactoryRecipe() {
  override val id = 2

  override fun buildView(view: Group) {
    val label = Label(Core.bundle["misc.collecting"], Styles.outlineLabel)
    label.layout()

    label.setPosition(blockPos.x + NODE_SIZE/2 + ITEM_PAD + label.prefWidth/2, blockPos.y, Align.center)
    view.addChild(label)

    buildOpts(view)
    buildTime(view, label.height)
  }

  override fun initial(recipe: Recipe, noOptional: Boolean): Vec2 {
    time = recipe.craftTime

    consPos.clear()
    prodPos.clear()
    optPos.setZero()
    blockPos.setZero()

    val mats = recipe.materials.values().filter { e -> !e.optionalCons }
    val opts =
      if (noOptional) listOf()
      else recipe.materials.values().filter { e -> e.optionalCons }
    hasOptionals = opts.isNotEmpty()
    val materialNum = mats.size
    val productionNum = recipe.productions.size

    bound.setZero()

    var wOpt = 0f
    var wMat = 0f
    var wProd = 0f

    if (hasOptionals) {
      wOpt = handleBound(opts.size)
      bound.y += ROW_PAD
    }
    if (materialNum > 0) {
      wMat = handleBound(materialNum)
      bound.y += ROW_PAD
    }
    bound.y += NODE_SIZE
    if (productionNum > 0) {
      bound.y += ROW_PAD
      wProd = handleBound(productionNum)
    }

    val offOptX = (bound.x - wOpt)/2
    val offMatX = (bound.x - wMat)/2
    val offProdX = (bound.x - wProd)/2

    val centX = bound.x/2f
    var offY = NODE_SIZE/2

    if (hasOptionals) {
      offY = handleNode(opts, consPos, offOptX, offY)
      optPos[bound.x/2] = offY
      offY += ROW_PAD
    }
    if (materialNum > 0) {
      offY = handleNode(mats, consPos, offMatX, offY)
      offY += ROW_PAD
    }
    blockPos[centX] = offY
    offY += NODE_SIZE
    if (productionNum > 0) {
      offY += ROW_PAD
      val seq = recipe.productions.values().toList()
      handleNode(seq, prodPos, offProdX, offY)
    }

    return bound
  }

  protected fun handleNode(
    seq: List<RecipeItemStack<*>>,
    pos: ObjectMap<RecipeItem<*>, Vec2>,
    offX: Float,
    offY: Float
  ): Float {
    var yOff = offY
    var dx = NODE_SIZE/2
    for (element in seq) {
      pos[element.item] = Vec2(offX + dx, yOff)
      dx += NODE_SIZE + ITEM_PAD
    }
    yOff += NODE_SIZE
    return yOff
  }

  protected fun handleBound(num: Int): Float {
    var res: Float

    bound.x = max(
      bound.x.toDouble(),
      (NODE_SIZE*num + ITEM_PAD*(num - 1)).also {
        res = it
      }.toDouble()
    ).toFloat()
    bound.y += NODE_SIZE

    return res
  }

  companion object {
    val ROW_PAD: Float = Scl.scl(60f)
    val ITEM_PAD: Float = Scl.scl(10f)
  }
}
