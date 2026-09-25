package android.opengl;

import java.nio.Buffer;

public class GLES20 {
    public static final int GL_NO_ERROR = 0;
    public static final int GL_COLOR_BUFFER_BIT = 0x00004000;
    public static final int GL_TEXTURE_2D = 0x0DE1;
    public static final int GL_RGBA = 0x1908;
    public static final int GL_UNSIGNED_BYTE = 0x1401;
    public static final int GL_FLOAT = 0x1406;
    public static final int GL_TRIANGLE_STRIP = 0x0005;
    public static final int GL_TRIANGLES = 0x0004;
    public static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    public static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    public static final int GL_TEXTURE_WRAP_S = 0x2802;
    public static final int GL_TEXTURE_WRAP_T = 0x2803;
    public static final int GL_NEAREST = 0x2600;
    public static final int GL_LINEAR = 0x2601;
    public static final int GL_CLAMP_TO_EDGE = 0x812F;
    public static final int GL_TEXTURE0 = 0x84C0;
    public static final int GL_VERTEX_SHADER = 0x8B31;
    public static final int GL_FRAGMENT_SHADER = 0x8B30;
    public static final int GL_COMPILE_STATUS = 0x8B81;
    public static final int GL_LINK_STATUS = 0x8B82;

    public static void glClearColor(float red, float green, float blue, float alpha) {}
    public static void glClear(int mask) {}
    public static void glViewport(int x, int y, int width, int height) {}
    public static void glGenTextures(int n, int[] textures, int offset) {}
    public static void glBindTexture(int target, int texture) {}
    public static void glTexParameteri(int target, int pname, int param) {}
    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, Buffer pixels) {}
    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, Buffer pixels) {}
    public static void glDeleteTextures(int n, int[] textures, int offset) {}
    public static void glActiveTexture(int texture) {}
    public static int glCreateShader(int type) { return 1; }
    public static void glShaderSource(int shader, String string) {}
    public static void glCompileShader(int shader) {}
    public static void glGetShaderiv(int shader, int pname, int[] params, int offset) { params[offset] = 1; }
    public static String glGetShaderInfoLog(int shader) { return ""; }
    public static void glDeleteShader(int shader) {}
    public static int glCreateProgram() { return 1; }
    public static void glAttachShader(int program, int shader) {}
    public static void glLinkProgram(int program) {}
    public static void glGetProgramiv(int program, int pname, int[] params, int offset) { params[offset] = 1; }
    public static String glGetProgramInfoLog(int program) { return ""; }
    public static void glUseProgram(int program) {}
    public static void glDeleteProgram(int program) {}
    public static int glGetAttribLocation(int program, String name) { return 0; }
    public static int glGetUniformLocation(int program, String name) { return 0; }
    public static void glEnableVertexAttribArray(int index) {}
    public static void glDisableVertexAttribArray(int index) {}
    public static void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, Buffer ptr) {}
    public static void glUniform1i(int location, int x) {}
    public static void glDrawArrays(int mode, int first, int count) {}
    public static int glGetError() { return GL_NO_ERROR; }
}
