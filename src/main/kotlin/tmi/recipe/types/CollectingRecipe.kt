package tmi.recipe.types

import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.scene.style.BaseDrawable
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.util.Time
import arc.util.Tmp
import mindustry.graphics.Pal
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.ui.CellType
import tmi.ui.RecipeView
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

open class CollectingRecipe : RecipeType() {
  override fun RecipeView.BuilderScope.buildRecipeView(
    view: Table,
    recipe: Recipe,
  ) {
    val mats = min(4, ceil(sqrt(normalCons.size.toFloat())).toInt())
    val prods = min(4, ceil(sqrt(productions.size.toFloat())).toInt())

    val m = max(mats, prods)*92f
    val p = if (powerCons.any()) 100f else 0f

    view.table { main ->
      main.table { mat ->
        mat.right()

        mat.table { powers ->
          powerCons.forEach {
            powers.itemCell(CellType.MATERIAL, *it.toTypedArray()).size(80f).pad(6f)
          }
        }.padRight(8f)

        mat.table { matTab ->
          normalCons.forEachIndexed { i, group ->
            if (i > 0 && i%mats == 0) matTab.row()
            matTab.itemCell(CellType.MATERIAL, *group.toTypedArray()).size(80f).pad(6f)
          }
        }.padRight(8f)
      }.width(m + p + 16f)
      main.table { center ->
        val rect = center.clipRect{ ((Time.globalTime%180f)/180f) }

        if (normalCons.any() || boosterCons.any()){
          center.table { booster ->
            booster.bottom()
            if (boosterCons.any()) {
              booster.table { b ->
                boosterCons.forEach { group ->
                  b.itemCell(CellType.MATERIAL, *group.toTypedArray()).size(80f).pad(6f)
                  b.row()
                }
              }
              booster.row()
            }
            booster.image(object: BaseDrawable() {
              override fun draw(x: Float, y: Float, width: Float, height: Float) {
                val s = Scl.scl(18f)

                val centerY = y + height/2f
                val centerX = x + width/2f
                val dx = if (normalCons.any()) x else centerX
                val dw = if (normalCons.any()) width else width - width/2f
                Lines.stroke(Scl.scl(12f))
                val a = Draw.getColorAlpha()

                drawProgress(rect, Color.gray, Pal.accent, a) {
                  Lines.line(dx, centerY, dx + dw - s, centerY)
                  if (boosterCons.any()) Lines.line(centerX, centerY, centerX, y + height)
                  Fill.poly(x + width - s, centerY, 3, s, 0f)
                }
              }
            }).width(80f).height(32f)
          }.width(80f).height(32f).pad(8f)
        }
        center.table { i ->
          i.bottom()

          if (recipe.craftTime > 0) {
            i.timeTab().pad(6f)
            i.row()
          }
          i.itemCell(CellType.BLOCK, ownerBlock).size(120f).pad(8f)
        }.height(136f)
        if (productions.any()) center.image(object: BaseDrawable() {
          override fun draw(x: Float, y: Float, width: Float, height: Float) {
            val centerY = y + height/2f
            val s = Scl.scl(18f)
            Lines.stroke(Scl.scl(12f))

            val a = Draw.getColorAlpha()

            Draw.color(Color.gray, a)

            drawProgress(rect, Color.gray, Pal.accent, a) {
              Lines.line(
                x, y, Tmp.c1.set(Draw.getColor()).a(0f),
                x, centerY, Draw.getColor()
              )
              Lines.line(x, centerY, x + width - s, centerY)
              Fill.poly(x + width - s, centerY, 3, s, 0f)
            }
          }
        }).width(80f).height(80f).pad(8f)
      }
      main.table{ prod ->
        prod.left()
        productions.forEachIndexed { i, p ->
          if (i > 0 && i%prods == 0) prod.row()
          prod.itemCell(CellType.PRODUCTION, p).size(80f).pad(6f)
        }
      }.width(m + p + 16f)

      main.row()

      main.add()
      main.table { attrs ->
        attrs.image().color(Pal.accent).growX().height(4f).padTop(4f).padBottom(4f)
        attrs.row()

        attrs.table { items ->
          attributeCons.forEach { attribute ->
            items.itemCell(CellType.MATERIAL, *attribute.toTypedArray()).size(80f).pad(6f)
          }
        }
      }.growX()
    }.padTop(24f)
  }
}
