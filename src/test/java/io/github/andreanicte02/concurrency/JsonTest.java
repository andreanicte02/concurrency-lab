package io.github.andreanicte02.concurrency;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonTest {

    record Point(int x, String label) {}

    @Test
    void writesRecordsMapsAndLists() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("points", List.of(new Point(1, "a\"b"), new Point(2, null)));
        m.put("ok", true);
        assertEquals("{\"points\":[{\"x\":1,\"label\":\"a\\\"b\"},{\"x\":2,\"label\":null}],\"ok\":true}", Json.write(m));
    }
}
