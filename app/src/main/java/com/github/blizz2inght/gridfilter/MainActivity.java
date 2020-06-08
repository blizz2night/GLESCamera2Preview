package com.github.blizz2inght.gridfilter;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
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

import static com.github.blizz2inght.gridfilter.Utils.*;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private String[] mPerms;

    private GLSurfaceView mPreview;
    private SurfaceTexture mSurfaceTexture;
    private CameraController mCameraController;

    private Surface mSurface;
    private int mPreviewTex;
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

    private int mPreviewProgram;
    private int a_Position;
    private int a_TexCoord;
//    private int u_MVPMatrix;
    private float[] mTextureMatrix = new float[16];
    private FloatBuffer mVertexBuffer;
    private int u_TextureUnit;
    private int u_TextureMatrix;
    private int mSW;
    private int mSH;
    private int mFilterTex;
    private int mFBO;
    private int mFilterProgram;
    private int a_FilterPosition;
    private int a_FilterTexCoord;
    private int u_FilterTextureUnit;
    private int u_FilterLookupTable;
    private Size mPreviewSize;
    private Bitmap mLutsBitmap;
    private int mLutsTex;
    private boolean mShowNinePatch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreen();
        mPerms = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermission();
        }
        init();
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkPermission() {
        for (String perm : mPerms) {
            final int ret = checkSelfPermission(perm);
            if (ret != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(mPerms, 201);
                break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 201) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "onRequestPermissionsResult: not granted" + permissions[i]);
                }
            }
        }
    }

    private void init() {
        setContentView(R.layout.activity_main);
        mCameraController = new CameraController(getApplicationContext());
        mVertexBuffer = ByteBuffer.allocateDirect(mVertexArr.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertexBuffer.position(0);
        mVertexBuffer.put(mVertexArr);
        mPreview = findViewById(R.id.preview_view);
        mPreview.setEGLContextClientVersion(2);
        mPreview.setRenderer(new MyRender());
        mPreview.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        getWindowManager().getDefaultDisplay().getRealMetrics(mDisplayMetrics);
        mSW = mDisplayMetrics.widthPixels;
        mSH = mDisplayMetrics.heightPixels;
        mPreviewSize = mCameraController.filterPreviewSize(mSW,mSH);
        mLutsBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.luts);
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

        Log.i(TAG, "onResume: " + mDisplayMetrics);
    }

    public void generateBitmap(View view) {
        final Bitmap square = Utils.generateSquareLutBitmap();
        final Bitmap column = generateColumnLut();
        Utils.writeToDisk(this,square,"square.png");
        Utils.writeToDisk(this,column,"column.png");
    }

    public void showNinePatch(View view) {
        mShowNinePatch = !mShowNinePatch;
    }

    class MyRender implements GLSurfaceView.Renderer {

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            IntBuffer intBuffer = IntBuffer.allocate(1);
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, intBuffer);
            mMaxTextureSize = intBuffer.get();
            Log.i(TAG, "onSurfaceCreated: max texture size="+ mMaxTextureSize);
            intBuffer.clear();
            int[] textures = new int[3];
            //生成三个纹理，分别用于接收预览，离屏渲染，存储lutsbitmap
            GLES20.glGenTextures(textures.length, textures, 0);
            //外部纹理mPreviewTex用于接收预览
            mPreviewTex = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mPreviewTex);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE );
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE );
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);

            //2D纹理mFilterTex用于做离屏渲染
            mFilterTex = textures[1];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFilterTex);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mPreviewSize.getHeight(), mPreviewSize.getWidth(), 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE );
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE );
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);

            //2D纹理存储滤镜lut
            mLutsTex = textures[2];
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mLutsTex);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mLutsBitmap, 0);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE );
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE );
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);

            int[] fbo = new int[1];
            //创建FBO，mFilterTex纹理绑定到FBO做滤镜离屏渲染,在这个例子中其实没有必要
            GLES20.glGenFramebuffers(1, fbo, 0);
            mFBO = fbo[0];
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFBO);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, mFilterTex, 0);
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "onSurfaceCreated: glCheckFramebufferStatus failed ");
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

            final String previewFragShader = getStringFromFileInAssets(getApplicationContext(), "frag_shader.glsl");
            final String vertexShader = getStringFromFileInAssets(getApplicationContext(), "vertex_shader.glsl");

            final String flilterFragShader = getStringFromFileInAssets(getApplicationContext(), "filter_fsh.glsl");
            final String flilterVertexShader = getStringFromFileInAssets(getApplicationContext(), "filter_vsh.glsl");

            mPreviewProgram = loadProgram(vertexShader, previewFragShader);
            mFilterProgram =  loadProgram(flilterVertexShader, flilterFragShader);

            a_Position = GLES20.glGetAttribLocation(mPreviewProgram, "a_Position");
            a_TexCoord = GLES20.glGetAttribLocation(mPreviewProgram, "a_TexCoord");
