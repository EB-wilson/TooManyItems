package tmi.recipe.types

import arc.Core
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.graphics.g2d.ScissorStack
import arc.math.geom.Rect
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
        }
      }.width(m + p)
      main.table { center ->
        val rect = Rect()

        center.fill { x, y, width, height ->
          val d = ((Time.globalTime%180f)/180f)*width + 8
          val v1 = Tmp.v1.set(x - 8, y)
          val v2 = Tmp.v2.set(v1).add(d, height)
          val trans = Draw.trans()
          Core.scene.viewport.calculateScissors(
            trans,
            Tmp.r1.set(v1.x, v1.y, v2.x - v1.x, v2.y - v1.y),
            rect
          )
        }

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

                Draw.color(Color.gray)
                Lines.line(dx, centerY, dx + dw - s, centerY)
                if (boosterCons.any()) Lines.line(centerX, centerY, centerX, y + height)
                Fill.poly(x + width - s, centerY, 3, s, 0f)

                if (ScissorStack.push(rect)) {
                  Draw.color(Pal.accent)
                  Lines.line(dx, centerY, dx + dw - s, centerY)
                  if (boosterCons.any()) Lines.line(centerX, centerY, centerX, y + height)
                  Fill.poly(x + width - s, centerY, 3, s, 0f)
                  ScissorStack.pop()
                }
              }
            }).width(80f).height(32f)
          }.height(32f)
        }
        center.table { i ->
          i.bottom()
          i.timeTab().pad(6f)
          i.row()
          i.itemCell(CellType.BLOCK, ownerBlock).size(120f).pad(8f)
        }.height(136f)
        if (productions.any()) center.image(object: BaseDrawable() {
          override fun draw(x: Float, y: Float, width: Float, height: Float) {
            val centerY = y + height/2f
            val s = Scl.scl(18f)
            Lines.stroke(Scl.scl(12f))

            Draw.color(Color.gray)
            Lines.line(x, y, Tmp.c1.set(Color.gray).a(0f), x, centerY, Tmp.c2.set(Color.gray))
            Lines.line(x, centerY, x + width - s, centerY)
            Fill.poly(x + width - s, centerY, 3, s, 0f)

            if (ScissorStack.push(rect)) {
              Draw.color(Pal.accent)
              Lines.line(x, y, Tmp.c1.set(Pal.accent).a(0f), x, centerY, Tmp.c2.set(Pal.accent))
              Lines.line(x, centerY, x + width - s, centerY)
              Fill.poly(x + width - s, centerY, 3, s, 0f)
              ScissorStack.pop()
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
      }.width(m + p)

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
