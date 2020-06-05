package com.github.blizz2inght.gridfilter;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private GLSurfaceView mPreview;
    private SurfaceTexture mSurfaceTexture;
    private CameraController mCameraController;

    private Surface mSurface;
    private int mTexName;
    private int mMaxTextureSize;
//    private float[] mMVPMatrix = new float[16];
    private DisplayMetrics mDisplayMetrics = new DisplayMetrics();
    //每一行分别是顶点和纹理坐标x, y, s, t
    private float[] mVertexArr = new float[]{
            -1.f,  1.f, 0.f, 1.f,
            -1.f, -1.f, 0.f, 0.f,
             1.f, -1.f, 1.f, 0.f,
             1.f,  1.f, 1.f, 1.f
    };

    private int mProgram;
    private int a_Position;
    private int a_TexCoord;
//    private int u_MVPMatrix;
    private float[] mTextureMatrix = new float[16];
    private FloatBuffer mVertexBuffer;
    private int u_TextureUnit;
    private int u_TextureMatrix;
    private int mWidth;
    private int mHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreen();
        setContentView(R.layout.activity_main);
        mVertexBuffer = ByteBuffer.allocateDirect(mVertexArr.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertexBuffer.position(0);
        mVertexBuffer.put(mVertexArr);
        mCameraController = new CameraController(getApplicationContext());
        mPreview = findViewById(R.id.preview_view);
        mPreview.setEGLContextClientVersion(2);
        mPreview.setRenderer(new MyRender());
        mPreview.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

    }

    private void setFullScreen() {
        Window window = getWindow();
        int viewFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        int winFlags = WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | WindowManager.LayoutParams.FLAG_FULLSCREEN;
        WindowManager.LayoutParams lp =getWindow().getAttributes();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
        window.getDecorView().setSystemUiVisibility(viewFlags);
        window.addFlags(winFlags);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPreview.onResume();
        getWindowManager().getDefaultDisplay().getRealMetrics(mDisplayMetrics);
        mWidth = mDisplayMetrics.widthPixels;
        mHeight = mDisplayMetrics.heightPixels;
        Log.i(TAG, "onResume: " + mDisplayMetrics);
    }

    class MyRender implements GLSurfaceView.Renderer {

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            IntBuffer intBuffer = IntBuffer.allocate(1);
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, intBuffer);
            mMaxTextureSize = intBuffer.get();
            Log.i(TAG, "onSurfaceCreated: max texture size="+ mMaxTextureSize);
            intBuffer.clear();
            GLES20.glGenTextures(1, intBuffer);
            mTexName = intBuffer.get();
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTexName);
            GLES20.glTexParameteri(mTexName, GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE );
            GLES20.glTexParameteri(mTexName, GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE );
            GLES20.glTexParameteri(mTexName, GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(mTexName, GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            final String fShader = Utils.getStringFromFileInAssets(getApplicationContext(), "frag_shader.glsl");
            final String vShader = Utils.getStringFromFileInAssets(getApplicationContext(), "vertex_shader.glsl");
            Log.i(TAG, "onSurfaceCreated: " + fShader + vShader);
            mProgram = Utils.loadProgram(vShader, fShader);
            a_Position = GLES20.glGetAttribLocation(mProgram, "a_Position");
            a_TexCoord = GLES20.glGetAttribLocation(mProgram, "a_TexCoord");
//            u_MVPMatrix = GLES20.glGetUniformLocation(mProgram, "u_MVPMatrix");
            u_TextureMatrix = GLES20.glGetUniformLocation(mProgram, "u_TextureMatrix");
            u_TextureUnit = GLES20.glGetUniformLocation(mProgram, "u_TextureUnit");
            mSurfaceTexture = new SurfaceTexture(mTexName);
            mSurfaceTexture.setDefaultBufferSize(mWidth, mHeight);
            mSurfaceTexture.setOnFrameAvailableListener(mPreviewDataCallback, null);
            Surface surface = new Surface(mSurfaceTexture);
            mCameraController.open(surface);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            Log.i(TAG, "onSurfaceChanged: wxh=" + width + "x" + height);
            GLES20.glViewport(0, 0, width, height);
//            Matrix.orthoM(mPMatrix, 0, 0, width, 0, height, -1, 1);
//            Matrix.setLookAtM(mVMatrix, 0, 0, 0, 0, 0, 0, -1, 0, 1, 0);
//            Matrix.multiplyMM(temp, 0, mVMatrix, 0, mModelMatrix, 0);
//            Matrix.multiplyMM(mMVPMatrix, 0, mPMatrix, 0, temp, 0);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClearColor(0, 0, 0, 1);
            mSurfaceTexture.updateTexImage();
            Matrix.setIdentityM(mTextureMatrix, /* smOffset= */ 0);
            mSurfaceTexture.getTransformMatrix(mTextureMatrix);
            GLES20.glUseProgram(mProgram);
            mVertexBuffer.position(0);
            GLES20.glVertexAttribPointer(a_Position, 2, GLES20.GL_FLOAT, false, 4 * 4, mVertexBuffer);
            mVertexBuffer.position(2);
            GLES20.glVertexAttribPointer(a_TexCoord, 2, GLES20.GL_FLOAT, false, 4 * 4, mVertexBuffer);
            GLES20.glEnableVertexAttribArray(a_Position);
            GLES20.glEnableVertexAttribArray(a_TexCoord);
//            GLES20.glUniformMatrix4fv(
//                    u_MVPMatrix, /* count= */ 1, /* transpose= */ false, mMVPMatrix, /* offset= */ 0);
            GLES20.glUniformMatrix4fv(
                    u_TextureMatrix, /* count= */ 1, /* transpose= */ false, mTextureMatrix, /* offset= */ 0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTexName);
            GLES20.glUniform1i(u_TextureUnit, /* x= */ 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, /* first= */ 0, /* offset= */ 4);
        }
    }


    private SurfaceTexture.OnFrameAvailableListener mPreviewDataCallback = new SurfaceTexture.OnFrameAvailableListener() {
        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            mPreview.requestRender();
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        mPreview.onPause();
        mCameraController.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraController.release();
    }
}
