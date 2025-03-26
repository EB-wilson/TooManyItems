package tmi.ui.designer

import arc.Core
import arc.Graphics
import arc.func.Prov
import arc.graphics.Color
import arc.scene.Element
import arc.scene.event.Touchable
import arc.scene.ui.Button
import arc.scene.ui.TextField
import arc.scene.ui.layout.Table
import arc.util.Align
import arc.util.Scaling
import arc.util.Strings
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.core.UI
import mindustry.gen.Icon
import mindustry.ui.Styles
import tmi.TooManyItems
import tmi.forEach
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem
import tmi.ui.TmiUI
import tmi.ui.addEventBlocker
import tmi.util.Consts
import tmi.util.Utils

class IOCard(
  ownerDesigner: DesignerView,
  val isInput: Boolean
) : Card(ownerDesigner) {
  private val items = sortedMapOf<RecipeItem<*>, RecipeItemStack<*>>()
  private val inner = Table()

  override val balanceValid: Boolean get() = true

  override fun act(delta: Float) {
    for (linker in linkerIns) {
      val stack = items[linker.item] ?: continue

      linker.expectAmount = stack.amount
    }

    for (linker in linkerOuts) {
      val stack = items[linker.item] ?: continue

      linker.expectAmount = stack.amount
    }

    super.act(delta)
  }

  override fun buildCard() {
    pane.table(Consts.grayUI) { t ->
      t.center()

      t.hovered {
        ownerDesigner.removeEmphasize(this)
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
      t.table { m ->
        m.image(if (isInput) Icon.download else Icon.upload).scaling(Scaling.fit).size(26f).padRight(6f)
        m.add(Core.bundle[if (isInput) "dialog.calculator.input" else "dialog.calculator.output"]).growX()
          .labelAlign(Align.left).pad(12f)
      }
      t.row()
      t.add(inner).center().grow().pad(24f).padTop(24f)

      if (isInput) {
        t.row()
        t.button(Core.bundle["dialog.calculator.addItem"], Icon.addSmall, Styles.cleart, 24f) {
          ownerDesigner.parentDialog.showMenu(inner, Align.bottom, Align.top) { list ->
            list.table(Consts.darkGrayUIAlpha) { items ->
              val l = TooManyItems.itemsManager.list
                .removeAll { e -> !TooManyItems.recipesManager.anyMaterial(e) }
              TmiUI.buildItems(items, l, { i, b -> b.setDisabled { this.items.containsKey(i) } }) { item ->
                ownerDesigner.pushHandle(IOCardItemHandle(ownerDesigner, this, item, false))
                ownerDesigner.parentDialog.hideMenu()
              }
            }.margin(8f)
          }
        }.growX().fillY().margin(8f).marginTop(10f).marginBottom(10f)
      }
    }.grow()

    buildInner()
  }

  private fun buildInner() {
    inner.clearChildren()

    var tab: Table? = null
    if (items.isEmpty()){
      inner.add(Core.bundle["dialog.calculator.noItems"]).pad(24f).padTop(32f).padBottom(32f)
    }
    else {
      items.values.forEachIndexed { i, item ->
        if (i%5 == 0) {
          tab = inner.table().growY().fillX().left().pad(5f).get().top()
        }

        val button = Button(Styles.cleart).also { t ->
          if (isInput) {
            t.button(Icon.cancelSmall, Styles.clearNonei, 18f) {
              ownerDesigner.pushHandle(
                linkerOuts.find { it.item == item.item }?.let {
                  CombinedHandles(ownerDesigner,
                    RemoveLinkerHandle(ownerDesigner, it),
                    IOCardItemHandle(ownerDesigner, this, item.item, true),
                  )
                }?: IOCardItemHandle(ownerDesigner, this, item.item, true)
              )
            }.margin(4f)
          }
          t.image(item.item.icon).scaling(Scaling.fit).size(42f).pad(4f)
          t.add(item.item.localizedName).growX().left().pad(5f)
          t.add("").left().update {
            val (value, unit) = Utils.unitTimed(item.amount)

            it.setText(
              (if (value <= 0) "--"
              else if (value > 1000) UI.formatAmount(value.toLong())
              else Strings.autoFixed(value, 2))
              + unit
            )
          }.color(Color.lightGray).pad(5f)
        }

        if (isInput) {
          setNodeMoveLinkerListener(button, item.item, ownerDesigner)
        }
        else {
          button.clicked{
            ownerDesigner.parentDialog.showMenu(button, Align.bottomLeft, Align.topLeft) { pane ->
              pane.table(Consts.darkGrayUIAlpha){
                it.add(Core.bundle["dialog.calculator.setAmount"])
                it.row()
                it.table{ t ->
                  var (_, unit) = Utils.unitTimed(item.amount)

                  val field = t.field(Strings.autoFixed(item.amount*unit.multi, 2), TextField.TextFieldFilter.floatsOnly){ str ->
                    try {
                      item.amount = str.toFloat()/unit.multi
                      observeUpdate()
                    } catch (ignored: NumberFormatException){}
                  }.get()
                  t.button({ b -> b.add("").pad(4f).update{ l -> l.setText(unit.toString()) } }, Styles.flatt){
                    unit = unit.next()
                    field.text = Strings.autoFixed(item.amount*unit.multi, 2)
                  }.left().padLeft(4f).color(Color.lightGray).fillX().growY()
                }
              }.margin(6f)
            }
          }
        }

        tab!!.table(Consts.darkGrayUIAlpha){ it.add(button).left().growX() }.height(60f).growX().left().margin(6f)
        tab!!.row()
      }
    }

    pack()
  }

  override fun addIn(linker: ItemLinker) {
    super.addIn(linker)
    if (!isInput) {
      addItem(linker.item)
    }
  }

  override fun addOut(linker: ItemLinker) {
    super.addOut(linker)
    if (isInput && !items.containsKey(linker.item)) {
      addItem(linker.item)
    }
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> addItem(item: RecipeItem<T>): RecipeItemStack<T> {
    var stack = items[item]
    if (stack != null) return stack as RecipeItemStack<T>

    stack = RecipeItemStack(item, 0f)
    this.items[item] = stack
    buildInner()

    return stack
  }

  fun removeItem(item: RecipeItem<*>) {
    items.remove(item)
    buildInner()
  }

  override fun removeChild(element: Element, unfocus: Boolean): Boolean {
    val b = super.removeChild(element, unfocus)
    if (b && !isInput && element is ItemLinker) {
      removeItem(element.item)
      return true
    }

    return false
  }
  @Deprecated(
    message = "unnamed to inputs()",
    replaceWith = ReplaceWith("inputs()"),
    level = DeprecationLevel.WARNING
  )
  override fun accepts() = inputs()

  override fun inputTypes() = if (!isInput) items.values.map { it.item } else emptyList()
  override fun outputTypes() = if (isInput) items.values.map { it.item } else emptyList()
  override fun inputs() = if (!isInput) items.values.toList() else emptyList()
  override fun outputs() = if (isInput) items.values.toList() else emptyList()

  override fun added() {}

  override fun calculateBalance() {
    if (!isInput) return

    items.forEach { entry ->
      val linker = linkerOuts.find { it.item == entry.key }
      var amount = 0f

      linker?.links?.forEach lns@{ other, ent ->
        if (!other.isNormalized) return@lns

        amount += (if (other.links.size == 1) 1f else ent.rate)*other.expectAmount
      }

      entry.value.amount = amount
    }

    for (linker in linkerOuts) {
      val stack = items[linker.item] ?: continue

      linker.expectAmount = stack.amount
    }
  }

  override fun checkLinking(linker: ItemLinker): Boolean {
    return !isInput
  }

  override fun copy(): IOCard {
    val res = IOCard(ownerDesigner, isInput)
    res.setBounds(x, y, width, height)

    return res
  }

  override fun write(write: Writes) {
    write.i(CLASS_ID)
    write.bool(isInput)
    write.bool(isFold)

    super.write(write)

    write.i(items.size)
    items.values.forEach {
      write.str(it.item.name)
      write.f(it.amount)
    }
  }

  override fun read(read: Reads, ver: Int) {
    super.read(read, ver)

    val n = read.i()
    for (i in 0 until n) {
      val item = TooManyItems.itemsManager.getByName<Any>(read.str())
      val amount = read.f()
      addItem(item).amount = amount
    }

    observeUpdate()
  }

  companion object {
    const val CLASS_ID = 2117128239
  }
}
