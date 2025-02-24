package tmi.recipe.types

import arc.Core
import arc.func.Prov
import arc.graphics.Color
import arc.math.Mathf
import arc.math.geom.Vec2
import arc.scene.Group
import arc.scene.ui.Label
import arc.scene.ui.layout.Scl
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Align
import arc.util.Strings
import arc.util.Time
import arc.util.Tmp
import mindustry.core.UI
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.world.meta.Stat
import mindustry.world.meta.StatUnit
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.RecipeType
import tmi.set
import tmi.ui.NODE_SIZE
import tmi.ui.NodeType
import tmi.ui.RecipeNode
import tmi.ui.RecipeView.LineMeta
import kotlin.math.max

open class FactoryRecipe : RecipeType() {
  val bound: Vec2 = Vec2()
  val blockPos: Vec2 = Vec2()
  val consPos: ObjectMap<RecipeItem<*>, Vec2> = ObjectMap()
  val prodPos: ObjectMap<RecipeItem<*>, Vec2> = ObjectMap()
  val optPos: Vec2 = Vec2()

  var doubleInput: Boolean = false
  var doubleOutput: Boolean = false
  var hasOptionals: Boolean = false
  var time: Float = 0f

  override val id = 1

  override fun buildView(view: Group) {
    val label = Label(Core.bundle["misc.factory"], Styles.outlineLabel)
    label.validate()

    label.setPosition(blockPos.x + NODE_SIZE/2 + ITEM_PAD + label.prefWidth/2, blockPos.y, Align.center)
    view.addChild(label)

    buildOpts(view)
    buildTime(view, label.height)
  }

  protected fun buildTime(view: Group?, offY: Float) {
    if (time > 0) {
      val time = Label(
        Stat.productionTime.localized() + ": " + (if (this.time > 3600) UI.formatTime(
          this.time
        )
        else Strings.autoFixed(this.time/60, 2) + StatUnit.seconds.localized()), Styles.outlineLabel
      )
      time.validate()

      time.setPosition(
        blockPos.x + NODE_SIZE/2 + ITEM_PAD + time.prefWidth/2,
        blockPos.y - offY - 4,
        Align.center
      )
      view!!.addChild(time)
    }
  }

  protected fun buildOpts(view: Group) {
    if (!hasOptionals) return

    val optionals = Label(Core.bundle["misc.optional"], Styles.outlineLabel)
    optionals.setColor(Pal.accent)
    optionals.validate()
    optionals.setPosition(optPos.x, optPos.y - optionals.height, Align.center)
    view.addChild(optionals)
  }

  override fun initial(recipe: Recipe): Vec2 {
    time = recipe.craftTime

    consPos.clear()
    prodPos.clear()
    optPos.setZero()
    blockPos.setZero()

    val mats: List<RecipeItemStack> =
      recipe.materials.values().filter { e -> !e.optionalCons }
    val opts: List<RecipeItemStack> =
      recipe.materials.values().filter { e -> e.optionalCons }
    val materialNum = mats.size
    val productionNum = recipe.productions.size
    hasOptionals = opts.isNotEmpty()
    doubleInput = materialNum > DOUBLE_LIMIT
    doubleOutput = productionNum > DOUBLE_LIMIT

    bound.setZero()

    var wOpt = 0f
    var wMat = 0f
    var wProd = 0f

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

    val offOptX = (bound.x - wOpt)/2
    val offMatX = (bound.x - wMat)/2
    val offProdX = (bound.x - wProd)/2

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
      val seq: List<RecipeItemStack> = recipe.productions.values().toList()
      handleNode(seq, prodPos, offProdX, offY, doubleOutput, true)
    }

