package com.test.overlay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class MainActivity extends Activity {
    private GLSurfaceView glView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 1000);
        } else {
            createOverlay();
        }
    }

     private void createOverlay() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // 终极防弹参数：透明、不获取焦点、不阻挡触摸、全屏
        int layoutFlag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE 
                       | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE 
                       | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                       | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                layoutFlag,
                PixelFormat.TRANSLUCENT);

        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(3);
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        glView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        glView.setRenderer(new FieldRenderer());
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        wm.addView(glView, params);
        finish();
    }

class FieldRenderer implements GLSurfaceView.Renderer {
    private int program;
    private FloatBuffer vertexBuffer;
    private long startTime;
    private final String vertexShaderCode = "attribute vec4 vPosition; void main() { gl_Position = vPosition; }";
    private final String fragmentShaderCode =
        "#version 300 es\nprecision highp float;\nout vec4 fragColor;\nuniform float u_time;\n" +
        "void main() {\n" +
        "  vec2 uv = gl_FragCoord.xy / vec2(1920.0, 1080.0);\n" +
        "  float r = length(uv - 0.5);\n" +
        "  float bosonic = sin(uv.x * 20.0 + u_time) * cos(uv.y * 20.0 + u_time);\n" +
        "  float fermionic = sin(uv.x * 20.0 + u_time + 1.57) * cos(uv.y * 20.0 + u_time + 1.57);\n" +
        "  float herm = abs((bosonic + fermionic) * (bosonic - fermionic));\n" +
        "  float super_sym = 1.0 / (herm + 0.001);\n" +
        "  float flux = 1.0 / (r * r + 0.01);\n" +
        "  float intensity = flux * 0.0001 * super_sym;\n" +
        "  vec3 color = vec3(1.0, 0.4, 0.1) * intensity * 0.7606;\n" +
        "  fragColor = vec4(color, clamp(intensity, 0.0, 1.0));\n" +
        "}";

    public FieldRenderer() {
        float[] quad = { -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f };
        vertexBuffer = ByteBuffer.allocateDirect(quad.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(quad).position(0);
        startTime = System.currentTimeMillis();
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, shaderCode);
        GLES30.glCompileShader(shader);
        return shader;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        int vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderCode);
        program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, vertexShader);
        GLES30.glAttachShader(program, fragmentShader);
        GLES30.glLinkProgram(program);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE);
    }

    @Override public void onSurfaceChanged(GL10 gl, int width, int height) { GLES30.glViewport(0, 0, width, height); }
    @Override public void onDrawFrame(GL10 gl) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        GLES30.glUseProgram(program);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "u_time"), (System.currentTimeMillis() - startTime) / 1000.0f);
        int positionHandle = GLES30.glGetAttribLocation(program, "vPosition");
        GLES30.glEnableVertexAttribArray(positionHandle);
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glDisableVertexAttribArray(positionHandle);
    }
}
