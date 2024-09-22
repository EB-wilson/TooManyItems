package tmi.ui.designer

import arc.Core
import arc.Graphics
import arc.func.Cons
import arc.func.Prov
import arc.graphics.Color
import arc.input.KeyCode
import arc.math.Mathf
import arc.scene.event.Touchable
import arc.scene.ui.CheckBox
import arc.scene.ui.TextField
import arc.scene.ui.layout.Table
import arc.scene.utils.Elem
import arc.struct.OrderedSet
import arc.util.Align
import arc.util.Scaling
import arc.util.Strings
import arc.util.Time
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
import tmi.ui.NodeType
import tmi.ui.RecipeView
import tmi.ui.TmiUI
import tmi.ui.addEventBlocker
import tmi.ui.designer.IOCard.Companion.CLASS_ID
import tmi.util.Consts
import tmi.util.vec1
import kotlin.math.max

class RecipeCard(ownerView: DesignerView, val recipe: Recipe) : Card(ownerView) {
  var rebuildConfig = {}
  var rebuildOptionals = {}
  var rebuildAttrs = {}

  val environments: EnvParameter = EnvParameter()
  val optionalSelected: OrderedSet<RecipeItem<*>> = OrderedSet()

  override var balanceValid = false

  var balanceAmount = -1
  var mul = -1
  var effScale = 1f

  var over: Table? = null

