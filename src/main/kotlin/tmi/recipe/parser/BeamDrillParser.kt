package tmi.recipe.parser

import arc.math.Mathf
import arc.struct.ObjectMap
import arc.struct.ObjectSet
import arc.struct.Seq
import arc.util.Strings
import mindustry.Vars
import mindustry.core.UI
import mindustry.type.Item
import mindustry.world.Block
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.environment.OreBlock
import mindustry.world.blocks.production.BeamDrill
import mindustry.world.consumers.Consume
import mindustry.world.consumers.ConsumeLiquidBase
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.RecipeType
import tmi.util.Consts.markerTile
import tmi.util.Utils

open class BeamDrillParser : ConsumerParser<BeamDrill>() {
  private var itemDrops: ObjectSet<Floor> = ObjectSet()

  override fun init() {
    for (block in Vars.content.blocks()) {
      if (block is Floor && block.wallOre && block.itemDrop != null) itemDrops.add(block)
    }
  }

  override fun isTarget(content: Block): Boolean {
    return content is BeamDrill
  }

  override fun parse(content: BeamDrill): Seq<Recipe> {
    val res = ObjectMap<Item, Recipe>()

    for (drop in itemDrops) {
      if (drop is OreBlock) markerTile.setOverlay(drop)
      else markerTile.setFloor(drop)

      if (drop.itemDrop.hardness > content.tier) continue

      val recipe = res.get(drop.itemDrop) {
        val r = Recipe(
          recipeType = RecipeType.collecting,
          ownerBlock = content.getWrap(),
          craftTime = content.getDrillTime(drop.itemDrop)/content.size,
        ).setEff(Recipe.zeroEff)

        r.addProductionInteger(drop.itemDrop.getWrap(), 1)

        if (content.optionalBoostIntensity != 1f) {
          registerCons(
            r, *Seq.with(*content.consumers).select { e: Consume -> !e.booster }.toArray(
              Consume::class.java
            )
          )
          val consBase = content.findConsumer<Consume> { e: Consume -> e.booster }
          if (consBase is ConsumeLiquidBase) {
            registerCons(r, { s: RecipeItemStack<*> ->
              s.setEff(content.optionalBoostIntensity)
                .setBooster()
                .setOptional()
                .setFormat { f ->
                  val (value, unit) = Utils.unitTimed(f)

                  """
                  ${if (value > 1000) UI.formatAmount(value.toLong()) else Strings.autoFixed(value, 2)}$unit
                  [#98ffa9]+${Mathf.round(content.optionalBoostIntensity*100)}%
                  """.trimIndent()
                }
            }, consBase)
          }
        }
        else {
          registerCons(r, *content.consumers)
        }
        r
      }

      val realDrillTime = content.getDrillTime(drop.itemDrop)
      recipe!!.addMaterial(drop.getWrap(), content.size as Number)
        .setEff(content.drillTime/realDrillTime)
        .setAttribute()
        .emptyFormat()
    }

    return res.values().toSeq()
  }
}
