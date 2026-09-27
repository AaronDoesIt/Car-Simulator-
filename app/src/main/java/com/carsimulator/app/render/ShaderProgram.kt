package com.carsimulator.app.render

import android.opengl.GLES20

/** Single Lambert-lit vertex-colour program used for everything on screen. */
class ShaderProgram {
    val id: Int
    val aPosition: Int
    val aNormal: Int
    val aColor: Int
    val uMvp: Int
    val uModel: Int
    val uLightDir: Int
    val uFog: Int
    val uSky: Int
    val uGround: Int
    val uUnlit: Int
    val uAlpha: Int

    init {
        val vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT)
        id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vs)
        GLES20.glAttachShader(id, fs)
        GLES20.glLinkProgram(id)
        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { "Shader link failed: " + GLES20.glGetProgramInfoLog(id) }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        aPosition = GLES20.glGetAttribLocation(id, "aPosition")
        aNormal = GLES20.glGetAttribLocation(id, "aNormal")
        aColor = GLES20.glGetAttribLocation(id, "aColor")
        uMvp = GLES20.glGetUniformLocation(id, "uMvp")
        uModel = GLES20.glGetUniformLocation(id, "uModel")
        uLightDir = GLES20.glGetUniformLocation(id, "uLightDir")
        uFog = GLES20.glGetUniformLocation(id, "uFog")
        uSky = GLES20.glGetUniformLocation(id, "uSky")
        uGround = GLES20.glGetUniformLocation(id, "uGround")
        uUnlit = GLES20.glGetUniformLocation(id, "uUnlit")
        uAlpha = GLES20.glGetUniformLocation(id, "uAlpha")
    }

    fun use() = GLES20.glUseProgram(id)

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { "Shader compile failed: " + GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    companion object {
        private const val VERTEX = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec3 aColor;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying float vDepth;
            void main() {
                vec4 world = uModel * vec4(aPosition, 1.0);
                vNormal = normalize(mat3(uModel) * aNormal);
                vColor = aColor;
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vDepth = gl_Position.w;
            }
        """

        private const val FRAGMENT = """
            precision mediump float;
            uniform vec3 uLightDir;
            uniform vec3 uFog;
            uniform vec3 uSky;
            uniform vec3 uGround;
            uniform float uUnlit;
            uniform float uAlpha;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying float vDepth;
            void main() {
                vec3 n = normalize(vNormal);
                float diffuse = max(dot(n, uLightDir), 0.0);
                // Hemisphere ambient: sky colour from above, ground bounce from below.
                vec3 ambient = mix(uGround, uSky, n.y * 0.5 + 0.5);
                vec3 lit = vColor * (ambient * 0.6 + vec3(0.78) * diffuse);
                lit = mix(lit, vColor, uUnlit);
                float fog = clamp((vDepth - 150.0) / 1100.0, 0.0, 0.9) * (1.0 - uUnlit);
                gl_FragColor = vec4(mix(lit, uFog, fog), uAlpha);
            }
        """
    }
}
