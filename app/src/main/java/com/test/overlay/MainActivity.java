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
        "#version 300 es\n" +
        "precision highp float;\n" +
        "out vec4 fragColor;\n" +
        "uniform float u_time;\n" +
        "uniform vec2 u_resolution;\n" +
        "void main() {\n" +
        "  // 归一化坐标与屏幕中心\n" +
        "  vec2 uv = gl_FragCoord.xy / u_resolution;\n" +
        "  vec2 center = vec2(0.5, 0.5);\n" +
        "  vec2 delta = uv - center;\n" +
        "  float dist = length(delta);\n" +
        "  \n" +
        "  // 1. 动态体积光 Raymarching（步进采样）\n" +
        "  vec3 volColor = vec3(0.0);\n" +
        "  float density = 0.0;\n" +
        "  int numSteps = 32;\n" +
        "  float stepSize = 1.0 / float(numSteps);\n" +
        "  \n" +
        "  for(int i = 0; i < numSteps; i++) {\n" +
        "    float t = float(i) * stepSize;\n" +
        "    // 从当前像素向中心反向步进\n" +
        "    vec2 p = uv - delta * t * 2.0;\n" +
        "    \n" +
        "    // 2. 超弦干涉场与偏振驻波 (产生动态光斑)\n" +
        "    float bosonic = sin(p.x * 20.0 + u_time) * cos(p.y * 20.0 + u_time);\n" +
        "    float fermionic = sin(p.x * 20.0 + u_time + 1.57) * cos(p.y * 20.0 + u_time + 1.57);\n" +
        "    float interference = abs((bosonic + fermionic) * (bosonic - fermionic));\n" +
        "    \n" +
        "    // 3. 体积光衰减系数（Mie 散射近似）\n" +
        "    float mieScatter = 1.0 / (1.0 + dist * dist * 50.0);\n" +
        "    \n" +
        "    // 叠加能量\n" +
        "    density += exp(-interference * 2.0) * mieScatter * stepSize;\n" +
        "  }\n" +
        "  \n" +
        "  // 4. 亿万量纲极化调色 (红/蓝/紫 激光感)\n" +
        "  vec3 color1 = vec3(0.8, 0.2, 1.0); // 紫色高能\n" +
        "  vec3 color2 = vec3(0.2, 0.8, 1.0); // 蓝色康普顿\n" +
        "  vec3 finalColor = mix(color1, color2, sin(u_time * 0.5) * 0.5 + 0.5) * density * 5.0;\n" +
        "  \n" +
        "  // 5. 输出 (Alpha 基于光强，实现穿透叠加)\n" +
        "  fragColor = vec4(finalColor, clamp(density * 2.0, 0.0, 1.0));\n" +
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

    @Override 
    public void onSurfaceChanged(GL10 gl, int width, int height) { 
        GLES30.glViewport(0, 0, width, height); 
        // 把屏幕分辨率传给 shader
        GLES30.glUseProgram(program);
        int resLoc = GLES30.glGetUniformLocation(program, "u_resolution");
        GLES30.glUniform2f(resLoc, (float)width, (float)height);
    }
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
