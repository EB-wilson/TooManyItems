package tmi.graphic

import arc.Core
import arc.func.Cons
import arc.graphics.*
import arc.graphics.gl.FrameBuffer
import arc.graphics.gl.GLFrameBuffer
import arc.graphics.gl.Shader
import arc.util.ScreenUtils
import tmi.util.accessField
import kotlin.math.ceil

class ChunkedFrameBuffer(
  val format: Pixmap.Format = Pixmap.Format.rgba8888,
  width: Int = 2,
  height: Int = 2,
  val hasDepth: Boolean = false,
  val hasStencil: Boolean = false,
) {
  companion object {
    private val currentBuffer: GLFrameBuffer<*>? by GLFrameBuffer::class.accessField("currentBoundFramebuffer")
    private val defaultBlitShader by lazy {
      Shader(
        """
        attribute vec4 a_position;
        attribute vec2 a_texCoord0;
        varying vec2 v_texCoords;
        
        void main(){
            v_texCoords = a_texCoord0;
            gl_Position = a_position;
        }
        """,
        """
        uniform sampler2D u_texture;
        varying vec2 v_texCoords;
        
        void main(){
          gl_FragColor = texture2D(u_texture, v_texCoords);
        }
        """
      )
    }

    val maxTextureSize = Gl.getInt(GL20.GL_MAX_TEXTURE_SIZE)
  }

  var imageWidth: Int = width
    private set
  var imageHeight: Int = height
    private set
  var horN: Int = 0
    private set
  var vertN: Int = 0
    private set

  private var chunks = arrayOf<FrameChunk>()

  fun resize(width: Int, height: Int) {
    imageWidth = width
    imageHeight = height

    horN = ceil(imageWidth/maxTextureSize.toFloat()).toInt()
    vertN = ceil(imageHeight/maxTextureSize.toFloat()).toInt()
    val chunksN = horN*vertN

    if (chunksN != chunks.size) {
      chunks.forEach { chunk -> chunk.buffer.dispose() }
      chunks = Array(chunksN) { i ->
        val row = i / horN
        val col = i % horN

        FrameChunk(
          FrameBuffer(
            format,
            imageWidth/horN,
            imageHeight/vertN,
            hasDepth,
            hasStencil
          ),
          row, col,
          col.toFloat()/horN,
          row.toFloat()/vertN,
          (col + 1).toFloat()/horN,
          (row + 1).toFloat()/vertN,
        )
      }
    }
    else {
      chunks.forEachIndexed { i, chunk ->
        val row = i / horN
        val col = i % horN

        chunk.resize(
          imageWidth/horN,
          imageHeight/vertN,
          col.toFloat()/horN,
          row.toFloat()/vertN,
          (col + 1).toFloat()/horN,
          (row + 1).toFloat()/vertN,
        )
      }
    }
  }

  fun blit(shader: Shader = defaultBlitShader) {
    Core.gl30?.also { gl ->
      val target = currentBuffer
      eachChunk { chunk ->
        chunk.apply { gl.copyToBuffer(target) }
      }
    }?: run {
      eachChunk { chunk ->
        chunk.blit(shader)
      }
    }
  }

  fun eachChunk(chunk: Cons<FrameChunk>) {
    chunks.forEach {
      chunk.get(it)
    }
  }

  fun toPixmap(): Pixmap {
    val result = Pixmap(imageWidth, imageHeight)

    val chunkWidth = imageWidth/horN
    val chunkHeight = imageHeight/vertN

    chunks.forEach { chunk ->
      chunk.begin()
      val pixels = ScreenUtils.getFrameBufferPixmap(0, 0, chunkWidth, chunkHeight, false)

      val startX = chunk.col * chunkWidth
      val startY = chunk.row * chunkHeight

      result.draw(pixels, startX, startY, chunkWidth, chunkHeight)

      chunk.end()
    }

    return result.flipY()
  }

  inner class FrameChunk(
    val buffer: FrameBuffer,
    val row: Int, val col: Int,
    u1: Float, v1: Float,
    u2: Float, v2: Float,
  ){
    var u1: Float = u1
      private set
    var v1: Float = v1
      private set
    var u2: Float = u2
      private set
    var v2: Float = v2
      private set

    val mesh: Mesh = Mesh(
      false, 4, 0,
      VertexAttribute.position, VertexAttribute.texCoords
    )

    val widthFactor get() = u2 - u1
    val heightFactor get() = v2 - v1

    fun resize(
      width: Int, height: Int,
      u1: Float, v1: Float,
      u2: Float, v2: Float,
    ) {
      buffer.resize(width, height)
      this.u1 = u1
      this.v1 = v1
      this.u2 = u2
      this.v2 = v2

      val x1 = u1*2 - 1
      val y1 = v1*2 - 1
      val x2 = u2*2 - 1
      val y2 = v2*2 - 1
      mesh.setVertices(floatArrayOf(
        x1, y1, 0f, 0f, //p1
        x1, y2, 0f, 1f, //p2
        x2, y2, 1f, 1f, //p3
        x2, y1, 1f, 0f, //p4
      ))
    }

    fun blit(
      shader: Shader = defaultBlitShader,
    ){
      buffer.texture.bind()
      shader.bind()
      shader.apply()
      mesh.render(shader, Gl.triangleFan)
    }

    fun GL30.copyToBuffer(target: GLFrameBuffer<*>?) {
      glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, buffer.framebufferHandle)
      glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target?.framebufferHandle ?: 0)

      val targetWidth = target?.width ?: Core.graphics.width
      val targetHeight = target?.height ?: Core.graphics.height

      glBlitFramebuffer(
        (buffer.width*u1).toInt(),
        (buffer.height*v1).toInt(),
        (buffer.width*u2).toInt(),
        (buffer.height*v2).toInt(),
        (targetWidth*u1).toInt(),
        (targetHeight*v1).toInt(),
        (targetWidth*u2).toInt(),
        (targetHeight*v2).toInt(),
        Gl.colorBufferBit,
        Gl.nearest,
      )
    }

    fun begin(color: Color) { buffer.begin(color) }
    fun begin() { buffer.begin() }
    fun end(){ buffer.end() }
  }
}