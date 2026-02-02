package tmi.ui.designer

import arc.Core
import arc.Graphics
import arc.graphics.Color
import arc.input.KeyCode
import arc.scene.event.Touchable
import arc.scene.style.TextureRegionDrawable
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
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.TooManyItems
import tmi.util.forEach
import tmi.recipe.EnvParameter
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem
import tmi.ui.CellType
import tmi.ui.RecipeView
import tmi.ui.TmiUI
import tmi.ui.addEventBlocker
import tmi.util.Consts
import tmi.util.vec1
import kotlin.math.ceil

@Deprecated("Use recipe calculator instead")
open class RecipeCard(ownerView: DesignerView, val recipe: Recipe) : Card(ownerView) {
  var rebuildConfig = {}
  var rebuildOptionals = {}
  var rebuildAttrs = {}
  var rebuildSimAttrs = {}

  val environments: EnvParameter = EnvParameter()
  val optionalSelected: OrderedSet<RecipeItem<*>> = OrderedSet()

  override var balanceValid = false

  var balanceAmount = -1
  var mul = -1
  var effScale = 1f

  lateinit var over: Table

  val recipeView: RecipeView = RecipeView(recipe, { i, t, m ->
    Time.run(0f){ ownerView.parentDialog.hideMenu() }

    //TmiUI.recipesDialog.toggle = Cons { recipe ->
    //  TmiUI.recipesDialog.hide()
    //  val card = ownerView.addRecipe(recipe)
    //  if (Core.input.keyDown(TooManyItems.binds.hotKey)) {
    //    if (t == NodeType.MATERIAL) {
    //      val linker = card.linkerOuts.find { e -> e.item == i.item }!!
    //      var other = linkerIns.find { e -> e.item == i.item }
    //      if (other == null) {
    //        other = ItemLinker(this, i.item, true)
    //        other.pack()
    //        addIn(other)
//
    //        val fo = other
    //        Core.app.post {
    //          fo.adsorption(getWidth()/2, 10f, this)
    //        }
    //      }
//
    //      linker.linkTo(other)
    //    }
    //    else if (t == NodeType.PRODUCTION) {
    //      val linker = linkerOuts.find { e -> e.item == i.item }!!
    //      var other = card.linkerIns.find { e -> e.item == i.item }
//
    //      if (other == null) {
    //        other = ItemLinker(this, i.item, true)
    //        other.pack()
    //        card.addIn(other)
//
    //        val fo = other
    //        Core.app.post {
    //          fo.adsorption(card.getWidth()/2, 10f, card)
    //        }
    //      }
    //      linker.linkTo(other)
    //    }
    //  }
    //}
    TmiUI.recipesDialog.show()
    TmiUI.recipesDialog.setCurrSelecting(i.item, m!!)
  }){ node ->
    if (node.type != CellType.PRODUCTION) return@RecipeView

    //setNodeMoveLinkerListener(node, node.stack.item, ownerView){
    //  it.activity = false
    //  it.touched = false
    //}
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
      val stack = recipe.getMaterial(linker.item) ?: continue

      if (stack.isBooster) linker.expectAmount = (stack.amount*mul*effScale)
        .takeIf { !stack.isOptional || optionalSelected.contains(stack.item) } ?: 0f
      else linker.expectAmount = (stack.amount*mul*multiplier)
        .takeIf { !stack.isOptional || optionalSelected.contains(stack.item) } ?: 0f
    }

