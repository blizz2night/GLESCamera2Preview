package com.github.blizz2inght.gridfilter;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Utils {
    private static final String TAG = "GLUtils";

    public static String getStringFromFileInAssets(Context context, String filename){
        StringBuilder builder = new StringBuilder();
        try (InputStream ins = context.getAssets().open(filename);
             InputStreamReader insReader = new InputStreamReader(ins);
             BufferedReader reader = new BufferedReader(insReader)) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
        } catch (IOException e) {
            Log.e(TAG, "getStringFromFileInAssets: ", e);
        }
        return builder.toString();
    }

    public static int loadShader(String strSource, int iType) {
        int[] compiled = new int[1];
        int iShader = GLES20.glCreateShader(iType);
        GLES20.glShaderSource(iShader, strSource);
        GLES20.glCompileShader(iShader);
        GLES20.glGetShaderiv(iShader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.d(TAG,
                    "Compilation\n" + GLES20.glGetShaderInfoLog(iShader));
            return 0;
        }
        return iShader;
    }

    public static int loadProgram(String strVSource, String strFSource) {
        int[] link = new int[1];
        int iVShader = loadShader(strVSource, GLES20.GL_VERTEX_SHADER);
        if (iVShader == 0) {
            Log.d(TAG, "Vertex Shader Failed");
            return 0;
        }
        int iFShader = loadShader(strFSource,  GLES20.GL_FRAGMENT_SHADER);
        if (iFShader == 0) {
            Log.d(TAG, "Fragment Shader Failed");
            return 0;
        }

        int iProgId = GLES20.glCreateProgram();

        GLES20.glAttachShader(iProgId, iVShader);
        GLES20.glAttachShader(iProgId, iFShader);

        GLES20.glLinkProgram(iProgId);

        GLES20.glGetProgramiv(iProgId, GLES20.GL_LINK_STATUS, link, 0);
        if (link[0] <= 0) {
            Log.d(TAG, "Linking Failed");
            return 0;
        }
        GLES20.glDeleteShader(iVShader);
        GLES20.glDeleteShader(iFShader);
        return iProgId;
    }

//    public void generateBitmap(View view) {
//        Bitmap bitmap = Bitmap.createBitmap(16, 256, Bitmap.Config.ARGB_8888);
//        final int[] pixels = new int[16 * 256];
//        for (int b = 0; b < 16; b++) {
//            for (int g = 0; g < 16; g++) {
//                for (int r = 0; r < 16; r++) {
//                    pixels[r + 16 * g + 256 * b] = 0xFF000000 | (r * 16 << 16) | (g * 16 << 8) | b * 16;
//                }
//             }
//        }
//        bitmap.setPixels(pixels,0,16,0,0,16,256);
//        final File dir = getExternalMediaDirs()[0];
//        final File file = new File(dir, "lut.png");
//        try {
//            file.createNewFile();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        try (FileOutputStream stream = new FileOutputStream(file)){
//            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
}
