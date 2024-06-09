package tmi.recipe.parser

import arc.struct.ObjectMap
import arc.struct.ObjectSet
import arc.struct.Seq
import mindustry.Vars
import mindustry.type.Liquid
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.production.Pump
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.util.Consts.markerTile
import java.lang.reflect.InvocationTargetException

class PumpParser : ConsumerParser<Pump>() {
  private val floorDrops: ObjectSet<Floor> = ObjectSet()

  override fun init() {
    for (block in Vars.content.blocks()) {
      if (block is Floor && block.liquidDrop != null) floorDrops.add(block)
    }
  }

  override fun isTarget(content: Block): Boolean {
    return content is Pump
  }

  override fun parse(content: Pump): Seq<Recipe> {
    val res = ObjectMap<Liquid, Recipe>()
    for (drop in floorDrops) {
      markerTile.setFloor(drop)
      try {
        if (!(canPump.invoke(content, markerTile) as Boolean)) continue
      } catch (e: IllegalAccessException) {
        throw RuntimeException(e)
      } catch (e: InvocationTargetException) {
        throw RuntimeException(e)
      }

      val recipe = res[drop.liquidDrop, {
        val r = Recipe(
          recipeType = RecipeType.collecting,
          craftTime = content.consumeTime,
          ownerBlock = getWrap(content)
        ).setEff(Recipe.zeroEff)

        r.addProductionPersec(getWrap(drop.liquidDrop), content.pumpAmount)
        registerCons(r, *content.consumers)
        r
      }]

      recipe!!.addMaterial(getWrap(drop), (content.size*content.size) as Number)
        .setAttribute()
        .emptyFormat()
    }
    return res.values().toSeq()
  }

  companion object {
    private val canPump by lazy {
      try {
        val res = Pump::class.java.getDeclaredMethod("canPump", Tile::class.java)
        res.setAccessible(true)
        return@lazy res
      } catch (e: NoSuchMethodException) {
        throw RuntimeException(e)
      }
    }
  }
}
