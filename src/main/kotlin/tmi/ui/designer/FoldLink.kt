package tmi.ui.designer

import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.ui.layout.Scl
import arc.util.Align
import mindustry.gen.Icon
import mindustry.graphics.Pal
import tmi.util.drawable1

open class FoldLink(
  val card: Card,
  val linker: ItemLinker?,
  val inFold: Boolean
): Element() {
  val centerPos = Vec2()
  val linesColor: Color = Pal.accent.cpy()

  override fun act(delta: Float) {
    super.act(delta)
    localToAscendantCoordinates(card.ownerDesigner, centerPos.set(width/2, height/2))
  }

  override fun draw() {
    super.draw()

    Draw.color(card.backColor)
    Fill.circle(getX(Align.center), getY(Align.center), width/2 + Scl.scl(6f))
    Lines.stroke(Scl.scl(4f), card.foldColor)
    Lines.circle(getX(Align.center), getY(Align.center), width/2)

    val icon = (card.foldIcon?: when(card){
      is RecipeCard -> drawable1.set(card.recipe.ownerBlock?.icon).takeIf { it.region != null }?: Icon.layers
      is IOCard -> if (card.isInput) Icon.download else Icon.upload
      else -> Icon.layers
    })
    val isFont = icon::class.java.name.contains("Fonts")
    Draw.color(if (!isFont) Color.white else card.iconColor)
    icon.draw(
      getX(Align.center) - width/2f*0.7f, getY(Align.center) - height/2f*0.7f,
      width/2f*0.7f, height/2f*0.7f,
      width*(0.7f.takeIf { !isFont }?:1f), height*(0.7f.takeIf { !isFont }?:1f),
      0.7f, 0.7f,
      0f
    )
  }
}