    for (linker in linkerOuts) {
      val stack = recipe.getProduction(linker.item) ?: continue

      linker.expectAmount = stack.amount*mul*efficiency
    }
  }

  open fun buildLinker() {
    pack()

    for (item in outputs()) {
      val linker = ItemLinker(this, item.item, false)
      addOut(linker)
    }

    val outStep = width/linkerOuts.size
    val baseOff = outStep/2

    linkerOuts.forEachIndexed { i, linker ->
      linker.pack()
      val offY = linker.height/1.5f
      val offX = baseOff + i*outStep

      linker.setPosition(offX, height + offY, Align.center)
      linker.dir = 1
    }
  }

  override fun buildCard(inner: Table) {
    inner.table { tab ->
      tab.table { inf ->
        inf.image(Icon.boxSmall).size(32f).pad(6f).padRight(0f)
        inf.left().add("").growX()
          .update { l -> l.setText(
            Core.bundle.format("dialog.calculator.recipeMulti", if (balanceValid) mul else "--" )
          ) }
          .left().pad(6f).padLeft(12f).align(Align.left)
        inf.button(Icon.settings, Styles.clearNonei, 32f) { over.visible = true }.margin(4f).get().also {
          it.visible { !ownerDesigner.imageGenerating }
          it.addEventBlocker()
        }
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

      tab.row()
      tab.table { i -> i.add(recipeView).fill() }
        .center().grow().pad(36f).padTop(12f)
      tab.fill { over ->
        this.over = over
        over.visible = false
        over.touchable = Touchable.enabled
        over.table(Consts.darkGrayUIAlpha) { table ->
          table.visible { !ownerDesigner.imageGenerating }

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

              r.table { li ->
                li.add(Core.bundle["calculator.config.efficiencyScl"])
                li.table { inp ->
                  inp.right().field(Strings.autoFixed(effScale*100, 1), TextField.TextFieldFilter.floatsOnly) { i ->
                    try {
                      val setArgsHandle = checkHandle()
                      setArgsHandle.effScale = 0f.takeIf { i.isEmpty() } ?: (i.toFloat()/100)
                      setArgsHandle.handle()
                    } catch (ignored: Throwable) {
                    }
                  }.growX().right().maxWidth(160f).get().setAlignment(Align.right)
                  inp.add("%").color(Color.gray)
                }.growX().padLeft(50f)
              }.growX().fillY()
              r.row()

              r.table { li ->
                li.add(Core.bundle["calculator.config.optionalMats"])
                li.table { inner ->
                  inner.right().button(Icon.settingsSmall, Styles.clearNonei, 24f) {
                    buildOptSetter(inner)
                  }.right().fill().margin(4f).get().addEventBlocker()
                }.growX()
              }.growX().fillY()

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
                        val stack = recipe.getMaterial(item)!!
                        val am = stack.amount*mul*(effScale.takeIf { stack.isBooster }?:multiplier)*60
                        l.setText((UI.formatAmount(am.toLong()).takeIf { am > 1000 }?: Strings.autoFixed(am, 1)) + "/s")
                      }.labelAlign(Align.right)
                      mats.row()
                    }
                  }
                }
                rebuildOptionals()
              }.fillY().minHeight(40f).growX().scrollX(false).left()

              r.row()
              r.table { li ->
                li.add(Core.bundle["calculator.config.attributes"])
                li.table { inner ->
                  inner.right().button(Icon.settingsSmall, Styles.clearNonei, 24f) {
                    buildAttrSetter(inner)
                  }.right().fill().margin(4f).get().addEventBlocker()
                }.growX().fillY()
              }.growX().fillY()

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
              }.minHeight(40f).fillY().growX().scrollX(false).left()
            }.margin(10f).fillY().growX().left()
          }
          rebuildConfig()
        }.grow()

        over.addEventBlocker()
      }
    }.grow()
  }

  override fun buildSimpleCard(inner: Table) {
    inner.table { inf ->
      inf.image(Icon.boxSmall).size(32f).pad(6f).padRight(0f)
      inf.left().add("").growX()
        .update { l -> l.setText(
          Core.bundle.format("dialog.calculator.recipeMulti", if (balanceValid) mul else "--" )
        ) }
        .left().pad(6f).padLeft(12f).align(Align.left)
    }.growX()
    inner.row()
    inner.image(recipe.ownerBlock?.icon?.let { TextureRegionDrawable(it) }?:Icon.none)
      .grow().minSize(100f).maxSize(350f).scaling(Scaling.fit).pad(40f).padBottom(25f).padTop(25f)
      .get().also {
        it.hovered {
          it.color.set(Color.lightGray)
          Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand)
        }
        it.exited {
          it.color.set(Color.white)
          Core.graphics.restoreCursor()
        }
        it.clicked { TmiUI.recipesDialog.showWith {
          setCurrSelecting(recipe.ownerBlock)
        } }
        it.addEventBlocker()
      }
    inner.row()
    inner.add(recipe.ownerBlock?.localizedName?:"???", Styles.outlineLabel).padBottom(8f).color(Pal.accent)
    inner.row()

    inner.table { cont ->
      rebuildSimAttrs = {
        cont.clearChildren()
        if (environments.hasAttrs()) {
          cont.table(Tex.paneTop) { t ->
            var n = 0
            environments.eachAttribute{ i, f ->
              if (n++.mod(3) == 0 && n != 0) t.row()
              t.stack(
                Table{ t ->
                  t.left().image(i.icon).left().scaling(Scaling.fit).pad(4f).size(40f)
                },
                Table{ t ->
                  t.bottom().left().add("" + f.toInt(), Styles.outlineLabel)
                }
              ).fill().pad(4f)
            }
          }.fillY().growX()
        }
      }
      rebuildSimAttrs()
    }.fillY().growX()
  }

  private fun setDefAttribute() {
    recipe.materials.toList().firstOrNull { it.run { isAttribute && !isOptional } }?.apply {
      environments.add(item, amount, true)
    }
  }

  private fun buildAttrSetter(inner: Table) {
    ownerDesigner.parentDialog.showMenu(inner, Align.topRight, Align.topLeft, true) { menu ->
      menu.table(Consts.darkGrayUIAlpha) { i ->
        i.checkHideMenu()
        i.add(Core.bundle["calculator.config.selectAttributes"]).color(Pal.accent)
          .pad(8f).padBottom(4f).growX().left()
        i.row()
        if (!recipe.materials.any { e -> e.isAttribute }) {
          i.add(Core.bundle["calculator.config.noAttributes"]).color(Color.lightGray)
            .pad(8f).growX().left()
        }
        else {
          i.add(Core.bundle["calculator.config.attrTip"]).color(Color.lightGray)
            .pad(8f).padTop(4f).growX().left()
          i.row()
          i.pane { p ->
            for (stack in recipe.materials) {
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
        if (!recipe.materials.any { e -> e.isOptional && !e.isAttribute }) {
          i.add(Core.bundle["calculator.config.noOptionals"]).color(Color.lightGray).pad(8f)
            .growX().left()
        }
        else {
          i.pane { p ->
            for (stack in recipe.materials) {
              if (!stack.isOptional || stack.isAttribute) continue
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

  private fun Table.buildIcon(stack: RecipeItemStack<*>) {
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
    for (stack in recipe.materials) {
      if (stack.isOptional && stack.isBooster && !stack.isAttribute && optionalSelected.contains(stack.item))
        param.add(stack.item, stack.amount, false)
    }
    multiplier = recipe.calculateMultiple(param.setAttributes(environments))*effScale

    param.applyFullRecipe(recipe, fillOptional = false, applyAttribute = false, multiplier = multiplier)

    efficiency = recipe.calculateEfficiency(param, multiplier)
  }

  @Deprecated(
    message = "unnamed to inputs()",
    replaceWith = ReplaceWith("inputs()"),
    level = DeprecationLevel.WARNING
  )
  override fun accepts() = inputs()

  override fun inputTypes() = recipe.materials.map { it.item }
  override fun outputTypes() = recipe.productions.map { it.item }
  override fun inputs() = recipe.materials
    .filter { stack ->
      !stack.isAttribute && (!stack.isOptional || optionalSelected.contains(stack.item))
    }
    .map { stack ->
      stack.copy().also { s ->
        s.amount = if (!balanceValid) 0f
        else if (s.isBooster) (s.amount*mul*effScale)
          .takeIf { !s.isOptional || optionalSelected.contains(s.item) } ?: 0f
        else (s.amount*mul*multiplier)
          .takeIf { !s.isOptional || optionalSelected.contains(s.item) } ?: 0f
      }
    }
  override fun outputs() = recipe.productions
    .map { stack ->
      stack.copy().also { it.amount = if (balanceValid) it.amount*efficiency*mul else 0f }
    }
  override fun added() {
    setDefAttribute()
  }

  override fun calculateBalance() {
    balanceValid = true
    balanceAmount = 0

    recipe.productions.forEach { stack ->
      val linker = linkerOuts.find { it.item == stack.item }

      var total = 0f
      linker?.links?.forEach { other, ent ->
        if (!other.isNormalized) {
          balanceValid = false
          balanceAmount = -1
          return
        }

        total += ((if (other.links.size == 1) 1f else ent.rate)*other.expectAmount)
      }

      balanceAmount = balanceAmount.coerceAtLeast(
        ceil(total/(stack.amount * efficiency)).toInt()
      )
    }
  }

  override fun onObserveUpdated() {
    if (mul != balanceAmount || allUpdate){
      mul = balanceAmount

      super.onObserveUpdated()
    }
    else ownerDesigner.popObserve(this)
  }

  override fun checkLinking(linker: ItemLinker): Boolean {
    return recipe.containsMaterial(linker.item)
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

    write.i(optionalSelected.size)
    optionalSelected.forEach { write.str(it.name) }

    environments.write(write)
  }

  override fun read(read: Reads, ver: Int) {
    super.read(read, ver)

    mul = read.i()
    effScale = read.f()

    if (ver <= 6) return

    optionalSelected.clear()
    environments.clear()

    val n = read.i()
    for (i in 0 until n) {
      optionalSelected.add(TooManyItems.itemsManager.getByName<Any>(read.str()))
    }

    environments.read(read)
  }

  companion object {
    const val CLASS_ID = 2134534563
  }
}
