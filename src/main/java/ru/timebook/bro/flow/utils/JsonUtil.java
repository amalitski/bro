package ru.timebook.bro.flow.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class JsonUtil {
    public String serialize(Object model) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(model);
        } catch (JsonProcessingException e) {
            log.error("Json serialize exception", e);
        }
        return "";
    }

    public String serializePretty(Object model) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(model);
        } catch (JsonProcessingException e) {
            log.error("Json serialize exception", e);
        }
        return "";
    }

    public <T> T deserialize(String data, Class<T> classLoad) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(data, classLoad);
    }
}
