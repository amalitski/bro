package ru.timebook.bro.flow.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Slf4j
public class JsonUtil {
    public static String serialize(Object model) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(model);
        } catch (JsonProcessingException e) {
            log.error("Json serialize exception", e);
        }
        return "";
    }

    public static String serializePretty(Object model) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(model);
        } catch (JsonProcessingException e) {
            log.error("Json serialize exception", e);
        }
        return "";
    }

    public static <T> T deserialize(String data, Class<T> classLoad) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(data, classLoad);
    }
}