  val recipeView: RecipeView = RecipeView(recipe, { i, t, m ->
    Time.run(0f){ ownerView.parentDialog.hideMenu() }

    TmiUI.recipesDialog.toggle = Cons { recipe ->
      TmiUI.recipesDialog.hide()
      val card = ownerView.addRecipe(recipe)
      if (Core.input.keyDown(TooManyItems.binds.hotKey)) {
        if (t == NodeType.MATERIAL) {
          val linker = card.linkerOuts.find { e -> e.item == i.item }!!
          var other = linkerIns.find { e -> e.item == i.item }
          if (other == null) {
            other = ItemLinker(this, i.item, true)
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
          val linker = linkerOuts.find { e -> e.item == i.item }!!
          var other = card.linkerIns.find { e -> e.item == i.item }

          if (other == null) {
            other = ItemLinker(this, i.item, true)
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
    TmiUI.recipesDialog.show()
    TmiUI.recipesDialog.setCurrSelecting(i.item, m!!)
  }){ node ->
    if (node.type != NodeType.PRODUCTION) return@RecipeView

    setNodeMoveLinkerListener(node, node.stack.item, ownerView){
      it.activity = false
      it.touched = false
    }
  }

  var efficiency = 0f
    private set
  var multiplier = 0f
    private set

  private val param = EnvParameter()

  private var setArgsHandle: SetRecipeArgsHandle? = null

  init {
    recipeView.validate()
  }

  private fun checkHandle(): SetRecipeArgsHandle{
    if (setArgsHandle == null || setArgsHandle!!.isExpired){
      setArgsHandle = SetRecipeArgsHandle(ownerDesigner, this)
        .also { ownerDesigner.pushHandle(it) }
    }
    setArgsHandle!!.updateTimer()
    return setArgsHandle!!
  }

  override fun act(delta: Float) {
    super.act(delta)

    for (linker in linkerIns) {
      val stack = recipe.materials[linker.item] ?: continue

      if (stack.isBooster) linker.expectAmount = (stack.amount*mul*effScale)
        .takeIf { stack.optionalCons && optionalSelected.contains(stack.item) }?: 0f
      else linker.expectAmount = stack.amount*mul*multiplier
    }

    for (linker in linkerOuts) {
      val stack = recipe.productions[linker.item] ?: continue

      linker.expectAmount = stack.amount*mul*efficiency
    }
  }

  override fun buildCard() {
    setDefAttribute()

    pane.table(Consts.grayUI) { t ->
      t.center()
      t.hovered {
        if (ownerDesigner.newSet == this) ownerDesigner.newSet = null
      }

      t.center().table(Consts.darkGrayUI) { top ->
        top.touchablility = Prov { if (ownerDesigner.editLock) Touchable.disabled else Touchable.enabled }
        top.add().size(24f).pad(4f)

        top.hovered { Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand) }
        top.exited { Core.graphics.restoreCursor() }
        top.addCaptureListener(moveListener(top))
        top.addEventBlocker()
      }.fillY().growX().get()
      t.row()
      t.table { inner ->
        ownerDesigner.setMoveLocker(inner)
        inner.table { inf ->
          inf.left().add("").growX()
            .update { l -> l.setText(
              Core.bundle.format("dialog.calculator.recipeMulti", if (balanceValid) mul else "--" )
            ) }
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
          }.left().pad(6f).padLeft(12f).align(Align.left)
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

                r.add(Core.bundle["calculator.config.efficiencyScl"])
                r.table { inp ->
                  inp.field(Strings.autoFixed(effScale*100, 1), TextField.TextFieldFilter.floatsOnly) { i ->
                    try {
                      val setArgsHandle = checkHandle()
                      setArgsHandle.effScale = 0f.takeIf { i.isEmpty() }?: (i.toFloat()/100)
                      setArgsHandle.handle()
                    } catch (ignored: Throwable) { }
                  }.growX().get().setAlignment(Align.right)
                  inp.add("%").color(Color.gray)
                }.growX().padLeft(20f)
                r.row()

                r.add(Core.bundle["calculator.config.optionalMats"])
                r.table { inner ->
                  inner.right().button(Icon.settingsSmall, Styles.clearNonei, 24f) {
                    buildOptSetter(inner)
                  }.right().fill().margin(4f).get().addEventBlocker()
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
                          i.image(item!!.icon).size(32f).scaling(Scaling.fit)
                          i.add(item.localizedName).padLeft(4f)
                        }.growX().margin(6f)
                        mats.add("").growX().padLeft(4f).update { l ->
                          val stack = recipe.materials[item]!!
                          val am = stack.amount*mul*(effScale.takeIf { stack.isBooster }?:multiplier)*60
                          l.setText((UI.formatAmount(am.toLong()).takeIf { am > 1000 }?: Strings.autoFixed(am, 1)) + "/s")
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
                    buildAttrSetter(inner)
                  }.right().fill().margin(4f).get().addEventBlocker()
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
                      environments.eachAttribute { item, f ->
                        attr.table { i ->
                          i.left().defaults().left()
                          i.image(item!!.icon).size(32f).scaling(Scaling.fit)
                          i.add(item.localizedName).padLeft(4f)
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

  private fun setDefAttribute() {
    recipe.materials.toList().firstOrNull { it.second.run { isAttribute && !optionalCons } }?.apply {
      environments.add(this.first, this.second.amount, true)
    }
  }

  private fun buildAttrSetter(inner: Table) {
    ownerDesigner.parentDialog.showMenu(inner, Align.topRight, Align.topLeft, true) { menu ->
      menu.table(Consts.darkGrayUIAlpha) { i ->
        i.checkHideMenu()
        i.add(Core.bundle["calculator.config.selectAttributes"]).color(Pal.accent)
          .pad(8f).padBottom(4f).growX().left()
        i.row()
        if (!recipe.materials.any { e -> e.value.isAttribute }) {
          i.add(Core.bundle["calculator.config.noAttributes"]).color(Color.lightGray)
            .pad(8f).growX().left()
        }
        else {
          i.add(Core.bundle["calculator.config.attrTip"]).color(Color.lightGray)
            .pad(8f).padTop(4f).growX().left()
          i.row()
          i.pane { p ->
            for (stack in recipe.materials.values) {
              if (!stack.isAttribute) continue
              p.table { item ->
                item.buildIcon(stack)

                var check: CheckBox? = null
                val field = item.field(
                  environments.getAttribute(stack.item).toInt().toString() + "",
                  TextField.TextFieldFilter.digitsOnly
                ) {
                  val setArgsHandle = checkHandle()
                  setArgsHandle.envArgs.resetAttr(stack.item)

                  val amount = Strings.parseInt(it, 0)
                  if (amount > 0) setArgsHandle.envArgs.add(stack.item, amount.toFloat(), true)

                  setArgsHandle.handle()

                  check!!.isChecked = environments.getAttribute(stack.item) >= stack.amount
                }.get()

                field.programmaticChangeEvents = true
                check = item.check("", environments.getAttribute(stack.item) >= stack.amount) { b ->
                  if (b) {
                    field.text = stack.amount.toInt().toString() + ""
                  }
                  else field.text = "0"
                }.get()
              }.margin(6f).growX()

              p.row()
            }
          }.grow()
        }
      }.grow().maxHeight(400f).minWidth(260f)
    }
  }

  private fun buildOptSetter(inner: Table) {
    ownerDesigner.parentDialog.showMenu(inner, Align.topRight, Align.topLeft, true) { menu ->
      menu.table(Consts.darkGrayUIAlpha) { i ->
        i.checkHideMenu()
        i.add(Core.bundle["calculator.config.selectOptionals"]).color(Pal.accent).pad(8f).growX()
          .left()
        i.row()
        if (!recipe.materials.any { e -> e.value.optionalCons && !e.value.isAttribute }) {
          i.add(Core.bundle["calculator.config.noOptionals"]).color(Color.lightGray).pad(8f)
            .growX().left()
        }
        else {
          i.pane { p ->
            for (stack in recipe.materials.values) {
              if (!stack.optionalCons || stack.isAttribute) continue
              val item = Elem.newCheck("") { b ->
                val setArgsHandle = checkHandle()

                if (b) setArgsHandle.optionals.add(stack.item)
                else setArgsHandle.optionals.remove(stack.item)

                setArgsHandle.handle()
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
        vec1.set(Core.input.mouse())
        stageToLocalCoordinates(vec1)

        if (vec1.x > width || vec1.y > height || vec1.x < 0 || vec1.y < 0) {
          ownerDesigner.parentDialog.hideMenu()
        }
      }
    }
  }

  private fun Table.buildIcon(stack: RecipeItemStack) {
    image(stack.item.icon).size(36f).scaling(Scaling.fit)
    add(stack.item.localizedName).padLeft(5f).growX().left()
    table { am ->
      am.left().bottom()
      am.add(stack.getAmount(), Styles.outlineLabel)
      am.pack()
    }.padLeft(5f).fill().left()
  }

  fun calculateEfficiency() {
    param.clear()
    for (entry in recipe.materials) {
      if (entry.value.optionalCons && entry.value.isBooster && !entry.value.isAttribute && optionalSelected.contains(
          entry.key
        )
      ) param.add(entry.key, entry.value.amount, false)
    }
    multiplier = recipe.calculateMultiple(param.setAttributes(environments))*effScale

    param.applyFullRecipe(recipe, fillOptional = false, applyAttribute = false, multiplier = multiplier)

    efficiency = recipe.calculateEfficiency(param, multiplier)
  }

  override fun accepts() = recipe.materials.values.toList()

  override fun outputs() = recipe.productions.values.toList()

  override fun calculateBalance() {
    balanceValid = false
    balanceAmount = -1
    for (stack in recipe.productions.values) {
      if ((RecipeType.generator as GeneratorRecipe?)!!.isPower(stack.item)) continue

      val linker = linkerOuts.find { it.item === stack.item }
      if (linker != null) {
        if (linker.links.size == 1) {
          val other = linker.links.orderedKeys().first()
          if (!other!!.isNormalized || !(other.parent as Card).balanceValid) {
            balanceValid = false
            balanceAmount = -1
            break
          }

          balanceValid = true
          balanceAmount = Mathf.ceil(
            max(other.expectAmount/(stack.amount*efficiency), balanceAmount.toFloat())
          )
        }
        else if (!linker.links.isEmpty) {
          var anyUnset = false

          var amo = 0f
          for (other in linker.links.keys()) {
            var rate = other!!.links[linker]?.rate?:-1f

            if (!other.isNormalized) {
              anyUnset = true
              break
            }
            else if (rate < 0) rate = 1f

            amo += rate*other.expectAmount
          }

          if (!anyUnset) {
            balanceValid = true
            balanceAmount = Mathf.ceil(
              max(amo/(stack.amount*efficiency), balanceAmount.toFloat())
            )
          }
        }
      }
    }
  }

  override fun onObserveUpdated() {
    if (mul != balanceAmount || allUpdate){
      mul = balanceAmount

      linkerIns.forEach{l ->
        l.links.forEach {
          (it.key.parent as? Card)?.observeUpdate()
        }
      }
    }
  }

  override fun checkLinking(linker: ItemLinker): Boolean {
    return recipe.materials.containsKey(linker.item)
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
    write.bool(isFold)

    super.write(write)

    write.i(mul)
    write.f(effScale)
  }

  override fun read(read: Reads, ver: Int) {
    super.read(read, ver)

    mul = read.i()
    effScale = read.f()
  }

  companion object {
    const val CLASS_ID = 2134534563
  }
}
