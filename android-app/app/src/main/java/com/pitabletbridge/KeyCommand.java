package com.pitabletbridge;

import org.json.JSONException;
import org.json.JSONObject;

public final class KeyCommand {
    public final String kind;
    public final String text;
    public final String key;
    public final int count;

    private KeyCommand(String kind, String text, String key, int count) {
        this.kind = kind;
        this.text = text;
        this.key = key;
        this.count = count;
    }

    public static KeyCommand text(String text) {
        return new KeyCommand("text", text, null, 0);
    }

    public static KeyCommand special(String key) {
        return new KeyCommand("special", null, key, 0);
    }

    public static KeyCommand backspace(int count) {
        return new KeyCommand("backspace", null, null, count);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("kind", kind);
        if (text != null) {
            object.put("text", text);
        }
        if (key != null) {
            object.put("key", key);
        }
        if (count > 0) {
            object.put("count", count);
        }
        return object;
    }
}

