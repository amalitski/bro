package ru.timebook.bro.app.health;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

@Service
public class LivenessService {
    @Data
    @Builder
    public static class Response {
        private final int statusCode;
    }

    public Response health() {
        return Response.builder().statusCode(200).build();
    }
}
