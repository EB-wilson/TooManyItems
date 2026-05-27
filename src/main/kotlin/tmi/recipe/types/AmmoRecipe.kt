package tmi.recipe.types

import arc.Core
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.scene.style.BaseDrawable
import arc.scene.style.Drawable
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.ObjectMap
import arc.util.Time
import mindustry.ctype.UnlockableContent
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.world.meta.StatValues
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.ui.CellType
import tmi.ui.RecipeView
import tmi.util.Consts
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

class AmmoRecipe: RecipeType() {
  override val name: String
    get() = "ammo"
  override val icon: Drawable
    get() = Icon.turret

  override fun RecipeView.BuilderScope.buildRecipeView(
    view: Table,
    recipe: Recipe,
  ) {
    val ammo = ammoCons
    val bulletType = bulletProd.first().item.item

    val cons = normalCons + isolatedCons.filter { !it.first().isOptional }
    val prod = mainProd + isolatedProd

    val boosters = boosterCons + isolatedCons.filter { it.first().isOptional }

    val mats = min(4, ceil(sqrt(cons.size.toFloat())).toInt())
    val prods = min(4, ceil(sqrt(prod.size.toFloat())).toInt())

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
      }.fill()
      main.table { center ->
        val rect = center.clipRect{ ((Time.globalTime%180f)/180f) }

        if (cons.any()) {
          center.table { left ->
            left.timeTab().height(32f)
            left.row()
            left.image(object : BaseDrawable() {
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
            }).width(140f).height(36f).growX().pad(8f)
            left.row()
            left.add().height(32f)
          }.fill()
        }
        else {
          center.timeTab()
        }

        center.table{ align ->
          align.table { top ->
            top.left()
            top.image(object : BaseDrawable() {
              override fun draw(x: Float, y: Float, width: Float, height: Float) {
                val centerX = x + width/2f
                val centerY = y + height/2f
                val s = Scl.scl(16f)
                Lines.stroke(Scl.scl(12f))
                val a = Draw.getColorAlpha()
                drawProgress(rect, Color.gray, Pal.accent, a) {
                  Lines.line(centerX, y, centerX, centerY)
                  Lines.line(centerX, centerY, x + width - s, centerY)
                  Fill.poly(x + width - s, centerY, 3, s, 0f)
                }
              }
            }).width(80f).growY()
            top.itemCell(CellType.PRODUCTION, bulletProd.first()).size(80f).pad(8f)
          }.left().width(96f)
          align.row()
          align.itemCell(CellType.BLOCK, ownerBlock).size(80f).padLeft(8f).padRight(0f)

          if (ammoCons.any()) {
            align.row()
            align.table { bottom ->
              bottom.right()
              bottom.table { ammos ->
                ammoCons.forEach { ammo ->
                  ammos.itemCell(CellType.MATERIAL, *ammo.toTypedArray()).size(80f).pad(8f)
                  ammos.row()
                }
              }.fill()
              bottom.image(object : BaseDrawable() {
                override fun draw(x: Float, y: Float, width: Float, height: Float) {
                  val centerX = x + width/2f
                  val centerY = y + height/2f
                  val s = Scl.scl(16f)
                  Lines.stroke(Scl.scl(12f))
                  val a = Draw.getColorAlpha()
                  drawProgress(rect, Color.gray, Pal.accent, a) {
                    Lines.line(x, centerY, centerX, centerY)
                    Lines.line(centerX, centerY, centerX, y + height - s)
                    Fill.poly(centerX, y + height - s, 3, s, 90f)
                  }
                }
              }).width(80f).growY()
            }.right().width(96f)
          }
        }
      }.pad(8f)

      val mainHeight = main.prefHeight

      val statTable = Table()
      StatValues.ammo(
        ObjectMap.of(
          bulletProd.first().item.item.let {
            it as? UnlockableContent ?: ownerBlock.item.item
          },
          bulletType
        ), true, false
      ).display(statTable)
      val prefWidth = statTable.prefWidth

      main.table(Consts.darkGrayUI) { desc ->
        desc.top().pane(Styles.smallPane, statTable).pad(8f)
          .width(prefWidth/Scl.scl() + Styles.smallPane.vScroll.minWidth)
          .scrollX(false)
      }.padLeft(96f).fill().height(mainHeight/Scl.scl())
    }.fill()

    view.row()
    view.table{ attr ->
      if (boosters.any()) {
        attr.table { optional ->
          optional.add(Core.bundle["misc.optional"]).color(Pal.accent).pad(6f)
          optional.row()
          optional.image().color(Pal.accent).growX().height(4f).padTop(4f).padBottom(4f)
          optional.row()

          optional.table { items ->
            boosters.forEach { group ->
              items.itemCell(CellType.MATERIAL, *group.toTypedArray()).size(80f).pad(6f)
            }
          }
        }.padLeft(8f).padRight(8f)
      }
    }.growX().fillY()
  }
}