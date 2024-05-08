package tmi.ui.designer

import arc.Core
import arc.Graphics
import arc.func.Cons
import arc.func.Func
import arc.func.Prov
import arc.graphics.*
import arc.input.KeyCode
import arc.scene.event.Touchable
import arc.scene.ui.*
import arc.scene.ui.layout.Table
import arc.scene.utils.Elem
import arc.struct.OrderedSet
import arc.util.*
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.core.UI
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.TooManyItems
import tmi.recipe.EnvParameter
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.RecipeType
import tmi.recipe.types.GeneratorRecipe
import tmi.recipe.types.RecipeItem
import tmi.set
import tmi.ui.NodeType
import tmi.ui.RecipeView
import tmi.util.Consts

class RecipeCard(ownerDesigner: SchematicDesignerDialog, val recipe: Recipe) : Card(ownerDesigner) {
  var rebuildConfig = {}
  var rebuildOptionals = {}
  var rebuildAttrs = {}

  var efficiency = 0f
  var multiplier = 0f

  var over: Table? = null

  val recipeView: RecipeView = RecipeView(recipe) { i, t, m ->
    TooManyItems.recipesDialog.toggle = Cons { r ->
      TooManyItems.recipesDialog.hide()
      val card = ownerDesigner.addRecipe(r)
      if (Core.input.keyDown(TooManyItems.binds.hotKey)) {
        if (t == NodeType.MATERIAL) {
          val linker = card.linkerOuts.find { e -> e.item === i.item() }!!
          var other = linkerIns.find { e -> e.item === i.item() }
          if (other == null) {
            other = ItemLinker(ownerDesigner, i.item(), true)
            other.pack()
            addIn(other)

            val fo = other
            Core.app.post {
              fo.adsorption(getWidth()/2, 10f, this)
            }
          }

          linker.linkTo(other)
        }
        else if (t == NodeType.PRODUCTION) {
          val linker = linkerOuts.find { e -> e.item === i.item() }!!
          var other = card.linkerIns.find { e -> e.item === i.item() }

          if (other == null) {
            other = ItemLinker(ownerDesigner, i.item(), true)
            other.pack()
            card.addIn(other)

            val fo = other
            Core.app.post {
              fo.adsorption(card.getWidth()/2, 10f, card)
            }
          }
          linker.linkTo(other)
        }
      }
    }
    TooManyItems.recipesDialog.show()
    TooManyItems.recipesDialog.setCurrSelecting(i.item(), m!!)
  }

  val environments: EnvParameter = EnvParameter()
  val optionalSelected: OrderedSet<RecipeItem<*>?> = OrderedSet()

  private val param = EnvParameter()

  init {
    recipeView.validate()
  }

  override fun act(delta: Float) {
    super.act(delta)

    for (linker in linkerIns) {
      val stack = recipe.materials[linker.item] ?: continue

      if (stack.isBooster) linker.expectAmount = stack.amount*mul*effScale
      else linker.expectAmount = stack.amount*mul*multiplier
    }

    for (linker in linkerOuts) {
      val stack = recipe.productions[linker.item] ?: continue

      linker.expectAmount = stack.amount*mul*efficiency
    }
  }

