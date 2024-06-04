package tmi.ui.designer

import arc.Core
import arc.graphics.*
import arc.math.Mathf
import arc.scene.ui.*
import arc.scene.ui.layout.Scl
import arc.util.*
import mindustry.core.UI
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.recipe.RecipeType
import tmi.recipe.types.GeneratorRecipe
import tmi.util.Consts
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

@Deprecated("Responsive structures don't require this work")
class BalanceDialog(ownerDesigner: SchematicDesignerDialog) : Dialog("", Consts.transparentBack) {
  private var currCard: RecipeCard? = null

  private var balanceAmount = 0
  private var balanceValid = false

  private var rebuild = Runnable {}

  init {
    titleTable.clear()

    val cell = cont.table(Consts.darkGrayUIAlpha) { t ->
      t.table(Consts.darkGrayUI) { top ->
        top.left().add(
          Core.bundle["dialog.calculator.balance"]
        ).pad(8f)
      }.grow().padBottom(12f)
      t.row()
      t.table(Consts.darkGrayUI).grow().margin(12f).get().top().pane(Styles.smallPane) { inner ->
        shown(Runnable {
          inner.clearChildren()
          inner.defaults().left().growX().fillY().pad(5f)
          currCard = ownerDesigner.currPage!!.view.selects.run { if (size == 1 && first() is RecipeCard) first() as RecipeCard else null }

          inner.add(Core.bundle["dialog.calculator.targetRec"]).color(Pal.accent)
          if (currCard != null) {
            currCard!!.calculateEfficiency()

            inner.row()
            inner.table(Tex.pane) { ls ->
              ls.pane(Styles.noBarPane) { p ->
                p.top().left().defaults().growX().height(45f).minWidth(160f).left().padLeft(4f).padRight(4f)
                p.add(Core.bundle["dialog.calculator.materials"]).labelAlign(Align.center).color(Color.lightGray)
                p.table(Consts.grayUIAlpha).margin(6f).get().add(Core.bundle["dialog.calculator.expectAmount"])
                  .labelAlign(Align.center).color(
                    Color.lightGray,
                  )
                p.add(Core.bundle["dialog.calculator.configured"]).labelAlign(Align.center).color(Color.lightGray)
                p.row()
                for (stack in currCard!!.recipe.materials.values()) {
                  if ((RecipeType.generator as GeneratorRecipe?)!!.isPower(stack.item)) continue

                  p.table { left ->
                    left.left()
                    left.image(stack.item.icon()).size(36f).scaling(Scaling.fit)
                    left.table { num ->
                      num.left().defaults().fill().left()
                      num.add(stack.item.localizedName())
                      num.row()
                      num.table { amo ->
                        amo.left().defaults().left()
                        val amount =
                          if (stack.isAttribute) if (stack.amount > 1000) UI.formatAmount(stack.amount.toLong())
                          else Mathf.round(stack.amount).toString()
                          else (if (stack.amount*60 > 1000) UI.formatAmount(
                            (stack.amount*60).toLong(),
                          )
                          else Strings.autoFixed(stack.amount*60, 1)) + "/s"
                        amo.add(amount).color(Color.gray)
                        if (currCard!!.multiplier != 1f && !stack.isAttribute && !stack.isBooster) amo.add(
                          Mathf.round(
                            currCard!!.multiplier*100,
                          ).toString() + "%",
                        ).padLeft(5f).color(if (currCard!!.multiplier > 1) Pal.heal else Color.red)
                        amo.add("x" + currCard!!.mul).color(Pal.gray)
                      }
                    }.padLeft(5f)
                  }

                  val amount =
                    if (stack.isAttribute) stack.amount*currCard!!.mul else if (stack.isBooster) stack.amount*currCard!!.mul*currCard!!.effScale*60 else stack.amount*currCard!!.mul*currCard!!.multiplier*60
                  p.table(Consts.grayUIAlpha).get().left().marginLeft(6f).marginRight(6f).add(
                    (if (amount > 1000) UI.formatAmount(amount.toLong())
                    else Strings.autoFixed(
                      amount,
                      1,
                    )) + (if (stack.isAttribute) "" else "/s"),
                  )

                  p.table { stat ->
                    stat.left().defaults().left()
                    val input = currCard!!.linkerIns.find { e: ItemLinker -> e.item === stack.item }
                    if (!stack.isAttribute && input == null) {
                      stat.add(Core.bundle["misc.noInput"])
                    }
                    else {
                      if (stack.isAttribute) {
                        if (currCard!!.environments.getAttribute(stack.item) <= 0) stat.add(Core.bundle["misc.unset"])
                        else stat.add(currCard!!.environments.getAttribute(stack.item).toString() + "")
                      }
                      else if (stack.optionalCons) {
                        stat.table { assign ->
                          assign.left().defaults().left()
                          if (input!!.isNormalized) {
                            val a =
                              if (input.links.size == 1) input.links.orderedKeys().first()!!.expectAmount else -1f

                            assign.add(
                              Core.bundle["misc.provided"] + (if (a <= 0) ""
                              else " [lightgray]" + (if (a*60 > 1000) UI.formatAmount(
                                (a*60).toLong(),
                              )
                              else Strings.autoFixed(a*60, 1)) + "/s"),
                            ).growX()

                            assign.check("", currCard!!.optionalSelected.contains(stack.item)) { b: Boolean ->
                              if (b) currCard!!.optionalSelected.add(stack.item)
                              else currCard!!.optionalSelected.remove(stack.item)
                              currCard!!.rebuildConfig.invoke()
                              rebuild.run()
                            }.margin(4f).fill()
                          }
                          else {
                            assign.add(Core.bundle["misc.assignInvalid"]).growX()
                          }
                        }.growX()
                      }
                      else {
                        if (input!!.isNormalized) {
                          val a = if (input.links.size == 1) input.links.orderedKeys().first()!!.expectAmount else -1f

                          stat.add(
                            Core.bundle["misc.provided"] + (if (a <= 0) ""
                            else " [lightgray]" + (if (a*60 > 1000) UI.formatAmount(
                              (a*60).toLong(),
                            )
                            else Strings.autoFixed(a*60, 1)) + "/s"),
                          )
                        }
                        else {
                          stat.add(Core.bundle["misc.assignInvalid"])
                        }
                      }
                    }
                  }

                  p.row()
                }
              }.maxHeight(240f).scrollX(false).growX().fillY()
            }
            inner.row()
            inner.image(Icon.down).scaling(Scaling.fit).pad(12f)
            inner.row()
            inner.table(Tex.pane) { ls ->
              ls.pane(Styles.noBarPane) { p ->
                p.top().left().defaults().growX().height(45f).minWidth(160f).left().padLeft(4f).padRight(4f)
                p.add(Core.bundle["dialog.calculator.productions"]).labelAlign(Align.center).color(Color.lightGray)
                p.table(Consts.grayUIAlpha).margin(6f).get().add(Core.bundle["dialog.calculator.actualAmount"])
                  .labelAlign(Align.center).color(
                    Color.lightGray,
                  )
                p.add(Core.bundle["dialog.calculator.expectAmount"]).labelAlign(Align.center).color(Color.lightGray)
                p.row()

                balanceValid = false
                balanceAmount = 0
                for (stack in currCard!!.recipe.productions.values()) {
                  if ((RecipeType.generator as GeneratorRecipe?)!!.isPower(stack.item)) continue

                  p.table { left ->
                    left.left()
                    left.image(stack.item.icon()).size(36f).scaling(Scaling.fit)
                    left.table { num ->
                      num.left().defaults().fill().left()
                      num.add(stack.item.localizedName())
                      num.row()
                      num.table { amo ->
                        amo.left().defaults().left()
                        amo.add(
                          (if (stack.amount*60 > 1000) UI.formatAmount((stack.amount*60).toLong())
                          else Strings.autoFixed(
                            stack.amount*60,
                            1,
                          )) + "/s",
                        ).color(
                          Color.gray,
                        )
                        if (currCard!!.efficiency != 1f) amo.add(
                          Mathf.round(currCard!!.efficiency*100).toString() + "%",
                        ).padLeft(5f).color(if (currCard!!.efficiency > 1) Pal.heal else Color.red)
                        amo.add("x" + currCard!!.mul).color(Pal.gray)
                      }
                    }.padLeft(5f)
                  }

                  var expected = 1f
                  val amount = stack.amount*currCard!!.mul*currCard!!.efficiency
                  p.table(Consts.grayUIAlpha) { actual ->
                    actual.defaults().growX().left()
                    actual.add(
                      (if (amount*60 > 1000) UI.formatAmount((amount*60).toLong())
                      else Strings.autoFixed(
                        amount*60,
                        1,
                      )) + "/s",
                    )
                    actual.add("").update { l: Label ->
                      val diff = amount - expected
                      if (balanceValid) {
                        if (Mathf.zero(diff)) {
                          l.setText(Core.bundle["misc.balanced"])
                          l.setColor(Color.lightGray)
                        }
                        else {
                          l.setText(
                            (if (diff > 0) "+" else "") + (if (diff*60 > 1000) UI.formatAmount((diff*60).toLong())
                            else Strings.autoFixed(
                              diff*60,
                              1,
                            )) + "/s",
                          )
                          l.setColor(if (diff > 0) Pal.accent else Color.red)
                        }
                      }
                      else {
                        l.setText("")
                      }
                    }
                  }.left().marginLeft(6f).marginRight(6f)

                  val linker = currCard!!.linkerOuts.find { e: ItemLinker -> e.item === stack.item }
                  if (linker != null) {
                    p.table a@{ tab ->
                      tab.defaults().growX().fillY()
                      if (linker.links.size == 1) {
                        val other = linker.links.orderedKeys().first()
                        if (!other!!.isNormalized) {
                          tab.add(Core.bundle["misc.assignInvalid"]).color(Color.red)
                          return@a
                        }

                        tab.add(
                          Core.bundle.format(
                            "dialog.calculator.assigned",
                            (if (other.expectAmount*60 > 1000) UI.formatAmount(
                              (other.expectAmount*60).toLong(),
                            )
                            else Strings.autoFixed(
                              other.expectAmount*60, 1,
                            )) + "/s",
                          ),
                        )

                        expected = other.expectAmount

                        balanceValid = true
                        balanceAmount = Mathf.ceil(
                          max(
                            (other.expectAmount/(stack.amount*currCard!!.efficiency)),
                            balanceAmount.toFloat(),
                          )
                        )
                      }
                      else if (linker.links.isEmpty) {
                        tab.add(Core.bundle["misc.unset"])
                      }
                      else {
                        var anyUnset = false

                        var amo = 0f
                        for (other in linker.links.keys()) {
                          var rate = other!!.links[linker]?:-1f

                          if (!other.isNormalized) {
                            anyUnset = true
                            break
                          }
                          else if (rate < 0) rate = 1f

                          amo += rate*other.expectAmount
                        }

                        expected = amo
                        if (!anyUnset) {
                          tab.add(
                            Core.bundle.format(
                              "dialog.calculator.assigned",
                              (if (amo*60 > 1000) UI.formatAmount(
                                (amo*60).toLong(),
                              )
                              else Strings.autoFixed(amo*60, 1)) + "/s",
                            ),
                          )
                          balanceValid = true
                          balanceAmount = Mathf.ceil(
                            max(amo/(stack.amount*currCard!!.efficiency), balanceAmount.toFloat())
                          )
                        }
                        else tab.add(Core.bundle["misc.assignInvalid"]).color(Color.red)
                      }
                    }
                  }
                  else p.add("<error>")

                  p.row()
                }
              }.maxHeight(240f).scrollX(false).growX().fillY()
            }.grow()

            inner.row()
            inner.table { scl ->
              scl.left()
              val fold = AtomicBoolean(false)
              val scale = floatArrayOf(currCard!!.effScale)
              val tab = scl.table().growX().get()
              tab.left().defaults().left().padRight(8f)
              val doFold = Runnable {
                tab.clearChildren()
                if (fold.get()) {
                  tab.add(Core.bundle["dialog.calculator.effScale"])
                  tab.field(Strings.autoFixed(scale[0]*100, 1), TextField.TextFieldFilter.digitsOnly) { i: String ->
                    try {
                      scale[0] = if (i.isEmpty()) 0f else i.toFloat()/100
                    } catch (ignored: Throwable) {
                    }
                  }.growX().get().setAlignment(Align.right)
                  tab.add("%").color(Color.gray)

                  fold.set(false)
                }
                else {
                  tab.add(
                    Core.bundle["dialog.calculator.effScale"] + Strings.autoFixed(
                      currCard!!.effScale*100,
                      1,
                    ) + "%",
                  )

                  fold.set(true)
                }
              }
              doFold.run()
              scl.button(Icon.pencilSmall, Styles.clearNonei, 24f) {
                if (fold.get()) {
                  doFold.run()
                }
                else {
                  currCard!!.effScale = scale[0]
                  currCard!!.rebuildConfig()
                  rebuild.run()
                }
              }.update { i: ImageButton -> i.style.imageUp = if (fold.get()) Icon.pencilSmall else Icon.okSmall }
                .fill().margin(5f)
            }
            inner.row()
            inner.add(
              Core.bundle.format(
                "dialog.calculator.expectedMultiple",
                if (balanceValid) balanceAmount.toString() + "x" else Core.bundle["misc.invalid"],
              ),
            )
            inner.row()
            inner.add(
              Core.bundle.format(
                "dialog.calculator.currentMul", currCard!!.mul,
                if (balanceValid) (if (currCard!!.mul == balanceAmount) "[gray]" + Core.bundle["misc.balanced"] else (if (currCard!!.mul > balanceAmount) "[accent]+" else "[red]") + (currCard!!.mul - balanceAmount)) else "",
              ),
            )
          }
          else {
            inner.add("misc.unset")
          }
        }.also { rebuild = it })
      }.grow()
      t.row()
      t.table { buttons ->
        buttons.right().defaults().size(92f, 36f).pad(6f)
        buttons.button(Core.bundle["misc.close"], Styles.cleart) {
          ownerDesigner.currPage!!.view.selects.clear()
          hide()
        }
        buttons.button(Core.bundle["misc.ensure"], Styles.cleart) {
          currCard!!.mul = balanceAmount
          currCard!!.rebuildConfig()
          rebuild.run()
        }.disabled { !balanceValid }
      }.growX()
    }.grow().margin(8f)

    resized {
      cell.maxSize(Core.scene.width/Scl.scl(), Core.scene.height/Scl.scl())
      cell.get().invalidateHierarchy()
    }
  }
}
