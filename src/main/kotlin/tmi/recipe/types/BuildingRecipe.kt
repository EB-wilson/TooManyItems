package tmi.recipe.types

import arc.Core
import arc.func.Prov
import arc.graphics.Color
import arc.math.Angles
import arc.math.Mathf
import arc.math.Rand
import arc.math.geom.Vec2
import arc.scene.Group
import arc.scene.ui.ImageButton
import arc.scene.ui.Label
import arc.scene.ui.layout.Scl
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Align
import arc.util.Log
import arc.util.Strings
import mindustry.Vars
import mindustry.core.UI
import mindustry.gen.Icon
import mindustry.ui.Styles
import mindustry.world.Block
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
import tmi.util.Consts
import kotlin.math.max

class BuildingRecipe : RecipeType() {
  val bound = Vec2()
  val blockPos = Vec2()
  val materialPos = ObjectMap<RecipeItem<*>, Vec2>()

  var time = 0f
  var build: Block? = null

  override val id = 0

  override fun buildView(view: Group) {
    val label = Label(Core.bundle["misc.building"], Styles.outlineLabel)
    label.style.background = Consts.grayUIAlpha
    label.validate()

    label.setPosition(blockPos.x + NODE_SIZE/2 + ITEM_PAD + label.prefWidth/2, blockPos.y, Align.center)
    view.addChild(label)

    if (time > 0) {
      val time = Label(
        Stat.buildTime.localized() + ": " + (if (this.time > 3600) UI.formatTime(this.time)
        else Strings.autoFixed(
          this.time/60, 2
        ) + StatUnit.seconds.localized()), Styles.outlineLabel
      )
      time.style.background = Consts.grayUIAlpha
      time.validate()

      time.setPosition(
        blockPos.x + NODE_SIZE/2 + ITEM_PAD + time.prefWidth/2,
        blockPos.y - label.height - 4,
        Align.center
      )
      view.addChild(time)
    }

    if (Vars.state.isGame) {
      val button = ImageButton(Icon.hammer, Styles.clearNonei)

      button.setDisabled {
        build == null || !build!!.unlockedNow() || !build!!.placeablePlayer || !build!!.environmentBuildable() || !build!!.supportsEnv(
          Vars.state.rules.env
        )
      }
      button.clicked {
        while (Core.scene.hasDialog()) {
          Core.scene.dialog.hide()
        }
        Vars.control.input.block = build
      }
      button.margin(5f)
      button.setSize(40f)
      button.setPosition(bound.x, 0f, Align.topRight)
      view.addChild(button)
    }
  }

  override fun initial(recipe: Recipe): Vec2 {
    build = recipe.ownerBlock!!.item as Block
    time = recipe.craftTime

    bound.setZero()
    blockPos.setZero()
    materialPos.clear()

    val seq: List<RecipeItemStack> = recipe.materials.values().toList()
    val radians = 2f*Mathf.pi/seq.size
    val radius = max(
      MIN_RAD.toDouble(),
      ((NODE_SIZE + ITEM_PAD)/radians).toDouble()
    ).toFloat()

    bound.set(radius + NODE_SIZE, radius + NODE_SIZE).scl(2f)
    blockPos.set(bound).scl(0.5f)

    val r = Rand(build!!.id.toLong())
    val off = r.random(0f, 360f)
    for (i in seq.indices) {
      val angle = radians*i*Mathf.radDeg + off
      val rot = r.random(0f, RAND) + radius

      materialPos[seq[i].item] = Vec2(blockPos.x + Angles.trnsx(angle, rot), blockPos.y + Angles.trnsy(angle, rot))
    }

    return bound
  }

  override fun layout(recipeNode: RecipeNode) {
    when (recipeNode.type) {
      NodeType.MATERIAL -> {
        val pos = materialPos[recipeNode.stack.item]
        recipeNode.setPosition(pos.x, pos.y, Align.center)
      }
      NodeType.BLOCK -> {
        recipeNode.setPosition(blockPos.x, blockPos.y, Align.center)
      }
      else -> Log.warn("unexpected production in building recipe")
    }
  }

  override fun line(from: RecipeNode, to: RecipeNode): LineMeta {
    val res = LineMeta()
    res.color = Prov { Color.gray }

    val offX = from.width/2
    val offY = from.height/2
    val offX1 = to.width/2
    val offY1 = to.height/2

    res.addVertex(from.x + offX, from.y + offY)
    res.addVertex(to.x + offX1, to.y + offY1)

    return res
  }

  companion object {
    val ITEM_PAD = Scl.scl(30f)
    val RAND = Scl.scl(65f)
    val MIN_RAD = Scl.scl(125f)
  }
}
