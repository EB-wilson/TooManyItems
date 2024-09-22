package tmi.recipe.types

import arc.Core
import arc.math.geom.Vec2
import arc.scene.Group
import arc.scene.ui.Label
import arc.struct.ObjectSet
import arc.struct.Seq
import arc.util.Align
import mindustry.ui.Styles
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.ui.NODE_SIZE
import tmi.ui.NodeType
import tmi.ui.RecipeNode
import tmi.ui.RecipeView.LineMeta

class GeneratorRecipe : FactoryRecipe() {
  private val powers = ObjectSet<RecipeItem<*>>()

  override val id = 3

  fun addPower(item: RecipeItem<*>){
    powers.add(item)
  }

  override fun buildView(view: Group) {
    val label = Label(Core.bundle["misc.generator"], Styles.outlineLabel)
    label.layout()

    label.setPosition(
      blockPos.x + NODE_SIZE/2 + ITEM_PAD + label.prefWidth/2,
      blockPos.y,
      Align.center
    )
    view.addChild(label)

    buildOpts(view)
    buildTime(view, label.height)
  }

  override fun initial(recipe: Recipe): Vec2 {
    time = recipe.craftTime

    consPos.clear()
    prodPos.clear()
    blockPos.setZero()

    val mats = recipe.materials.values.filter { e -> !e.optionalCons }
    val opts = recipe.materials.values.filter { e -> e.optionalCons }
    val prod = arrayListOf<RecipeItemStack>()
    val powers = arrayListOf<RecipeItemStack>()
    for (item in recipe.productions.values) {
      if (isPower(item.item)) powers.add(item)
      else prod.add(item)
    }

    val materialNum = mats.size
    val productionNum = prod.size
    hasOptionals = opts.isNotEmpty()
    doubleInput = materialNum > DOUBLE_LIMIT
    doubleOutput = productionNum > DOUBLE_LIMIT

    bound.setZero()

    var wOpt = 0f
    var wMat = 0f
    var wProd = 0f
    var wPow = 0f

    if (hasOptionals) {
      wOpt = handleBound(opts.size, false)
      bound.y += ROW_PAD
    }
    if (materialNum > 0) {
      wMat = handleBound(materialNum, doubleInput)
      bound.y += ROW_PAD
    }
    bound.y += NODE_SIZE
    if (productionNum > 0) {
      bound.y += ROW_PAD
      wProd = handleBound(productionNum, doubleOutput)
    }
    if (powers.any()) {
      bound.y += ROW_PAD
      wPow = handleBound(powers.size, false)
    }

    val offOptX = (bound.x - wOpt)/2
    val offMatX = (bound.x - wMat)/2
    val offProdX = (bound.x - wProd)/2
    val offPowX = (bound.x - wPow)/2

    val centX = bound.x/2f
    var offY = NODE_SIZE/2

    if (hasOptionals) {
      offY = handleNode(opts, consPos, offOptX, offY, isDouble = false, turn = false)
      optPos[bound.x/2] = offY
      offY += ROW_PAD
    }
    if (materialNum > 0) {
      offY = handleNode(mats, consPos, offMatX, offY, doubleInput, false)
      offY += ROW_PAD
    }
    blockPos[centX] = offY
    offY += NODE_SIZE
    if (productionNum > 0) {
      offY += ROW_PAD
      offY = handleNode(prod, prodPos, offProdX, offY, doubleOutput, true)
    }

    if (powers.any()) {
      offY += ROW_PAD
      handleNode(powers, prodPos, offPowX, offY, false, true)
    }

    return bound
  }

  override fun line(from: RecipeNode, to: RecipeNode): LineMeta {
    return if ((isPower(from.stack.item) && from.type == NodeType.PRODUCTION) || (isPower(
        to.stack.item
      ) && to.type == NodeType.PRODUCTION)
    ) LineMeta()
    else super.line(from, to)
  }

  fun isPower(item: RecipeItem<*>): Boolean {
    return powers.contains(item)
  }
}
