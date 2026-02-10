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
import mindustry.gen.Icon
import mindustry.graphics.Pal
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.ui.CellType
import tmi.ui.RecipeView
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class GeneratorRecipe : RecipeType() {
  override fun RecipeView.BuilderScope.buildRecipeView(
    view: Table,
    recipe: Recipe,
  ) {
    val realCons = normalCons + isolatedCons.filter { !it.first().isOptional }
    val realProd = mainProd + isolatedProd
    val boosters = boosterCons + isolatedCons.filter { it.first().isOptional }

    val mats = min(4, ceil(sqrt(realCons.size.toFloat())).toInt())
    val prods = min(4, ceil(sqrt(realProd.size.toFloat())).toInt())

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
          realCons.forEachIndexed { i, group ->
            if (i > 0 && i%mats == 0) matTab.row()
            matTab.itemCell(CellType.MATERIAL, *group.toTypedArray()).size(80f).pad(6f)
          }
        }.padRight(8f)
      }.width(m + p + 16f)
      main.table { center ->
        val rect = center.clipRect{ ((Time.globalTime%180f)/180f) }

        if (realCons.any() || boosters.any()){
          center.table { booster ->
            booster.bottom()
            if (boosters.any()) {
              booster.table { b ->
                boosters.forEach { group ->
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
                val dx = if (realCons.any()) x else centerX
                val dw = if (realCons.any()) width else width - width/2f
                Lines.stroke(Scl.scl(12f))
                val a = Draw.getColorAlpha()

                drawProgress(rect, Color.gray, Pal.accent, a) {
                  Lines.line(dx, centerY, dx + dw - s, centerY)
                  if (boosters.any()) Lines.line(centerX, centerY, centerX, y + height)
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
        center.table { out ->
          out.top()

          out.image(object : BaseDrawable() {
            override fun draw(x: Float, y: Float, width: Float, height: Float) {
              val centerX = x + width/2f
              val centerY = y + height/2f
              val s = Scl.scl(18f)
              Lines.stroke(Scl.scl(12f))

              val a = Draw.getColorAlpha()

              Draw.color(Color.gray, a)

              drawProgress(rect, Color.gray, Pal.accent, a) {
                Lines.line(x, centerY, x + width - s, centerY)
                Fill.poly(x + width - s, centerY, 3, s, 0f)

                if (sideProd.any() || garbage.any()) {
                  Lines.line(centerX, y - height + s, centerX, centerY)
                  Fill.poly(centerX, y - height + s, 3, s, 30f)
                }
              }
            }
          }).minWidth(80f).height(32f).growX()

          if (sideProd.any() || garbage.any()) {
            out.row()
            out.table { sub ->
              sideProd.forEach { p ->
                sub.itemCell(CellType.PRODUCTION, p).size(80f).pad(6f)
              }

              garbage.forEach { p ->
                sub.stack(
                  Table{ it.itemCell(CellType.MATERIAL, p).size(80f).pad(6f)},
                  Table{ it.top().right().image(Icon.warningSmall).size(24f).pad(8f) }
                )
              }
            }.fill().padTop(32f)
          }
        }.height(32f).fillX().pad(8f)
      }
      main.table{ out ->
        out.left()

        out.table { prod ->
          realProd.forEachIndexed { i, p ->
            if (i > 0 && i%prods == 0) prod.row()
            prod.itemCell(CellType.PRODUCTION, p).size(80f).pad(6f)
          }
        }.padLeft(8f)

        out.table { power ->
          powerProd.forEach { p ->
            power.itemCell(CellType.PRODUCTION, p).size(80f).pad(6f)
            power.row()
          }
        }.padLeft(8f)
      }.width(m + p + 16f)

      if (attributeCons.any()) {
        val optionalAttrs = attributeCons.filter { it.first().isOptional }
        val nonOptAttrs = attributeCons.filter { !it.first().isOptional }

        main.row()
        main.table { attr ->
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
            }.pad(8f)
          }

          if (optionalAttrs.any()) {
            attr.table { optional ->
              optional.add(Core.bundle["misc.optional"]).color(Pal.accent).pad(6f)
              optional.row()
              optional.image().color(Pal.accent).growX().height(4f).padTop(4f).padBottom(4f)
              optional.row()

              optional.table { items ->
                optionalAttrs.forEach { attribute ->
                  items.itemCell(CellType.MATERIAL, *attribute.toTypedArray()).size(80f).pad(6f)
                }
              }
            }.pad(8f)
          }
        }.growX().colspan(3)
      }
      else if (sideProd.any() || garbage.any()) {
        main.row()
        main.add().height(40f)
      }
    }.padTop(24f)
  }
}