  override fun buildCard() {
    child.table(Consts.grayUI) { t ->
      t.center()
      t.hovered {
        if (ownerDesigner.view!!.newSet === this) ownerDesigner.view!!.newSet = null
      }

      t.center().table(Consts.darkGrayUI) { top ->
        top.touchablility = Prov { if (ownerDesigner.editLock) Touchable.disabled else Touchable.enabled }
        top.add().size(24f).pad(4f)

        top.hovered { Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand) }
        top.exited { Core.graphics.restoreCursor() }
        top.addCaptureListener(moveListener(top))
      }.fillY().growX().get()
      t.row()
      t.table { inner ->
        ownerDesigner.setMoveLocker(inner)
        inner.table { inf ->
          inf.left().add("").growX()
            .update { l -> l.setText(Core.bundle.format("dialog.calculator.recipeMulti", mul)) }
            .left().pad(6f).padLeft(12f).align(Align.left)
          inf.add(Core.bundle.format("dialog.calculator.config")).padLeft(30f)
          inf.button(Icon.pencil, Styles.clearNonei, 32f) { over!!.visible = true }.margin(4f)
          inf.row()
          inf.left().add("").colspan(3).growX().update { l ->
            l.setText(
              Core.bundle.format(
                "dialog.calculator.recipeEff",
                (if (efficiency == 1f) "" else if (efficiency > 1) "[#98ffa9]" else "[red]") + Strings.autoFixed(
                  efficiency*100,
                  1
                )
              )
            )
          }
            .left().pad(6f).padLeft(12f).align(Align.left)
        }.growX()

        inner.row()
        inner.table { i -> i.add(recipeView).fill() }
          .center().grow().pad(36f).padTop(12f)
        inner.fill { over ->
          this.over = over
          over.visible = false
          over.table(Consts.darkGrayUIAlpha) { table ->
            rebuildConfig = {
              calculateEfficiency()
              table.clearChildren()

              table.top()
              table.table(Consts.grayUI) { b ->
                b.table(Consts.darkGrayUIAlpha) { pane ->
                  pane.add(Core.bundle["dialog.calculator.config"]).growX().padLeft(10f)
                  pane.button(Icon.cancel, Styles.clearNonei, 32f) { over.visible = false }.margin(4f)
                }.growX()
              }.growX()
              table.row()
              table.table { r ->
                r.left().defaults().left().padBottom(4f)
                r.add(Core.bundle["calculator.config.multiple"])
                r.table { inp ->
                  inp.field(mul.toString(), TextField.TextFieldFilter.digitsOnly) { i ->
                    try {
                      mul = if (i.isEmpty()) 0 else i.toInt()
                    } catch (ignored: Throwable) {
                    }
                  }.growX().get().setAlignment(Align.right)
                  inp.add("x").color(Color.gray)
                }.growX().padLeft(20f)
                r.row()

                r.add(Core.bundle["calculator.config.efficiencyScl"])
                r.table { inp ->
                  inp.field(Strings.autoFixed(effScale*100, 1), TextField.TextFieldFilter.floatsOnly) { i ->
                    try {
                      effScale = if (i.isEmpty()) 0f else i.toFloat()/100
                      calculateEfficiency()
                    } catch (ignored: Throwable) { }
                  }.growX().get().setAlignment(Align.right)
                  inp.add("%").color(Color.gray)
                }.growX().padLeft(20f)
                r.row()

                r.add(Core.bundle["calculator.config.optionalMats"])
                r.table { inner ->
                  inner.right().button(Icon.settingsSmall, Styles.clearNonei, 24f) { buildOptionals(inner) }
                    .right().fill().margin(4f)
                }.growX()
                r.row()
                r.pane { mats ->
                  rebuildOptionals = {
                    mats.clearChildren()
                    mats.left().top().defaults().left()
                    if (optionalSelected.isEmpty) {
                      mats.add(Core.bundle["misc.empty"], Styles.outlineLabel).pad(6f).color(Color.gray)
                    }
                    else {
                      for (item in optionalSelected) {
                        mats.table { i ->
                          i.left().defaults().left()
                          i.image(item!!.icon()).size(32f).scaling(Scaling.fit)
                          i.add(item.localizedName()).padLeft(4f)
                        }.growX().margin(6f)
                        mats.add("").growX().padLeft(4f).update { l ->
                          val stack = recipe.materials[item]!!
                          val am = stack.amount*mul*(if (stack.isBooster) effScale else multiplier)*60
                          l.setText((if (am > 1000) UI.formatAmount(am.toLong()) else Strings.autoFixed(am, 1)) + "/s")
                        }.labelAlign(Align.right)
                        mats.row()
                      }
                    }
                  }
                  rebuildOptionals()
                }.colspan(2).fillY().minHeight(40f).growX().scrollX(false).left()

                r.row()
                r.add(Core.bundle["calculator.config.attributes"])
                r.table { inner ->
                  inner.right().button(Icon.settingsSmall, Styles.clearNonei, 24f) {
                    buildAttributes(inner)
                  }.right().fill().margin(4f)
                }.growX()
                r.row()
                r.pane { attr ->
                  rebuildAttrs = {
                    attr.clearChildren()
                    attr.left().top().defaults().left()
                    if (!environments.hasAttrs()) {
                      attr.add(Core.bundle["misc.empty"], Styles.outlineLabel).pad(6f).color(Color.gray)
                    }
                    else {
                      environments.eachAttribute { item: RecipeItem<*>?, f: Float? ->
                        attr.table { i ->
                          i.left().defaults().left()
                          i.image(item!!.icon()).size(32f).scaling(Scaling.fit)
                          i.add(item.localizedName()).padLeft(4f)
                          i.add("x" + f!!.toInt(), Styles.outlineLabel).pad(6f).color(Color.lightGray)
                        }.fill().margin(6f)
                        attr.row()
                      }
                    }
                  }
                  rebuildAttrs()
                }.colspan(2).minHeight(40f).fillY().growX().scrollX(false).left()
              }.margin(10f).fillY().growX().left()
            }
            rebuildConfig()
          }.grow()
        }
      }.grow()
    }.grow()
  }

  private fun buildAttributes(inner: Table?) {
     ownerDesigner.showMenu(inner, Align.topRight, Align.topLeft, true) { menu ->
       menu.table(Consts.darkGrayUIAlpha) { i ->
         i.checkHideMenu()
         i.add(Core.bundle["calculator.config.selectAttributes"]).color(Pal.accent).pad(8f)
           .padBottom(4f).growX().left()
         i.row()
         if (!recipe.materials.values().toSeq().contains { e -> e.isAttribute }) {
           i.add(Core.bundle["calculator.config.noAttributes"]).color(Color.lightGray).pad(8f)
             .growX()
             .left()
         }
         else {
           i.add(Core.bundle["calculator.config.attrTip"]).color(Color.lightGray).pad(8f)
             .padTop(4f)
             .growX().left()
           i.row()
           i.pane { p ->
             for (stack in recipe.materials.values()) {
               if (!stack.isAttribute) continue
               p.table { item ->
                 item.buildIcon(stack)

                 val field = item.field(
                   environments.getAttribute(stack.item).toInt().toString() + "",
                   TextField.TextFieldFilter.digitsOnly
                 ) { f: String? ->
                   environments.resetAttr(stack.item)
                   val amount = Strings.parseInt(f, 0)
                   if (amount > 0) environments.add(stack.item, amount.toFloat(), true)

                   calculateEfficiency()
                   rebuildAttrs()
                 }.get()
                 field.programmaticChangeEvents = true
                 item.check("") { b ->
                   if (b) field.text = stack.amount.toInt().toString() + ""
                   else field.text = "0"
                 }.update { c ->
                   c.isChecked = environments.getAttribute(stack.item) >= stack.amount
                 }
               }.margin(6f).growX()

               p.row()
             }
           }.grow()
         }
       }.grow().maxHeight(400f).minWidth(260f)
     }
  }

  private fun buildOptionals(inner: Table?) {
    ownerDesigner.showMenu(inner, Align.topRight, Align.topLeft, true) { menu ->
      menu.table(Consts.darkGrayUIAlpha) { i ->
        i.checkHideMenu()
        i.add(Core.bundle["calculator.config.selectOptionals"]).color(Pal.accent).pad(8f).growX()
          .left()
        i.row()
        if (!recipe.materials.values().toSeq()
            .contains { e -> e.optionalCons && !e.isAttribute }
        ) {
          i.add(Core.bundle["calculator.config.noOptionals"]).color(Color.lightGray).pad(8f)
            .growX().left()
        }
        else {
          i.pane { p ->
            for (stack in recipe.materials.values()) {
              if (!stack.optionalCons || stack.isAttribute) continue
              val item = Elem.newCheck("") { b ->
                if (b) optionalSelected.add(stack.item)
                else optionalSelected.remove(stack.item)
                calculateEfficiency()
                rebuildOptionals()
              }
              item.isChecked = optionalSelected.contains(stack.item)
              item.buildIcon(stack)

              p.add(item).margin(6f).growX()
              p.row()
            }
          }.grow()
        }
      }.grow().maxHeight(400f).minWidth(260f)
    }
  }

  private fun Table.checkHideMenu() {
    update {
      if (Core.input.keyDown(KeyCode.mouseLeft) || Core.input.keyDown(KeyCode.mouseRight)) {
        SchematicDesignerDialog.tmp.set(Core.input.mouse())
        stageToLocalCoordinates(SchematicDesignerDialog.tmp)

        if (SchematicDesignerDialog.tmp.x > width || SchematicDesignerDialog.tmp.y > height || SchematicDesignerDialog.tmp.x < 0 || SchematicDesignerDialog.tmp.y < 0) {
          ownerDesigner.hideMenu()
        }
      }
    }
  }

  private fun Table.buildIcon(stack: RecipeItemStack) {
    image(stack.item.icon()).size(36f).scaling(Scaling.fit)
    add(stack.item.localizedName()).padLeft(5f).growX().left()
    table { am ->
      am.left().bottom()
      am.add(stack.getAmount(), Styles.outlineLabel)
      am.pack()
    }.padLeft(5f).fill().left()
  }

  fun calculateEfficiency() {
    param.clear()
    for (entry in recipe.materials) {
      if (entry!!.value.optionalCons && entry.value.isBooster && !entry.value.isAttribute && optionalSelected.contains(
          entry.key
        )
      ) param.add(entry.key, entry.value.amount, false)
    }
    multiplier = recipe.calculateMultiple(param.setAttributes(environments))*effScale

    param.applyFullRecipe(recipe, fillOptional = false, applyAttribute = false, multiplier = multiplier)

    efficiency = recipe.calculateEfficiency(param, multiplier)
  }

  override fun buildLinker() {
    for (item in outputs()) {
      if ((RecipeType.generator as GeneratorRecipe?)!!.isPower(item.item)) continue

      val linker = ItemLinker(ownerDesigner, item.item, false)
      addOut(linker)
    }

    Core.app.post {
      val outStep = child.width/linkerOuts.size
      val baseOff = outStep/2
      for (i in 0 until linkerOuts.size) {
        val linker = linkerOuts[i]!!

        linker.pack()
        val offY = child.height/2 + linker.height/1.5f
        val offX = baseOff + i*outStep

        linker.setPosition(child.x + offX, child.y + child.height/2 + offY, Align.center)
        linker.dir = 1
      }
    }
  }

  override fun accepts(): Iterable<RecipeItemStack> {
    return recipe.materials.values()
  }

  override fun outputs(): Iterable<RecipeItemStack> {
    return recipe.productions.values()
  }

  override fun copy(): RecipeCard {
    val res = RecipeCard(ownerDesigner, recipe)
    res.mul = mul

    res.setBounds(x, y, width, height)

    return res
  }

  override fun write(write: Writes) {
    write.i(CLASS_ID)
    write.i(recipe.hashCode())
  }

  companion object {
    private const val CLASS_ID = 2134534563

    init {
      provs[CLASS_ID] = Func<Reads, Card> { r ->
        val id = r.i()
        RecipeCard(TooManyItems.schematicDesigner, TooManyItems.recipesManager.getByID(id))
      }
    }
  }
}
