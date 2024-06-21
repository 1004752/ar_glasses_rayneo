package com.sk.vcs.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.AttributeSet
import android.view.Surface
import com.sk.vcs.VcsDefine
import com.sk.vcs.data.AlphaDataInfo
import com.sk.vcs.data.MediaNativeBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class VcsGlTextureView : GLTextureView {
    private lateinit var mRenderer: VideoRender
    private var mCallback: SurfaceCallback? = null

    fun interface SurfaceCallback {
        fun onSurfaceCreated(surface: Surface?)
    }

    fun setSurfaceCallback(callback: SurfaceCallback) {
        mCallback = callback
    }

    constructor(context: Context) : super(context) {
        initialize(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs)
    }

    private fun initialize(context: Context, attrs: AttributeSet?) {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        mRenderer = VideoRender(this)
        setRenderer(mRenderer)
    }

    override fun onResume() {
        super.onResume()
    }

    fun updateAlphaChannel(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        data: ByteArray?,
        length: Int,
        timestamp: Long
    ) {
        mRenderer.updateAlphaChannel(x, y, w, h, data, length, timestamp)
    }

    fun setAlphaFrameTimestamp(timestamp: Long) {
        mRenderer.setAlphaFrameTimestamp(timestamp)
    }

    fun setAlphaNativeBuffer(alphaNativeBuffer: MediaNativeBuffer?) {
        mRenderer.setAlphaNativeBuffer(alphaNativeBuffer)
    }

    fun updateSurface() {
        mRenderer.updateSurface()
    }

    fun reset() {
        mRenderer.reset()
    }

    private class VideoRender(private val mVcsGlSurfaceView: VcsGlTextureView?) : Renderer,
        OnFrameAvailableListener {
        private val mTriangleVertices: FloatBuffer
        private val mTriangleUVs: FloatBuffer

        private val mMVPMatrix = FloatArray(16)
        private val mSTMatrix = FloatArray(16)

        private var mProgram = -1
        private var mTextureID = 0
        private var muMVPMatrixHandle = 0
        private var muSTMatrixHandle = 0
        private var maPositionHandle = 0
        private var maTextureHandle = 0

        private var mSurfaceTexture: SurfaceTexture? = null
        private var mUpdateSurface = false

        private val GL_TEXTURE_EXTERNAL_OES = 0x8D65

        private var mTextureLocationHd = 0
        private var mAlphaChannelTextureLocationHd = 0
        private var mAlphaChannelTextureName = 0
        private val mAlphaData = ByteArray(VcsDefine.SCREEN_WIDTH * VcsDefine.SCREEN_HEIGHT)

        private val mAlphaFrameQueue = ConcurrentLinkedQueue<AlphaDataInfo>()

        private var mLastFrameTimestamp: Long = 0

        private var mAlphaNativeBuffer: MediaNativeBuffer? = null

        private val mLockObject = Any()

        init {
            val verticesData =
                floatArrayOf(-1.0f, -1.0f, 0f, 1.0f, -1.0f, 0f, -1.0f, 1.0f, 0f, 1.0f, 1.0f, 0f)
            mTriangleVertices =
                ByteBuffer.allocateDirect(verticesData.size * FLOAT_SIZE_BYTES).order(
                    ByteOrder.nativeOrder()
                ).asFloatBuffer()
            mTriangleVertices.put(verticesData).position(0)

            val uvsData = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
            mTriangleUVs = ByteBuffer.allocateDirect(uvsData.size * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            mTriangleUVs.put(uvsData).position(0)

            Matrix.setIdentityM(mSTMatrix, 0)
        }

        fun setAlphaFrameTimestamp(timestamp: Long) {
            synchronized(mLockObject) {
                if (timestamp > mLastFrameTimestamp) {
                    mLastFrameTimestamp = timestamp
                }
            }
        }

        fun setAlphaNativeBuffer(alphaNativeBuffer: MediaNativeBuffer?) {
            mAlphaNativeBuffer = alphaNativeBuffer
        }

        fun updateSurface() {
            mUpdateSurface = true
        }

        fun reset() {
            synchronized(mLockObject) {
                mLastFrameTimestamp = 0
                mAlphaFrameQueue.clear()
            }
        }

        fun updateAlphaChannel(
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            data: ByteArray?,
            length: Int,
            timestamp: Long
        ) {
            val alphaData = AlphaDataInfo()
            alphaData.mX = x
            alphaData.mY = y
            alphaData.mW = w
            alphaData.mH = h
            alphaData.mByteArray = data!!
            alphaData.mLength = length
            alphaData.mTimeStamp = timestamp

            // 추가
            synchronized(mLockObject) {
                mAlphaFrameQueue.add(alphaData)
            }
        }

        override fun onDrawFrame(glUnused: GL10?) {
            if (!mUpdateSurface) {
                return
            }

            synchronized(this) {
                if (mSurfaceTexture != null && mLastFrameTimestamp > 0) {
                    mSurfaceTexture!!.updateTexImage()
                    mSurfaceTexture!!.getTransformMatrix(mSTMatrix)
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mAlphaChannelTextureName)

                    synchronized(mLockObject) {
                        while (mAlphaFrameQueue.size > 0) {
                            val alphaData = mAlphaFrameQueue.peek() ?: break
                            if (alphaData.mTimeStamp <= mLastFrameTimestamp) {
                                val alphaBuffer =
                                    ByteBuffer.wrap(alphaData.mByteArray, 0, alphaData.mLength)
                                GLES20.glTexSubImage2D(
                                    GLES20.GL_TEXTURE_2D,
                                    0,
                                    alphaData.mX,
                                    alphaData.mY,
                                    alphaData.mW,
                                    alphaData.mH,
                                    GLES20.GL_LUMINANCE,
                                    GLES20.GL_UNSIGNED_BYTE,
                                    alphaBuffer
                                )

                                if (mAlphaNativeBuffer != null) {
                                    mAlphaNativeBuffer!!.releaseBuffer(alphaData.mByteArray)
                                }
                                mAlphaFrameQueue.remove(alphaData)
                            } else {
                                break
                            }
                        }
                    }
                    mLastFrameTimestamp = 0
                }
                GLES20.glUseProgram(mProgram)
                GLES20.glUniform1i(mAlphaChannelTextureLocationHd, 2)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureID)
                GLES20.glUniform1i(mTextureLocationHd, 0)

                mTriangleVertices.position(0)
                GLES20.glVertexAttribPointer(
                    maPositionHandle,
                    3,
                    GLES20.GL_FLOAT,
                    false,
                    3 * FLOAT_SIZE_BYTES,
                    mTriangleVertices
                )
                GLES20.glEnableVertexAttribArray(maPositionHandle)

                mTriangleUVs.position(0)
                GLES20.glVertexAttribPointer(
                    maTextureHandle,
                    2,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    mTriangleUVs
                )
                GLES20.glEnableVertexAttribArray(maTextureHandle)

                Matrix.setIdentityM(mMVPMatrix, 0)
                GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0)
                GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            }
        }

        override fun onSurfaceCreated(glUnused: GL10?, config: EGLConfig?) {
            mProgram = createProgram()
            if (mProgram == 0) {
                return
            }
            maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition")
            checkGlError("glGetAttribLocation aPosition")
            if (maPositionHandle == -1) {
                throw RuntimeException("Could not get attrib location for aPosition")
            }

            maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord")
            checkGlError("glGetAttribLocation aTextureCoord")
            if (maTextureHandle == -1) {
                throw RuntimeException("Could not get attrib location for aTextureCoord")
            }

            muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
            checkGlError("glGetUniformLocation uMVPMatrix")
            if (muMVPMatrixHandle == -1) {
                throw RuntimeException("Could not get attrib location for uMVPMatrix")
            }

            muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix")
            checkGlError("glGetUniformLocation uSTMatrix")
            if (muSTMatrixHandle == -1) {
                throw RuntimeException("Could not get attrib location for uSTMatrix")
            }

            mTextureLocationHd = GLES20.glGetUniformLocation(mProgram, "sTexture")
            mAlphaChannelTextureLocationHd = GLES20.glGetUniformLocation(mProgram, "alphaChannel")
            GLES20.glGenTextures(1, IntArray(1).apply { mTextureID = this[0] }, 0)

            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureID)
            checkGlError("glBindTexture mTextureID")
            GLES20.glTexParameterf(
                GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST.toFloat()
            )
            GLES20.glTexParameterf(
                GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR.toFloat()
            )

            GLES20.glTexParameterf(
                GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE.toFloat()
            )
            GLES20.glTexParameterf(
                GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE.toFloat()
            )

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            mAlphaChannelTextureName = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mAlphaChannelTextureName)
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_LUMINANCE,
                VcsDefine.SCREEN_WIDTH,
                VcsDefine.SCREEN_HEIGHT,
                0,
                GLES20.GL_LUMINANCE,
                GLES20.GL_UNSIGNED_BYTE,
                ByteBuffer.wrap(mAlphaData)
            )

            mSurfaceTexture = SurfaceTexture(mTextureID)
            mSurfaceTexture!!.setOnFrameAvailableListener(this)
            if (mVcsGlSurfaceView?.mCallback != null) {
                val surface = Surface(mSurfaceTexture)
                mVcsGlSurfaceView.mCallback!!.onSurfaceCreated(surface)
            }
            synchronized(this) { mUpdateSurface = false }
        }

        override fun onSurfaceChanged(glUnused: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
            synchronized(this) { mUpdateSurface = true }
        }

        private fun createProgram(): Int {
            val vertexShader = loadShader(
                GLES20.GL_VERTEX_SHADER, VERTEX_SHADER
            )
            if (vertexShader == 0) {
                return 0
            }

            val pixelShader = loadShader(
                GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER
            )
            if (pixelShader == 0) {
                return 0
            }

            var program = GLES20.glCreateProgram()
            if (program != 0) {
                GLES20.glAttachShader(program, vertexShader)
                checkGlError("glAttachShader")
                GLES20.glAttachShader(program, pixelShader)
                checkGlError("glAttachShader")
                GLES20.glLinkProgram(program)
                val linkStatus = IntArray(1)
                GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
                if (linkStatus[0] != GLES20.GL_TRUE) {
                    error("Could not link program: ")
                    error(GLES20.glGetProgramInfoLog(program))
                    GLES20.glDeleteProgram(program)
                    program = 0
                }
            }
            return program
        }

        private fun loadShader(shaderType: Int, source: String): Int {
            var shader = GLES20.glCreateShader(shaderType)
            if (shader != 0) {
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val compiled = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
                if (compiled[0] == 0) {
                    error("Could not compile shader $shaderType:")
                    error(GLES20.glGetShaderInfoLog(shader))
                    GLES20.glDeleteShader(shader)
                    shader = 0
                }
            }
            return shader
        }

        private fun checkGlError(op: String) {
            var error: Int
            while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
                error("$op: glError $error")
                throw RuntimeException("$op: glError $error")
            }
        }

        companion object {
            private const val FLOAT_SIZE_BYTES = 4
            private const val VERTEX_SHADER = """uniform mat4 uMVPMatrix;
                uniform mat4 uSTMatrix;
                attribute vec4 aPosition;
                attribute vec4 aTextureCoord;
                varying vec2 vTextureCoord;
                void main() {
                    gl_Position = uMVPMatrix * aPosition;
                    vTextureCoord = (uSTMatrix * aTextureCoord).xy;
                }"""

            private const val FRAGMENT_SHADER = """#extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                uniform sampler2D alphaChannel;
                void main() {
                    vec4 textureColor = texture2D(sTexture, vTextureCoord);
                    vec4 alphaColor = texture2D(alphaChannel, vTextureCoord);
                    gl_FragColor = vec4(textureColor.rgb, alphaColor.r);
                }"""
        }
    }
}