//            u_MVPMatrix = GLES20.glGetUniformLocation(mProgram, "u_MVPMatrix");
            u_TextureMatrix = GLES20.glGetUniformLocation(mPreviewProgram, "u_TextureMatrix");
            u_TextureUnit = GLES20.glGetUniformLocation(mPreviewProgram, "u_TextureUnit");


            a_FilterPosition = GLES20.glGetAttribLocation(mFilterProgram, "a_FilterPosition");
            a_FilterTexCoord = GLES20.glGetAttribLocation(mFilterProgram, "a_FilterTexCoord");
            u_FilterTextureUnit = GLES20.glGetUniformLocation(mFilterProgram, "u_FilterTextureUnit");
            u_FilterLookupTable = GLES20.glGetUniformLocation(mFilterProgram, "u_FilterLookupTable");

            mSurfaceTexture = new SurfaceTexture(mPreviewTex);
            mSurfaceTexture.setDefaultBufferSize(mPreview.getHeight(), mPreview.getWidth());
            mSurfaceTexture.setOnFrameAvailableListener(mPreviewDataCallback, null);
            Surface surface = new Surface(mSurfaceTexture);
            mCameraController.open(surface);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            Log.i(TAG, "onSurfaceChanged: wxh=" + width + "x" + height);

            final int x = (width - mPreviewSize.getHeight()) / 2;
            final int y = (height - mPreviewSize.getWidth()) / 2;
            GLES20.glViewport(x, y, mPreviewSize.getHeight(), mPreviewSize.getWidth());
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
            //绘预览->filterTex(FBO)
            GLES20.glUseProgram(mPreviewProgram);
            mVertexBuffer.position(0);
            GLES20.glVertexAttribPointer(a_Position, 2, GLES20.GL_FLOAT, false, 4 * 4, mVertexBuffer);
            GLES20.glEnableVertexAttribArray(a_Position);

            mVertexBuffer.position(2);
            GLES20.glVertexAttribPointer(a_TexCoord, 2, GLES20.GL_FLOAT, false, 4 * 4, mVertexBuffer);
            GLES20.glEnableVertexAttribArray(a_TexCoord);
//            GLES20.glUniformMatrix4fv(
//                    u_MVPMatrix, /* count= */ 1, /* transpose= */ false, mMVPMatrix, /* offset= */ 0);
            GLES20.glUniformMatrix4fv(
                    u_TextureMatrix, /* count= */ 1, /* transpose= */ false, mTextureMatrix, /* offset= */ 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mPreviewTex);
            GLES20.glUniform1i(u_TextureUnit, /* x= */ 0);

            if (mShowNinePatch) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFBO);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, /* first= */ 0, /* offset= */ 4);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
                GLES20.glDisableVertexAttribArray(a_Position);
                GLES20.glDisableVertexAttribArray(a_TexCoord);

                GLES20.glUseProgram(mFilterProgram);
                mVertexBuffer.position(0);
                GLES20.glVertexAttribPointer(a_FilterPosition, 2, GLES20.GL_FLOAT, false, 4 * 4, mVertexBuffer);
                GLES20.glEnableVertexAttribArray(a_FilterPosition);

                mVertexBuffer.position(2);
                GLES20.glVertexAttribPointer(a_FilterTexCoord, 2, GLES20.GL_FLOAT, false, 4 * 4, mVertexBuffer);
                GLES20.glEnableVertexAttribArray(a_FilterTexCoord);

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFilterTex);
                GLES20.glUniform1i(u_FilterTextureUnit, 0);

                GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mLutsTex);
                GLES20.glUniform1i(u_FilterLookupTable, 1);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, /* first= */ 0, /* offset= */ 4);
            } else {
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, /* first= */ 0, /* offset= */ 4);
            }



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
