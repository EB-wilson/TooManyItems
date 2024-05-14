package tmi.ui.designer

import arc.Core
import arc.Graphics
import arc.func.Func
import arc.func.Prov
import arc.graphics.*
import arc.scene.event.Touchable
import arc.scene.ui.*
import arc.scene.ui.layout.Table
import arc.util.Align
import arc.util.Scaling
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.gen.Icon
import mindustry.ui.Styles
import mindustry.world.meta.StatUnit
import tmi.TooManyItems
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem
import tmi.set
import tmi.util.Consts

class IOCard(ownerDesigner: SchematicDesignerDialog, item: RecipeItem<*>, val isInput: Boolean) : Card(ownerDesigner) {
  val stack: RecipeItemStack = RecipeItemStack(item, 0f).setPersecFormat()

  private val itr = Iterable {
    object : Iterator<RecipeItemStack> {
      var has: Boolean = false

      override fun hasNext(): Boolean {
        return !has.also { has = it }
      }

      override fun next(): RecipeItemStack {
        return stack
      }
    }
  }

  override fun act(delta: Float) {
    super.act(delta)

    if (isInput && !linkerOuts.isEmpty) linkerOuts.first()!!.expectAmount = stack.amount
    else {
      for (linker in linkerIns) {
        linker.expectAmount = stack.amount
      }
    }
  }

  override fun buildCard() {
    child.table(Consts.grayUI) { t: Table ->
      t.center()
      t.hovered {
        if (ownerDesigner.view!!.newSet === this) ownerDesigner.view!!.newSet = null
      }

      t.center().table(Consts.darkGrayUI) { top: Table ->
        top.touchablility = Prov { if (ownerDesigner.editLock) Touchable.disabled else Touchable.enabled }
        top.add().size(24f).pad(4f)

        top.hovered { Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand) }
        top.exited { Core.graphics.restoreCursor() }
        top.addCaptureListener(moveListener(top))
      }.fillY().growX().get()
      t.row()
      t.table { m: Table ->
        m.image(if (isInput) Icon.download else Icon.upload).scaling(Scaling.fit).size(26f).padRight(6f)
        m.add(Core.bundle[if (isInput) "dialog.calculator.input" else "dialog.calculator.output"]).growX()
          .labelAlign(Align.left).pad(12f)
      }
      t.row()
      t.table { inner: Table ->
        inner.image(stack.item.icon()).scaling(Scaling.fit).size(48f)
        inner.row()
        inner.add(stack.item.localizedName()).pad(8f).color(Color.lightGray)
        inner.row()
        inner.table { ta: Table ->
          val edit = arrayOfNulls<Runnable>(1)
          val build = arrayOfNulls<Runnable>(1)

          ownerDesigner.setMoveLocker(ta)

          build[0] = Runnable {
            ta.clearChildren()
            ta.add(
              Core.bundle.format(
                "misc.amount",
                if (stack.amount > 0) stack.getAmount() else Core.bundle["misc.unset"]
              )
            ).minWidth(120f)
            ta.button(Icon.pencil, Styles.clearNonei, 32f) {
              edit[0]!!
                .run()
            }.margin(4f)
          }
          edit[0] = Runnable {
            ta.clearChildren()
            ta.field((stack.amount*60).toString(), TextField.TextFieldFilter.floatsOnly) { s: String ->
              try {
                stack.amount = s.toFloat()/60
              } catch (ignored: Throwable) {
              }
            }.width(100f)
            ta.add(StatUnit.perSecond.localized())
            ta.button(Icon.ok, Styles.clearNonei, 32f) { build[0]!!.run() }.margin(4f)
          }
          build[0]!!.run()
        }
      }.center().grow().pad(36f).padTop(24f)
    }.grow()
  }

  override fun buildLinker() {
    if (!isInput) return

    addOut(ItemLinker(ownerDesigner, stack.item, false))

    Core.app.post {
      val linker = linkerOuts[0]!!
      linker.pack()
      val offY = child.height/2 + linker.height/1.5f
      val offX = child.width/2

      linker.setPosition(child.x + offX, child.y + child.height/2 + offY, Align.center)
      linker.dir = 1
    }
  }

  override fun accepts(): Iterable<RecipeItemStack> {
    return itr
  }

  override fun outputs(): Iterable<RecipeItemStack> {
    return itr
  }

  override fun copy(): IOCard {
    val res = IOCard(ownerDesigner, stack.item, isInput)
    res.stack.amount = stack.amount

    res.setBounds(x, y, width, height)

    return res
  }

  override fun write(write: Writes) {
    write.i(CLASS_ID)
    write.str(stack.item.name())
    write.bool(isInput)
    write.f(stack.amount)
  }

  companion object {
    private const val CLASS_ID = 1213124234

    init {
      provs[CLASS_ID] = Func<Reads, Card> { r: Reads ->
        val res = IOCard(TooManyItems.schematicDesigner, TooManyItems.itemsManager.getByName<Any>(r.str()), r.bool())
        res.stack.amount = r.f()
        res
      }
    }
  }
}