    return bound
  }

  protected fun handleNode(
    seq: List<RecipeItemStack>,
    pos: ObjectMap<RecipeItem<*>, Vec2>,
    offX: Float,
    offY: Float,
    isDouble: Boolean,
    turn: Boolean
  ): Float {
    var yOff = offY
    var dx = NODE_SIZE/2
    if (isDouble) {
      for (i in seq.indices) {
        if (turn) {
          if (i%2 == 0) pos[seq[i].item] = Vec2(offX + dx, yOff + NODE_SIZE + ITEM_PAD)
          else pos[seq[i].item] = Vec2(offX + dx, yOff)
        }
        if (i%2 == 0) pos[seq[i].item] = Vec2(offX + dx, yOff)
        else pos[seq[i].item] = Vec2(offX + dx, yOff + NODE_SIZE + ITEM_PAD)

        dx += NODE_SIZE/2 + ITEM_PAD
      }
      yOff += NODE_SIZE*2 + ITEM_PAD
    }
    else {
      for (i in 0 until seq.size) {
        pos[seq[i].item] = Vec2(offX + dx, yOff)
        dx += NODE_SIZE + ITEM_PAD
      }
      yOff += NODE_SIZE
    }
    return yOff
  }

  protected fun handleBound(num: Int, isDouble: Boolean): Float {
    var res: Float
    if (isDouble) {
      val n = Mathf.ceil(num/2f)
      bound.x = max(bound.x.toDouble(),
                    (NODE_SIZE*n + 2*ITEM_PAD*(n - 1) + (1 - num%2)*(NODE_SIZE/2 + ITEM_PAD/2)).also {
                      res = it
                    }
                      .toDouble()
      ).toFloat()
      bound.y += NODE_SIZE*2 + ITEM_PAD
    }
    else {
      bound.x = max(
        bound.x.toDouble(),
        (NODE_SIZE*num + ITEM_PAD*(num - 1)).also {
          res = it
        }.toDouble()
      ).toFloat()
      bound.y += NODE_SIZE
    }

    return res
  }

  override fun layout(recipeNode: RecipeNode) {
    when (recipeNode.type) {
      NodeType.MATERIAL -> {
        val pos = consPos[recipeNode.stack.item]
        recipeNode.setPosition(pos.x, pos.y, Align.center)
      }
      NodeType.PRODUCTION -> {
        val pos = prodPos[recipeNode.stack.item]
        recipeNode.setPosition(pos.x, pos.y, Align.center)
      }
      NodeType.BLOCK -> {
        recipeNode.setPosition(blockPos.x, blockPos.y, Align.center)
      }
    }
  }

  override fun line(from: RecipeNode, to: RecipeNode): LineMeta {
    val res = LineMeta()

    if (from.stack.optionalCons) return res

    res.color = if (from.type == NodeType.MATERIAL) Prov {
      Tmp.c1.set(Color.gray).lerp(
        Pal.accent, Mathf.pow(
          Mathf.absin(
            Time.globalTime/8 + Mathf.pi, 1f, 1f
          ), 3f
        )
      )
    }
    else Prov {
      Tmp.c1.set(Color.gray).lerp(
        Pal.accent, Mathf.pow(
          Mathf.absin(
            Time.globalTime/8, 1f, 1f
          ), 3f
        )
      )
    }

    val offX = from.width/2
    val offY = from.height/2
    val offX1 = to.width/2
    val offY1 = to.height/2

    val off = if ((to.y - from.y) > 0) -ROW_PAD/2 - NODE_SIZE/2 else ROW_PAD/2 + NODE_SIZE/2
    if (from.x != to.x) {
      res.addVertex(from.x + offX, from.y + offY)
      res.addVertex(from.x + offX, to.y + offY1 + off)
      res.addVertex(to.x + offX1, to.y + offY1 + off)
      res.addVertex(to.x + offX1, to.y + offY1)
    }
    else {
      res.addVertex(from.x + offX, from.y + offY)
      res.addVertex(to.x + offX1, to.y + offY1)
    }

    return res
  }

  companion object {
    val ROW_PAD: Float = Scl.scl(60f)
    val ITEM_PAD: Float = Scl.scl(10f)
    const val DOUBLE_LIMIT: Int = 5
  }
}
