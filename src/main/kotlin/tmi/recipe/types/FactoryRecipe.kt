package tmi.recipe.types

import arc.Core
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.scene.style.BaseDrawable
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.util.Time
import mindustry.graphics.Pal
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.ui.CellType
import tmi.ui.RecipeView
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

open class FactoryRecipe : RecipeType() {
  override fun RecipeView.BuilderScope.buildRecipeView(
    view: Table,
    recipe: Recipe
  ) {
    val cons = normalCons + isolatedCons.filter { !it.first().isOptional }
    val boosters = boosterCons + isolatedCons.filter { it.first().isOptional }
    val prod = mainProd + isolatedProd
    val subProd = sideProd + garbage
    val nonOptAttrs = attributeCons.filter { !it.first().isOptional }
    val optionalAttrs = attributeCons.filter { it.first().isOptional }

    val mats = min(4, ceil(sqrt(cons.size.toFloat())).toInt())
    val prods = min(4, ceil(sqrt(prod.size.toFloat())).toInt())
    val w = max(mats, prods)*92f
    val p = if (powerCons.any() || powerProd.any()) 100f else 0f

    view.table { main ->
      main.table { input ->
        input.right()
        if (powerCons.any()) {
          input.table { powers ->
            powerCons.forEach {
              powers.itemCell(CellType.MATERIAL, *it.toTypedArray()).size(80f).pad(6f)
              powers.row()
            }
          }.padRight(8f)
        }
        input.table { mat ->
          cons.forEachIndexed { i, group ->
            if (i > 0 && i%mats == 0) mat.row()
            mat.itemCell(CellType.MATERIAL, *group.toTypedArray()).size(80f).pad(6f)
          }
        }
      }.width(w + p)
      main.table { center ->
        val rect = center.clipRect{ ((Time.globalTime%180f)/180f) }

        center.itemCell(CellType.BLOCK, ownerBlock)
          .size(80f).pad(8f)
        center.row()
        center.image(object : BaseDrawable() {
          override fun draw(x: Float, y: Float, width: Float, height: Float) {
            val centerY = y + height/2f
            val s = Scl.scl(24f)
            Lines.stroke(Scl.scl(12f))
            val a = Draw.getColorAlpha()

            drawProgress(rect, Color.gray, Pal.accent, a) {
              Lines.line(x, centerY, x + width - s, centerY)
              Fill.poly(x + width - s, centerY, 3, s, 0f)
            }
          }
        }).minWidth(140f).height(36f).growX().pad(8f).padBottom(0f)
        center.row()
        center.table { under ->
          under.top().defaults().top()

          if (recipe.craftTime > 0) under.timeTab().pad(8f).padTop(18f)

          if (subProd.any()) {
            under.table { garb ->
              garb.image(object : BaseDrawable() {
                override fun draw(x: Float, y: Float, width: Float, height: Float) {
                  val a = Draw.getColorAlpha()

                  drawProgress(rect, Color.gray, Pal.accent, a) {
                    Fill.rect(x + width/2, y + height/2, width, height)
                  }
                }
              }).size(12f, 40f)
              garb.row()
              garb.table { tab ->
                subProd.forEachIndexed { i, stack ->
                  if (i > 0 && i%2 == 0) tab.row()
                  tab.itemCell(CellType.MATERIAL, stack).size(80f).pad(6f).padTop(0f)
                }
              }
            }.fill().padLeft(8f).padRight(8f)
          }
        }.height(112f).pad(8f).padTop(-16f)
      }.pad(8f)
      main.table { output ->
        output.left()
        output.table { productions ->
          prod.forEachIndexed { i, mat ->
            if (i > 0 && i%prods == 0) productions.row()
            productions.itemCell(CellType.PRODUCTION, mat).size(80f).pad(6f)
          }
        }
        if (powerProd.any()) {
          output.table { powers ->
            powerProd.forEach {
              powers.itemCell(CellType.MATERIAL, it).size(80f).pad(6f)
              powers.row()
            }
          }.padLeft(8f)
        }
      }.width(w + p)
    }.fill()
    view.row()
    view.table{ attr ->
      if (nonOptAttrs.any()) {
        attr.table { nonOpt ->
          nonOpt.add(Core.bundle["misc.environment"]).color(Pal.accent).pad(6f)
          nonOpt.row()
          nonOpt.image().color(Pal.accent).growX().height(4f).padTop(4f).padBottom(4f)
          nonOpt.row()

          nonOpt.table { items ->
            nonOptAttrs.forEach { attribute ->
              items.itemCell(CellType.MATERIAL, *attribute.toTypedArray()).size(80f).pad(6f)
            }
          }
        }
      }

      if (optionalAttrs.any() || boosters.any()) {
        attr.table { optional ->
          optional.add(Core.bundle["misc.optional"]).color(Pal.accent).pad(6f)
          optional.row()
          optional.image().color(Pal.accent).growX().height(4f).padTop(4f).padBottom(4f)
          optional.row()

          optional.table { items ->
            boosters.forEach { group ->
              items.itemCell(CellType.MATERIAL, *group.toTypedArray()).size(80f).pad(6f)
            }

            optionalAttrs.forEachIndexed { i, attribute ->
              items.itemCell(CellType.MATERIAL, *attribute.toTypedArray()).size(80f).pad(6f).also {
                if (i == 0 && boosters.any()) it.padLeft(12f)
              }
            }
          }
        }.padLeft(8f).padRight(8f)
      }
    }.growX().fillY().padTop(-24f)
  }
}
