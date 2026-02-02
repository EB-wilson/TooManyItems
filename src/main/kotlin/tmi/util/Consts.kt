package tmi.util

import arc.Core
import arc.func.Prov
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.scene.style.BaseDrawable
import arc.scene.style.Drawable
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.Button
import arc.scene.ui.Dialog.DialogStyle
import arc.scene.ui.layout.Scl
import arc.struct.Seq
import arc.util.Tmp
import mindustry.content.Blocks
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Fonts
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.world.blocks.environment.Floor
import java.lang.reflect.Field

object Consts {
  private val emp: Seq<*> = Seq<Any>()

  val buildTimeAlter: Field = try {
    Block::class.java.getField("buildTime")
  } catch (e: NoSuchFieldException) {
    Block::class.java.getField("buildCost")
  }

  val foldCardIcons: List<Drawable> by lazy {
    Icon.icons.map { it.key to it.value }.sortedBy { it.first }.filter { !it.first.contains("Small") }.map { it.second }
  }

  val grayUI: Drawable by lazy { (Tex.whiteui as TextureRegionDrawable).tint(Tmp.c1.set(Pal.darkerGray)) }
  val darkGrayUI: Drawable by lazy { (Tex.whiteui as TextureRegionDrawable).tint(Tmp.c1.set(Pal.darkestGray)) }
  val midGrayUI: Drawable by lazy { (Tex.whiteui as TextureRegionDrawable).tint(Tmp.c1.set(Pal.darkerGray).lerp(Pal.darkestGray, 0.7f)) }
  val grayUIAlpha: Drawable by lazy { (Tex.whiteui as TextureRegionDrawable).tint(Tmp.c1.set(Pal.darkerGray).a(0.7f)) }
  val darkGrayUIAlpha: Drawable by lazy { (Tex.whiteui as TextureRegionDrawable).tint(Tmp.c1.set(Pal.darkestGray).a(0.7f)) }
  val padGrayUI: Drawable by lazy {
    val res = (Tex.whiteui as TextureRegionDrawable).tint(Tmp.c1.set(Pal.darkerGray))
    res.leftWidth = 8f
    res.rightWidth = 8f
    res.topHeight = 8f
    res.bottomHeight = 8f
    return@lazy res
  }
  val padDarkGrayUI: Drawable by lazy {
    val res = (Tex.whiteui as TextureRegionDrawable).tint(Tmp.c1.set(Pal.darkestGray))
    res.leftWidth = 8f
    res.rightWidth = 8f
    res.topHeight = 8f
    res.bottomHeight = 8f
    return@lazy res
  }
  val padGrayUIAlpha: Drawable by lazy {
    val res = (Tex.whiteui as TextureRegionDrawable).tint(Tmp.c1.set(Pal.darkerGray).a(0.7f))
    res.leftWidth = 8f
    res.rightWidth = 8f
    res.topHeight = 8f
    res.bottomHeight = 8f
    return@lazy res
  }
  val padDarkGrayUIAlpha: Drawable by lazy {
    val res = (Tex.whiteui as TextureRegionDrawable).tint(Tmp.c1.set(Pal.darkestGray).a(0.7f))
    res.leftWidth = 8f
    res.rightWidth = 8f
    res.topHeight = 8f
    res.bottomHeight = 8f
    return@lazy res
  }
  val leftLine by lazy { object : BaseDrawable(){
    init {
      leftWidth = 4f
      rightWidth = 4f
      topHeight = 4f
      bottomHeight = 4f
    }

    override fun draw(x: Float, y: Float, width: Float, height: Float) {
      val lw = Scl.scl(3f)
      Lines.stroke(lw, Color.gray)
      Lines.line(x + lw/2f, y + lw, x + lw/2f, y + height - lw)
    }
  } }
  val a_z: Drawable by lazy { Core.atlas.getDrawable("tmi-a_z") }
  val tmi: Drawable by lazy { Core.atlas.getDrawable("tmi-tmi") }
  val panner: Drawable by lazy { Core.atlas.getDrawable("tmi-panner") }
  val balance: Drawable by lazy { Core.atlas.getDrawable("tmi-balance") }
  val inbalance: Drawable by lazy { Core.atlas.getDrawable("tmi-inbalance") }
  val time: Drawable by lazy { Core.atlas.getDrawable("tmi-time") }
  val clip: Drawable by lazy { Core.atlas.getDrawable("tmi-clip") }

  val side_bottom: Drawable by lazy { Core.atlas.getDrawable("tmi-side_bottom") }
  val side_top: Drawable by lazy { Core.atlas.getDrawable("tmi-side_top") }
  val side_left: Drawable by lazy { Core.atlas.getDrawable("tmi-side_left") }
  val side_right: Drawable by lazy { Core.atlas.getDrawable("tmi-side_right") }

  val transparent: Drawable by lazy { (Tex.whiteui as TextureRegionDrawable).tint(Color.clear) }

  val transparentBack: DialogStyle by lazy { DialogStyle().apply {
    stageBackground = transparent
    titleFont = Fonts.outline
    background = transparent
    titleFontColor = Pal.accent
  } }

  val recipeTabSelector: Button.ButtonStyle by lazy { Button.ButtonStyle().apply{
    up = object : BaseDrawable(){
      override fun draw(x: Float, y: Float, width: Float, height: Float) {
        Lines.stroke(Scl.scl(8f), Pal.accent)
        Lines.rect(x, y, width, height)
      }
    }
    over = object : BaseDrawable(){
      override fun draw(x: Float, y: Float, width: Float, height: Float) {
        Lines.stroke(Scl.scl(8f), Color.white)
        Lines.rect(x, y, width, height)
      }
    }
    down = object : BaseDrawable(){
      override fun draw(x: Float, y: Float, width: Float, height: Float) {
        Lines.stroke(Scl.scl(8f), Color.gray)
        Lines.rect(x, y, width, height)
      }
    }
  } }

  val markerTile: Tile = object : Tile(0, 0) {
    override fun setFloor(type: Floor) {
      this.floor = type
      this.overlay = Blocks.air as Floor
    }

    override fun setOverlay(block: Block) {
      this.overlay = block as Floor
    }

    override fun setBlock(type: Block, team: Team, rotation: Int, entityprov: Prov<Building>) {
      this.block = type
      this.build = entityprov.get()
      build.team = team
      build.rotation = rotation
    }
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> empSeq(): Seq<T> {
    return emp as Seq<T>
  }
}
