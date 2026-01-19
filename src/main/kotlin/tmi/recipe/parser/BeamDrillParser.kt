package tmi.recipe.parser

import arc.struct.ObjectMap
import arc.struct.ObjectSet
import arc.struct.Seq
import mindustry.Vars
import mindustry.type.Item
import mindustry.world.Block
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.environment.OreBlock
import mindustry.world.blocks.production.BeamDrill
import mindustry.world.consumers.Consume
import mindustry.world.consumers.ConsumeLiquidBase
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemGroup
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeType
import tmi.util.Consts.markerTile

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
    val oreGroup = ObjectMap<Item, RecipeItemGroup>()

    for (drop in itemDrops) {
      if (drop is OreBlock) markerTile.setOverlay(drop)
      else markerTile.setFloor(drop)

      if (drop.itemDrop.hardness > content.tier) continue

      val recipe = res.get(drop.itemDrop) {
        val r = Recipe(
          recipeType = RecipeType.collecting,
          ownerBlock = content.getWrap(),
          craftTime = content.getDrillTime(drop.itemDrop)/content.size,
        ).setBaseEff(0f)

        r.addProductionInteger(drop.itemDrop.getWrap(), 1)

        if (content.optionalBoostIntensity != 1f) {
          registerCons(
            r, *Seq.with(*content.consumers).select { e ->
              !e.booster || e !is ConsumeLiquidBase
            }.toArray(
              Consume::class.java
            )
          )
          val consBase = content.findConsumer<ConsumeLiquidBase> { e -> e.booster && e is ConsumeLiquidBase }
          registerCons(r, { s ->
            s.setEff(content.optionalBoostIntensity)
              .setType(RecipeItemType.BOOSTER)
              .setOptional()
              .boostAndConsFormat(content.optionalBoostIntensity)
          }, consBase)
        }
        else {
          registerCons(r, *content.consumers)
        }
        r
      }

      val realDrillTime = content.getDrillTime(drop.itemDrop)
      recipe!!.addMaterial(drop.getWrap(), content.size as Number)
        .setEff(content.drillTime/realDrillTime)
        .setType(RecipeItemType.ATTRIBUTE)
        .emptyFormat()
        .setGroup(oreGroup.get(drop.itemDrop){ RecipeItemGroup() })
    }

    return res.values().toSeq()
  }
}
