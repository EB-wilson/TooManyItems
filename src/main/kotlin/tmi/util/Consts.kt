package tmi.util

import arc.Core
import arc.func.Prov
import arc.graphics.Color
import arc.scene.style.Drawable
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.Dialog.DialogStyle
import arc.struct.Seq
import arc.util.Tmp
import mindustry.content.Blocks
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Fonts
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.world.blocks.environment.Floor

object Consts {
  private val emp: Seq<*> = Seq<Any>()

  val grayUI: Drawable by lazy { (Tex.whiteui as TextureRegionDrawable).tint(Tmp.c1.set(Pal.darkerGray)) }
  val darkGrayUI: Drawable by lazy { (Tex.whiteui as TextureRegionDrawable).tint(Tmp.c1.set(Pal.darkestGray)) }
  val grayUIAlpha: Drawable by lazy { (Tex.whiteui as TextureRegionDrawable).tint(Tmp.c1.set(Pal.darkerGray).a(0.7f)) }
  val darkGrayUIAlpha: Drawable by lazy { (Tex.whiteui as TextureRegionDrawable).tint(Tmp.c1.set(Pal.darkestGray).a(0.7f)) }
  val padGrayUIAlpha: Drawable by lazy {
    val res = (Tex.whiteui as TextureRegionDrawable).tint(Tmp.c1.set(Pal.darkerGray).a(0.7f))
    res.leftWidth = 8f
    res.rightWidth = 8f
    res.topHeight = 8f
    res.bottomHeight = 8f
    return@lazy res
  }
  val a_z: Drawable by lazy { Core.atlas.getDrawable("tmi-a_z") }
  val tmi: Drawable by lazy { Core.atlas.getDrawable("tmi-tmi") }
  val transparent: Drawable by lazy { (Tex.whiteui as TextureRegionDrawable).tint(Color.clear) }

  val transparentBack: DialogStyle by lazy { object : DialogStyle() {
    init {
      stageBackground = transparent
      titleFont = Fonts.outline
      background = transparent
      titleFontColor = Pal.accent
    }
  }}

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
