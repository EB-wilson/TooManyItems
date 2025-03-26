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
import mindustry.world.blocks.production.Drill
import mindustry.world.consumers.Consume
import mindustry.world.consumers.ConsumeLiquidBase
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.util.Consts.markerTile
import tmi.util.Utils

open class DrillParser : ConsumerParser<Drill>() {
  protected var itemDrops: ObjectSet<Floor> = ObjectSet()

  override fun init() {
    for (block in Vars.content.blocks()) {
      if (block is Floor && block.itemDrop != null && !block.wallOre) itemDrops.add(block)
    }
  }

  override fun isTarget(content: Block): Boolean {
    return content is Drill
  }

  override fun parse(content: Drill): Seq<Recipe> {
    val res = ObjectMap<Item, Recipe>()

    for (drop in itemDrops) {
      if (drop is OreBlock) markerTile.setOverlay(drop)
      else markerTile.setFloor(drop)

      if (!content.canMine(markerTile)) continue

      val recipe = res.get(drop.itemDrop) {
        val r = Recipe(
          recipeType = RecipeType.collecting,
          ownerBlock = content.getWrap(),
          craftTime = content.getDrillTime(drop.itemDrop)/content.size/content.size,
        ).setEff(Recipe.zeroEff)

        r.addProductionInteger(drop.itemDrop.getWrap(), 1)

        if (content.liquidBoostIntensity != 1f) {
          registerCons(r, *Seq.with(*content.consumers).select { e: Consume -> !(e.optional && e is ConsumeLiquidBase && e.booster) }.toArray(Consume::class.java))

          val consBase = content.findConsumer<Consume> { f: Consume -> f is ConsumeLiquidBase && f.optional && f.booster }
          if (consBase is ConsumeLiquidBase) {
            registerCons(r, { s ->
              val eff = content.liquidBoostIntensity*content.liquidBoostIntensity
              s!!.setEff(eff)
                .setBooster()
                .setOptional()
                .setFormat { f ->
                  val (value, unit) = Utils.unitTimed(f)

                  """
                  ${if (value > 1000) UI.formatAmount(value.toLong()) else Strings.autoFixed(value, 2)}$unit
                  [#98ffa9]+${Mathf.round(eff*100)}%
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
      recipe!!.addMaterial(drop.getWrap(), (content.size*content.size) as Number)
        .setEff(content.drillTime/realDrillTime)
        .setAttribute()
        .emptyFormat()
    }

    return res.values().toSeq()
  }
}
