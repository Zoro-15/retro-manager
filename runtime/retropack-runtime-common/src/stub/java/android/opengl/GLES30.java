package android.opengl;

import java.nio.Buffer;

/**
 * JVM test stub mirroring the GLES20 stub: all calls are no-ops so the
 * renderer's PBO fast paths (issue #46) can be exercised under JUnit.
 * glMapBufferRange returns null, which drives the renderer into its
 * direct-upload fallback, keeping lifecycle tests behaviour-neutral.
 */
public class GLES30 {
    public static final int GL_STREAM_DRAW = 0x88E0;
    public static final int GL_PIXEL_UNPACK_BUFFER = 0x88EC;
    public static final int GL_PIXEL_UNPACK_BUFFER_BINDING = 0x88EF;
    public static final int GL_MAP_READ_BIT = 0x0001;
    public static final int GL_MAP_WRITE_BIT = 0x0002;
    public static final int GL_MAP_INVALIDATE_RANGE_BIT = 0x0004;
    public static final int GL_MAP_INVALIDATE_BUFFER_BIT = 0x0008;
    public static final int GL_MAP_UNSYNCHRONIZED_BIT = 0x0020;

    public static void glGenBuffers(int n, int[] buffers, int offset) {}
    public static void glDeleteBuffers(int n, int[] buffers, int offset) {}
    public static void glBindBuffer(int target, int buffer) {}
    public static void glBufferData(int target, int size, Buffer data, int usage) {}
    public static void glBufferSubData(int target, int offset, int size, Buffer data) {}
    public static Buffer glMapBufferRange(int target, int offset, int length, int access) {
        return null;
    }
    public static boolean glUnmapBuffer(int target) {
        return true;
    }
    public static int glGetError() {
        return GLES20.GL_NO_ERROR;
    }
}
