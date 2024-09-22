package tmi.ui.designer

import arc.func.Cons
import arc.func.Cons2
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.input.KeyCode
import arc.math.Angles
import arc.math.Mathf
import arc.scene.Element
import arc.scene.Group
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.scene.style.Drawable
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.util.Align
import mindustry.graphics.Pal
import mindustry.ui.Fonts
import tmi.util.Consts
import tmi.util.Shapes
import tmi.util.vec1
import tmi.util.vec2
import kotlin.math.roundToInt

class PieChartSetter<T>(
  proportionEntries: List<Pair<T, Float>>,
  private val callback: Cons<List<Pair<T, Float>>>,
  back: Drawable? = null,
  private val colorList: List<Color> = listOf(
    Pal.accent,
    Pal.heal,
    Pal.place,
    Pal.remove,
    Pal.reactorPurple,
    Pal.gray,
  ),
  private val colorSetter: Cons2<T, Color>? = null,
): Table(back){
  private var externUpdated = false
  private val proportionsData = proportionEntries.map { ProportionData(it.first, it.second, 90f, 0f) }

  init {
    normalize()
    assignmentAngles()

    table{ pie ->
      pie.add(object : Group(){
        override fun draw() {
          proportionsData.forEachIndexed { i, it ->
            Draw.color(colorList[i % colorList.size])
            Shapes.fan(
              getX(Align.center),
              getY(Align.center),
              width/2 - Scl.scl(12f),
              it.angleStep,
              it.angle,
            )
            Draw.reset()
          }

          proportionsData.forEach {
            Fonts.outline.draw(
              "${(it.angleStep/3.60f).roundToInt()}%",
              getX(Align.center) + Angles.trnsx(it.angle + it.angleStep/2, getWidth()/4),
              getY(Align.center) + Angles.trnsy(it.angle + it.angleStep/2, getWidth()/4),
              Color.white,
              Scl.scl(0.5f),
              false,
              Align.center
            )
          }

          super.draw()
        }
      }.apply par@{
        proportionsData.forEachIndexed { i, t ->
          var hover = false
          var touched = false

          addChild(object : Element(){
            var dX = 0f
            var dY = 0f

            override fun draw() {
              super.draw()

              Lines.stroke(2f, Color.white)
              Lines.line(
                getX(Align.center),
                getY(Align.center),
                getX(Align.center) - dX,
                getY(Align.center) - dY
              )

              Draw.color(if (touched) Color.lightGray else if (hover) Pal.accent else Color.white)
              val ang = Angles.angle(
                getX(Align.center),
                getY(Align.center),
                getX(Align.center) - dX,
                getY(Align.center) - dY
              )

              Consts.panner.draw(
                x, y, width/2, height/2,
                width, height, 1f, 1f,
                ang
              )
            }

            override fun act(delta: Float) {
              super.act(delta)
              dX = Angles.trnsx(t.angle, this@par.width/2 - 6)
              dY = Angles.trnsy(t.angle, this@par.width/2 - 6)
              setPosition(
                this@par.getX(Align.center) + dX,
                this@par.getY(Align.center) + dY,
                Align.center
              )
            }
          }.apply {
            t.element = this

            setSize(36f)
            addListener(object : InputListener(){
              override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Element?) {
                hover = true
              }

              override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Element?) {
                hover = false
              }

              override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?): Boolean {
                touched = true
                return true
              }

              override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?) {
                touched = false
                computeProportion()
              }

              override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                vec1.set(x, y)
                vec2.set(this@par.getX(Align.center), this@par.getY(Align.center))
                localToParentCoordinates(vec1)
                vec1.sub(vec2)

                moveAnchor(i, vec1.angle())
              }
            })
          })
        }
      }).grow()
    }.size(220f)
  }

  override fun act(delta: Float) {
    super.act(delta)

    if (externUpdated){
      normalize()
      assignmentAngles()
      computeProportion()

      externUpdated = false
    }

    proportionsData.forEachIndexed { i, it ->
      val color = colorList[i % colorList.size]
      colorSetter?.get(it.obj, color)
    }
  }

  fun set(obj: T, value: Float){
    proportionsData.find { it.obj == obj }?.proporition = value
    externUpdated = true
  }

  fun average(){
    proportionsData.forEach { it.proporition = 1f/proportionsData.size }
    externUpdated = true
  }

  private fun moveAnchor(anchorIndex: Int, toAngle: Float){
    val l = proportionsData[Mathf.mod(anchorIndex - 1, proportionsData.size)]
    val s = proportionsData[anchorIndex]
    val n = proportionsData[Mathf.mod(anchorIndex + 1, proportionsData.size)]

    if (toAngle > n.angle && toAngle < l.angle) return

    s.angle = Mathf.mod(toAngle, 360f)
    s.angleStep = Mathf.mod(n.angle - s.angle, 360f)
    l.angleStep = Mathf.mod(s.angle - l.angle, 360f)
  }

  private fun normalize() {
    val pros = proportionsData.associate { it.obj to it.proporition }

    val total = pros.values.sum()
    proportionsData.forEach { it.proporition = (pros[it.obj] ?: (1f/proportionsData.size))/total }
  }

  private fun assignmentAngles(){
    proportionsData.first().angle = Mathf.mod(proportionsData.first().angle, 360f)
    var off = proportionsData.first().angle
    proportionsData.forEach { d ->
      d.angle = off
      d.angleStep = d.proporition * 360f
      off += d.angleStep
    }
  }

  private fun computeProportion(){
    proportionsData.forEach {
      val v = it.angleStep/360f
      it.proporition = v
    }

    callback.get(proportionsData.map { it.obj to it.proporition })
  }

  private inner class ProportionData(
    val obj: T,
    var proporition: Float,
    var angle: Float,
    var angleStep: Float
  ){
    var element: Element? = null
  }
}


