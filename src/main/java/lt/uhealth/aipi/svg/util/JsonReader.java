package lt.uhealth.aipi.svg.util;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import java.io.ByteArrayInputStream;

public interface JsonReader {

    Jsonb jsonb = JsonbBuilder.create();

    static <T> T readValue(byte[] json, Class<T> valueType){
        return jsonb.fromJson(new ByteArrayInputStream(json), valueType);
    }

    static <T> T readValue(String json, Class<T> valueType){
        return jsonb.fromJson(json, valueType);
    }
}
