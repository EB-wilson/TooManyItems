package tmi.ui.designer

import arc.Core
import arc.func.Cons
import arc.func.Prov
import arc.graphics.Color
import arc.graphics.Pixmaps
import arc.graphics.Texture.TextureFilter
import arc.graphics.g2d.Fill
import arc.graphics.g2d.TextureRegion
import arc.scene.Element
import arc.scene.ui.*
import arc.scene.ui.layout.Cell
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.util.Tmp
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.invoke
import tmi.ui.addEventBlocker
import tmi.util.Consts

class FoldIconCfgDialog(private val configuringCard: Card) : Dialog("", Consts.transparentBack) {
  private val current = Color()
  private var h = 1f
  private var s = 1f
  private var v = 1f
  private var a = 1f
  private var callBack: Cons<Color>? = null
  private val values = FloatArray(3)

  private lateinit var hexField: TextField
  private lateinit var hSlider: Slider
  private lateinit var sSlider: Slider
  private lateinit var vSlider: Slider
  private lateinit var aSlider: Slider

  private lateinit var iconsTable: Table

  private var currConf = "foldColor"

  init {
    titleTable.clear()

    val cell = buildInner()

    resizedShown {
      cell.maxSize(Core.scene.width/Scl.scl(), Core.scene.height/Scl.scl())
      cell.get().invalidateHierarchy()
      cell.get().validate()

      iconsTable.clearChildren()
      val n = (iconsTable.width/Scl.scl(64f + 8f*2)).toInt()
      Consts.foldCardIcons.forEachIndexed { i, icon ->
        if (i != 0 && i%n == 0) iconsTable.row()
        iconsTable.button(icon, Styles.clearNoneTogglei, 46f) {
          configuringCard.foldIcon = if (icon == configuringCard.foldIcon) null else icon
        }.size(64f).pad(8f).update { b -> b.isChecked = configuringCard.foldIcon == icon }
      }
    }

    shown { setCurrConf(configuringCard.foldColor){ c -> configuringCard.foldColor.set(c) } }
  }

  private fun buildInner(): Cell<Table> {
    return cont.table(Consts.darkGrayUIAlpha) { t ->
      t.table(Consts.darkGrayUI) { top ->
        top.left().add(Core.bundle["dialog.calculator.foldIconConfig"]).pad(8f)
      }.grow().padBottom(12f)
      t.row()
      t.table(Consts.darkGrayUI){ inner ->
        inner.left().defaults().growX().fillY().pad(5f)

        inner.pane { iconsPane -> iconsTable = iconsPane }.fillX().growY().maxHeight(320f)

        inner.row()

        inner.table(Consts.grayUI) { colorsPane ->
          colorsPane.table { left ->
            left.button(
              { t -> buildColorTab(t, Core.bundle["dialog.calculator.foldColor"]){ configuringCard.foldColor } },
              Styles.clearNoneTogglei
            ) {
              if (currConf != "foldColor") {
                currConf = "foldColor"
                setCurrConf(configuringCard.foldColor){ c -> configuringCard.foldColor.set(c) }
              }
            }.fillY().growX().pad(6f).margin(6f).update { it.isChecked = currConf == "foldColor" }
            left.row()
            left.button(
              { t -> buildColorTab(t, Core.bundle["dialog.calculator.foldIconColor"]){ configuringCard.iconColor } },
              Styles.clearNoneTogglei
            ) {
              if (currConf != "foldIconColor") {
                currConf = "foldIconColor"
                setCurrConf(configuringCard.iconColor){ c -> configuringCard.iconColor.set(c) }
              }
            }.fillY().growX().pad(6f).margin(6f).update { it.isChecked = currConf == "foldIconColor" }
            left.row()
            left.button(
              { t -> buildColorTab(t, Core.bundle["dialog.calculator.foldBackColor"]){ configuringCard.backColor } },
              Styles.clearNoneTogglei
            ) {
              if (currConf != "foldBackColor") {
                currConf = "foldBackColor"
                setCurrConf(configuringCard.backColor){ c -> configuringCard.backColor.set(c) }
              }
            }.fillY().growX().pad(6f).margin(6f).update { it.isChecked = currConf == "foldBackColor" }
          }.fillX().growY().top()

          colorsPane.table { right ->
            var over: Image
            right.stack(
              Table{ picker -> buildColorPicker(picker) },
              Image().apply {
                color.set(Pal.darkerGray).a(0.6f)
                addEventBlocker()
                over = this
              }
            ).grow()
            right.update { over.visible = callBack == null }
          }.grow()
        }.fillX().growY().maxHeight(320f).left()
      }.grow().margin(12f)
      t.row()
      t.table { buttons ->
        buttons.right().defaults().size(92f, 36f).pad(6f)
        buttons.button(Core.bundle["misc.close"], Styles.cleart) { this.hide() }
      }.growX()
    }.grow().margin(8f)
  }

