package tmi.recipe.parser

import arc.struct.Seq
import mindustry.type.Liquid
import mindustry.world.Block
import mindustry.world.blocks.defense.turrets.ContinuousLiquidTurret
import mindustry.world.blocks.defense.turrets.ItemTurret
import mindustry.world.blocks.defense.turrets.LiquidTurret
import mindustry.world.consumers.ConsumeItemFilter
import mindustry.world.consumers.ConsumeLiquidFilter
import tmi.recipe.Recipe
import tmi.recipe.RecipeParser
import tmi.recipe.RecipeType
import tmi.recipe.types.RecipeItemType

class ContinuousLiquidTurretParser: TurretParser<ContinuousLiquidTurret>() {
  override val excludes: Seq<Class<out RecipeParser<*>>> = Seq.with(
    ContinuousTurretParser::class.java
  )

  override fun isTarget(content: Block): Boolean {
    return content is ContinuousLiquidTurret
  }

  override fun parse(content: ContinuousLiquidTurret): Seq<Recipe> {
    val res = Seq<Recipe>()

    content.ammoTypes.each { liquid, ammoType ->
      val baseReload = content.reload + if (!content.reloadWhileCharging) content.shoot.firstShotDelay else 0f
      val recipe = Recipe(
        RecipeType.ammo,
        content.getWrap(),
        baseReload/ammoType.reloadMultiplier
      )

      registerCons(recipe, *content.consumers.filterNot {
        it == content.coolant
        || (it is ConsumeLiquidFilter
          && it::class.java.isAnonymousClass
          && it::class.java.enclosingMethod.declaringClass == ContinuousLiquidTurret::class.java)
      }.toTypedArray())

      recipe.addMaterialPersec(
        liquid.getWrap(),
        content.liquidConsumed*(if (content.consumeAmmoOnce) 1 else content.shoot.shots)*content.ammoPerShot/ammoType.ammoMultiplier
      ).setType(RecipeItemType.AMMO)

      content.coolant?.also { registerCoolant(recipe, it, 1f, content.coolantMultiplier) }

      recipe.addProductionInteger(
        AmmoRecipeItem(
          content.getWrap(),
          ammoType,
          matchBulletType(ammoType, content.shoot)
        ),
        content.shoot.shots
      ).setType(RecipeItemType.AMMO)

      res.add(recipe)
    }

    return res
  }
}