package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.defense.turrets.PowerTurret
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.recipe.types.RecipeItemType

class PowerTurretParser: TurretParser<PowerTurret>() {
  override fun isTarget(content: Block): Boolean {
    return content is PowerTurret
  }

  override fun parse(content: PowerTurret): Seq<Recipe> {
    val ammoType = content.shootType
    val baseReload = content.reload + if (!content.reloadWhileCharging) content.shoot.firstShotDelay else 0f
    val recipe = Recipe(
      RecipeType.ammo,
      content.getWrap(),
      baseReload/ammoType.reloadMultiplier
    )

    registerCons(recipe, *content.consumers.filterNot { it == content.coolant }.toTypedArray())

    content.coolant?.also { registerCoolant(recipe, it, 1f, content.coolantMultiplier) }

    recipe.addProductionInteger(
      AmmoRecipeItem(
        content.getWrap(),
        ammoType,
        matchBulletType(ammoType, content.shoot)
      ),
      content.shoot.shots
    ).setType(RecipeItemType.AMMO)

    return Seq.with(recipe)
  }
}