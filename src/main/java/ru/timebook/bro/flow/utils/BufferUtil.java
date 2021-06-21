package ru.timebook.bro.flow.utils;

import java.util.HashMap;

public class BufferUtil {
    private static final HashMap<String, String> map = new HashMap<String, String>();

    public interface StringValue {
        public String call();
    }

    public static void flush() {
        map.clear();
    }

    public static String key(String key, StringValue call) {
        if (map.containsKey(key)) {
            return map.get(key);
        }
        var value = call.call();
        map.put(key, value);
        return value;
    }
}
