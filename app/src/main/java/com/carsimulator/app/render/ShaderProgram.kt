package com.carsimulator.app.render

import android.opengl.GLES20

/**
 * The one program everything is drawn with. Lit mode does hemisphere
 * ambient, a warm sun with Blinn-Phong highlights, a sky reflection with
 * fresnel for paint, glass and chrome, world-space detail texturing chosen
 * per material, and distance fog. Unlit mode is for the shadow blob and sky
 * mode shades the dome by view direction.
 */
class ShaderProgram {
    val id: Int
    val aPosition: Int
    val aNormal: Int
    val aColor: Int
    val aMaterial: Int
    val uMvp: Int
    val uModel: Int
    val uLightDir: Int
    val uEye: Int
    val uFog: Int
    val uSkyZenith: Int
    val uSkyHorizon: Int
    val uGround: Int
    val uSun: Int
    val uMode: Int
    val uAlpha: Int
    val uDetail: Int

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
        aMaterial = GLES20.glGetAttribLocation(id, "aMaterial")
        uMvp = GLES20.glGetUniformLocation(id, "uMvp")
        uModel = GLES20.glGetUniformLocation(id, "uModel")
        uLightDir = GLES20.glGetUniformLocation(id, "uLightDir")
        uEye = GLES20.glGetUniformLocation(id, "uEye")
        uFog = GLES20.glGetUniformLocation(id, "uFog")
        uSkyZenith = GLES20.glGetUniformLocation(id, "uSkyZenith")
        uSkyHorizon = GLES20.glGetUniformLocation(id, "uSkyHorizon")
        uGround = GLES20.glGetUniformLocation(id, "uGround")
        uSun = GLES20.glGetUniformLocation(id, "uSun")
        uMode = GLES20.glGetUniformLocation(id, "uMode")
        uAlpha = GLES20.glGetUniformLocation(id, "uAlpha")
        uDetail = GLES20.glGetUniformLocation(id, "uDetail")
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
        const val MODE_LIT = 0f
        const val MODE_UNLIT = 1f
        const val MODE_SKY = 2f

