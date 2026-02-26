package tmi.recipe.parser

import arc.struct.ObjectMap
import arc.struct.ObjectSet
import arc.struct.Seq
import mindustry.Vars
import mindustry.type.Item
import mindustry.world.Block
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.environment.OreBlock
import mindustry.world.blocks.production.Drill
import mindustry.world.consumers.Consume
import mindustry.world.consumers.ConsumeLiquidBase
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemGroup
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeType
import tmi.util.Consts.markerTile

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
    val oreGroup = ObjectMap<Item, RecipeItemGroup>()

    for (drop in itemDrops) {
      if (drop is OreBlock) markerTile.setOverlay(drop)
      else markerTile.setFloor(drop)

      if (!content.canMine(markerTile)) continue

      val recipe = res.get(drop.itemDrop) {
        val r = Recipe(
          recipeType = RecipeType.collecting,
          ownerBlock = content.getWrap(),
          craftTime = content.getDrillTime(drop.itemDrop)/content.size/content.size,
        ).setBaseEff(0f)

        r.addProductionInteger(drop.itemDrop.getWrap(), 1)

        if (content.liquidBoostIntensity != 1f) {
          registerCons(r, *Seq.with(*content.consumers).select { e ->
            !(e.optional && e is ConsumeLiquidBase && e.booster)
          }.toArray(Consume::class.java))

          val consBase = content.findConsumer<Consume> { f -> f is ConsumeLiquidBase && f.optional && f.booster }
          if (consBase is ConsumeLiquidBase) {
            registerCons(r, { s ->
              val eff = content.liquidBoostIntensity*content.liquidBoostIntensity
              s!!.setEfficiency(eff)
                .setType(RecipeItemType.BOOSTER)
                .setOptional()
                .boostAndConsFormat(eff)
            }, consBase)
          }
        }
        else {
          registerCons(r, *content.consumers)
        }
        r
      }

      recipe!!.addMaterial(drop.getWrap(), (content.size*content.size) as Number)
        .setType(RecipeItemType.ATTRIBUTE)
        .emptyFormat()
        .setGroup(oreGroup.get(drop.itemDrop){ RecipeItemGroup() })
    }

    return res.values().toSeq()
  }
}
