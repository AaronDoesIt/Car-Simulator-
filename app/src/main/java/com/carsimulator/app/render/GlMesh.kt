package com.carsimulator.app.render

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/** GPU copy of a [MeshData]. Call only on the GL thread. */
class GlMesh(data: MeshData, private val dynamic: Boolean = false) {
    private val vbo = IntArray(1)
    private val ibo = IntArray(1)
    private val indexCount = data.indexCount
    private val vertexBuffer: FloatBuffer
    val vertexArray: FloatArray = data.vertexArray()

    init {
        vertexBuffer = ByteBuffer.allocateDirect(vertexArray.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vertexArray).position(0)
        val indexBuffer: ShortBuffer = ByteBuffer.allocateDirect(indexCount * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        indexBuffer.put(data.indexArray()).position(0)

        GLES20.glGenBuffers(1, vbo, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER, vertexArray.size * 4, vertexBuffer,
            if (dynamic) GLES20.GL_DYNAMIC_DRAW else GLES20.GL_STATIC_DRAW,
        )
        GLES20.glGenBuffers(1, ibo, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo[0])
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexCount * 2, indexBuffer, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    /** Re-upload [vertexArray] after editing it in place (deformation). */
    fun updateVertices() {
        vertexBuffer.position(0)
        vertexBuffer.put(vertexArray).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, vertexArray.size * 4, vertexBuffer)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun draw(program: ShaderProgram) {
        val strideBytes = MeshData.STRIDE * 4
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
        GLES20.glEnableVertexAttribArray(program.aPosition)
        GLES20.glVertexAttribPointer(program.aPosition, 3, GLES20.GL_FLOAT, false, strideBytes, 0)
        GLES20.glEnableVertexAttribArray(program.aNormal)
        GLES20.glVertexAttribPointer(program.aNormal, 3, GLES20.GL_FLOAT, false, strideBytes, MeshData.NORMAL_OFFSET * 4)
        GLES20.glEnableVertexAttribArray(program.aColor)
        GLES20.glVertexAttribPointer(program.aColor, 3, GLES20.GL_FLOAT, false, strideBytes, MeshData.COLOR_OFFSET * 4)
        GLES20.glEnableVertexAttribArray(program.aMaterial)
        GLES20.glVertexAttribPointer(program.aMaterial, 1, GLES20.GL_FLOAT, false, strideBytes, MeshData.MATERIAL_OFFSET * 4)
        GLES20.glEnableVertexAttribArray(program.aUv)
        GLES20.glVertexAttribPointer(program.aUv, 2, GLES20.GL_FLOAT, false, strideBytes, MeshData.UV_OFFSET * 4)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo[0])
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun release() {
        GLES20.glDeleteBuffers(1, vbo, 0)
        GLES20.glDeleteBuffers(1, ibo, 0)
    }
}
