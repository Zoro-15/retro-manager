package android.content.res;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class AssetManager {
    private final Map<String, byte[]> mockAssets = new HashMap<>();

    public void putMockAsset(String filename, byte[] content) {
        mockAssets.put(filename, content);
    }

    public InputStream open(String filename) throws IOException {
        byte[] bytes = mockAssets.get(filename);
        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        }
        InputStream resStream = getClass().getClassLoader().getResourceAsStream("assets/" + filename);
        if (resStream != null) {
            return resStream;
        }
        throw new IOException("Asset not found: " + filename);
    }
}