  private fun buildColorTab(table: Table, text: String, colorProv: Prov<Color>) {
    table.add(text).pad(6f)
    table.add().growX()
    val color = colorProv()
    table.table { c ->
      c.image().size(40f, 35f).update { it.color.set(color) }
      c.row()
      c.image().size(40f, 5f).update {
        it.color.set(if (color.value() > 0.5f) Color.black else Color.white)
      }
    }.fill().pad(6f)
  }

  private fun setCurrConf(color: Color, callBack: Cons<Color>?){
    this.callBack = callBack
    current.set(color)
    current.toHsv(values)

    h = values[0]
    s = values[1]
    v = values[2]
    a = current.a

    hSlider.value = values[0]
    sSlider.value = values[1]
    vSlider.value = values[2]
    aSlider.value = current.a

    hexField.text = current.toString()
  }

  private fun buildColorPicker(table: Table) {
    val hueTex = Pixmaps.hueTexture(128, 1).apply { setFilter(TextureFilter.linear) }

    val updateColor = fun(updateField: Boolean){
      if (callBack == null) return

      current.fromHsv(h, s, v)
      current.a = a
      callBack!!.get(current)

      if (!updateField) return

      hexField.text = current.toString()
    }

    table.table(
      Tex.pane
    ) { i ->
      i.stack(Image(Tex.alphaBg), object : Image() {
        init {
          setColor(current)
          update { setColor(current) }
        }
      }).size(200f)
    }.colspan(2).padBottom(5f)

    table.defaults().padBottom(6f).width(370f).height(44f)

    table.table{ setter ->
      setter.stack(Image(TextureRegion(hueTex)), object : Slider(0f, 360f, 0.3f, false) {
        init {
          value = h
          moved { value ->
            h = value
            updateColor(true)
          }
        }
      }.also { hSlider = it }).growX().row()

      setter.stack(object : Element() {
        override fun draw() {
          val first: Float = Tmp.c1.fromHsv(h, 0f, v).saturation(0f).a(parentAlpha).toFloatBits()
          val second: Float = Tmp.c1.fromHsv(h, 1f, v).saturation(1f).a(parentAlpha).toFloatBits()

          Fill.quad(
            x, y, first,
            x + width, y, second,
            x + width, y + height, second,
            x, y + height, first
          )
        }
      }, object : Slider(0f, 1f, 0.001f, false) {
        init {
          value = s
          moved { value ->
            s = value
            updateColor(true)
          }
        }
      }.also { sSlider = it }).growX().row()

      setter.stack(object : Element() {
        override fun draw() {
          val first: Float = Tmp.c1.set(current).value(0f).a(parentAlpha).toFloatBits()
          val second = Tmp.c1.fromHsv(h, s, 1f).a(parentAlpha).toFloatBits()

          Fill.quad(
            x, y, first,
            x + width, y, second,
            x + width, y + height, second,
            x, y + height, first
          )
        }
      }, object : Slider(0f, 1f, 0.001f, false) {
        init {
          value = v

          moved { value ->
            v = value
            updateColor(true)
          }
        }
      }.also { vSlider = it }).growX().row()

      setter.stack(Image(Tex.alphaBgLine), object : Element() {
        override fun draw() {
          val first: Float = Tmp.c1.set(current).a(0f).toFloatBits()
          val second: Float = Tmp.c1.set(current).a(parentAlpha).toFloatBits()

          Fill.quad(
            x, y, first,
            x + width, y, second,
            x + width, y + height, second,
            x, y + height, first
          )
        }
      }, object : Slider(0f, 1f, 0.001f, false) {
        init {
          value = a

          moved { value ->
            a = value
            updateColor(true)
          }
        }
      }.also { aSlider = it }).growX().row()

      hexField = setter.field(current.toString()) { value ->
        try {
          current.set(Color.valueOf(value).a(a))
          current.toHsv(values)
          h = values[0]
          s = values[1]
          v = values[2]
          a = current.a

          hSlider.setValue(h)
          sSlider.setValue(s)
          vSlider.setValue(v)
          aSlider.setValue(a)

          updateColor(false)
        } catch (ignored: Exception) {
        }
      }.size(130f, 40f).valid { text ->
        //garbage performance but who cares this runs only every key type anyway
        try {
          Color.valueOf(text)
          return@valid true
        } catch (e: Exception) {
          return@valid false
        }
      }.get()
    }.pad(8f)
  }
}
