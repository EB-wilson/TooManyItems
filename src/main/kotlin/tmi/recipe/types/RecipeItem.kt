package tmi.recipe.types

import arc.graphics.g2d.TextureRegion
import java.util.*

abstract class RecipeItem<T> protected constructor(@JvmField val item: T) : Comparable<RecipeItem<*>> {
  abstract fun ordinal(): Int
  abstract fun typeID(): Int
  abstract fun name(): String
  abstract fun localizedName(): String
  abstract fun icon(): TextureRegion
  abstract fun hidden(): Boolean

  open fun hasDetails() = false
  open fun locked() = false

  open fun displayDetails() {}

  override fun compareTo(other: RecipeItem<*>): Int {
    val n = typeID().compareTo(other.typeID())

    if (n == 0) {
      return ordinal() - other.ordinal()
    }

    return n
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val that = other as RecipeItem<*>
    return item == that.item
  }

  override fun hashCode(): Int {
    return Objects.hash(name())
  }
}