        private const val VERTEX = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec3 aColor;
            attribute float aMaterial;
            varying vec3 vWorld;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying float vMaterial;
            varying float vDepth;
            void main() {
                vec4 world = uModel * vec4(aPosition, 1.0);
                vWorld = world.xyz;
                vNormal = normalize(mat3(uModel) * aNormal);
                vColor = aColor;
                vMaterial = aMaterial;
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vDepth = gl_Position.w;
            }
        """

        private const val FRAGMENT = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform vec3 uLightDir;
            uniform vec3 uEye;
            uniform vec3 uFog;
            uniform vec3 uSkyZenith;
            uniform vec3 uSkyHorizon;
            uniform vec3 uGround;
            uniform vec3 uSun;
            uniform float uMode;
            uniform float uAlpha;
            uniform sampler2D uDetail;
            varying vec3 vWorld;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying float vMaterial;
            varying float vDepth;

            vec3 skyColor(vec3 dir) {
                float t = pow(clamp(dir.y, 0.0, 1.0), 0.5);
                vec3 c = mix(uSkyHorizon, uSkyZenith, t);
                float s = max(dot(dir, uLightDir), 0.0);
                c += uSun * (pow(s, 900.0) * 4.0 + pow(s, 24.0) * 0.25 + pow(s, 3.0) * 0.06);
                return c;
            }

            void main() {
                if (uMode > 1.5) {
                    vec3 dir = normalize(vWorld - uEye);
                    vec3 c = skyColor(dir);
                    // Clouds: the ground channel projected onto a flat layer overhead.
                    vec2 cuv = dir.xz / (max(dir.y, 0.0) + 0.18) * 0.55 + vec2(0.31, 0.77);
                    float cl = texture2D(uDetail, cuv).g;
                    float cover = smoothstep(0.55, 0.80, cl) * smoothstep(0.0, 0.22, dir.y);
                    vec3 cloud = mix(vec3(0.78, 0.80, 0.85), vec3(1.0), smoothstep(0.6, 0.95, cl));
                    c = mix(c, cloud, cover * 0.9);
                    gl_FragColor = vec4(c, 1.0);
                    return;
                }
                if (uMode > 0.5) {
                    gl_FragColor = vec4(vColor, uAlpha);
                    return;
                }

                vec3 n = normalize(vNormal);
                vec3 v = normalize(uEye - vWorld);
                float m = vMaterial;

                float specStr = 0.0;
                float shin = 16.0;
                float refl = 0.0;
                float detailAmt = 0.0;
                float texScale = 0.35;
                vec4 dmask = vec4(0.0);
                bool lamp = false;
                if (m < 0.95) {
                    float rock = clamp(m / 0.9, 0.0, 1.0);
                    dmask = mix(vec4(0.0, 1.0, 0.0, 0.0), vec4(0.0, 0.0, 1.0, 0.0), rock);
                    detailAmt = 0.40;
                    texScale = 0.22;
                } else if (m < 1.5) {
                    dmask = vec4(1.0, 0.0, 0.0, 0.0);
                    detailAmt = 0.30;
                    texScale = 1.1;
                    specStr = 0.12; shin = 6.0;
                } else if (m < 2.5) {
                    specStr = 0.9; shin = 70.0; refl = 0.32;
                } else if (m < 3.5) {
                    specStr = 1.3; shin = 140.0; refl = 0.55;
                } else if (m < 4.5) {
                    specStr = 1.0; shin = 90.0; refl = 0.85;
                } else if (m < 5.5) {
                    specStr = 0.06; shin = 5.0;
                } else if (m < 6.5) {
                    dmask = vec4(0.0, 0.0, 0.0, 1.0);
                    detailAmt = 0.6;
                    texScale = 0.9;
                } else if (m < 7.5) {
                    dmask = vec4(0.0, 0.0, 1.0, 0.0);
                    detailAmt = 0.5;
                    texScale = 0.6;
                } else if (m < 8.5) {
                    lamp = true;
                } else {
                    specStr = 0.7; shin = 40.0; refl = 0.40;
                }

                // World-space detail: top-down for flat surfaces, side projection for walls.
                vec2 uvTop = vWorld.xz * texScale;
                vec2 uvSide = vec2(vWorld.x + vWorld.z, vWorld.y) * texScale;
                vec4 tTop = texture2D(uDetail, uvTop);
                vec4 tSide = texture2D(uDetail, uvSide);
                // A second, seven times coarser sample breaks the visible tiling of the first.
                vec4 tMacro = texture2D(uDetail, uvTop * 0.143 + vec2(0.37, 0.61));
                vec4 t = mix(tSide, tTop, abs(n.y));
                float d = dot(t, dmask) * 0.5 + dot(tMacro, dmask) * 0.5;
                float detail = 1.0 + (d - 0.5) * 2.0 * detailAmt;
                vec3 base = vColor * detail;

                float fog = clamp((vDepth - 90.0) / 1100.0, 0.0, 0.92);

                if (lamp) {
                    vec3 lc = base * 1.25;
                    gl_FragColor = vec4(mix(lc, uFog, fog), uAlpha);
                    return;
                }

                float diff = max(dot(n, uLightDir), 0.0);
                vec3 ambient = mix(uGround, uSkyZenith, n.y * 0.5 + 0.5) * 0.62;
                vec3 c = base * (ambient + uSun * diff * 0.95);

                float ndv = max(dot(n, v), 0.0);
                float fres = pow(1.0 - ndv, 4.0);
                if (refl > 0.0) {
                    vec3 r = reflect(-v, n);
                    vec3 env = skyColor(r);
                    env = mix(env, uGround * 0.8, clamp(-r.y * 3.0, 0.0, 1.0));
                    c = mix(c, env, refl * (0.30 + 0.70 * fres));
                }
                if (specStr > 0.0) {
                    vec3 h = normalize(uLightDir + v);
                    float sp = pow(max(dot(n, h), 0.0), shin);
                    c += uSun * sp * specStr;
                }
                gl_FragColor = vec4(mix(c, uFog, fog), uAlpha);
            }
        """
    }
}
