package tmi.recipe.parser

import arc.func.Boolf
import arc.graphics.g2d.TextureRegion
import mindustry.content.Items
import mindustry.ctype.ContentType
import mindustry.entities.bullet.BulletType
import mindustry.entities.bullet.ContinuousLaserBulletType
import mindustry.entities.bullet.LaserBulletType
import mindustry.entities.bullet.LightningBulletType
import mindustry.entities.pattern.ShootPattern
import mindustry.type.Liquid
import mindustry.world.blocks.defense.turrets.Turret
import mindustry.world.consumers.Consume
import mindustry.world.consumers.ConsumeLiquidBase
import tmi.recipe.AmountFormatter
import tmi.recipe.Recipe
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.RecipeItemType
import tmi.util.Consts

abstract class TurretParser<T: Turret>: ConsumerParser<T>() {
  companion object {
    private val bulletTypes = mutableListOf<Pair<Boolf<BulletType>, BulletItemType>>()

    fun registerBulletType(bulletType: BulletItemType, matcher: Boolf<BulletType>) {
      bulletTypes.addFirst(matcher to bulletType)
    }
  }

  protected fun registerCoolant(recipe: Recipe, coolant: ConsumeLiquidBase, base: Float, multiplier: Float) {
    coolant.also { coolant->
      registerCons(recipe, { _, s ->
        val liquid = s.item.item as Liquid
        val eff = base + s.amount*multiplier*liquid.heatCapacity
        s.setType(RecipeItemType.BOOSTER)
          .setEfficiency(eff)
          .setFormat(AmountFormatter.unitTimedFormatter())
          .boostAndConsFormat(eff)
      }, coolant)
    }
  }

  protected fun matchBulletType(bulletType: BulletType, shoot: ShootPattern): BulletItemType {
    bulletTypes.find { it.first.get(bulletType) }?.let { return it.second }

    return when {
      bulletType is LaserBulletType || bulletType is ContinuousLaserBulletType -> if (shoot.shots > 1) DefaultBulletType.CANISTER_LASER_AMMO else DefaultBulletType.LASER_AMMO
      bulletType is LightningBulletType -> DefaultBulletType.LIGHTNING_AMMO
      shoot.shots > 1 -> if (shoot.shotDelay > 0) DefaultBulletType.SPATE_AMMO else DefaultBulletType.CANISTER_AMMO
      else -> DefaultBulletType.NORMAL_AMMO
    }
  }

  class AmmoRecipeItem(
    private val owner: RecipeItem<*>,
    bullet: BulletType,
    val bulletType: BulletItemType
  ): RecipeItem<BulletType>(bullet) {
    override val ordinal: Int = item.id.toInt()
    override val typeOrdinal: Int get() = ContentType.bullet.ordinal
    override val typeID: Int get() = ContentType.bullet.ordinal
    override val name: String = "${owner.name}-${bulletType.name}-$ordinal"
    override val localizedName: String = bulletType.localizedName
    override val icon: TextureRegion = bulletType.icon
    override val hidden: Boolean = true
    override val hasDetails: Boolean get() = owner.hasDetails
    override val locked: Boolean get() = owner.locked
  }

  interface BulletItemType {
    val name: String
    val localizedName: String
    val icon: TextureRegion
  }

  enum class DefaultBulletType(
    override val localizedName: String,
    override val icon: TextureRegion
  ) : BulletItemType {
    NORMAL_AMMO("", Consts.ammo_normal),
    SPATE_AMMO("", Consts.ammo_spate),
    CANISTER_AMMO("", Consts.ammo_canister),
    LASER_AMMO("", Consts.ammo_laser),
    CANISTER_LASER_AMMO("", Consts.ammo_canister_laser),
    LIGHTNING_AMMO("", Consts.ammo_lightning),
  }
}