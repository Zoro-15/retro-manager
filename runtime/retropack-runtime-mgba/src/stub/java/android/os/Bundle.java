package android.os;

import java.util.HashMap;
import java.util.Map;

public class Bundle {
    private final Map<String, Object> map = new HashMap<>();

    public void putString(String key, String value) { map.put(key, value); }
    public String getString(String key) { return (String) map.get(key); }
    public void putInt(String key, int value) { map.put(key, value); }
    public int getInt(String key, int defaultValue) {
        Object val = map.get(key);
        return val instanceof Integer ? (Integer) val : defaultValue;
    }
}
