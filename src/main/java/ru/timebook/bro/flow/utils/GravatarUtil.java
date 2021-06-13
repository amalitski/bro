package ru.timebook.bro.flow.utils;

import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

public class GravatarUtil {
    /**
     * https://ru.gravatar.com/site/implement/hash/
     */
    public static String getUri(String email, int size) {
        var hash = DigestUtils.md5DigestAsHex(email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        return String.format("https://secure.gravatar.com/avatar/%s?rating=PG&size=%s&default=wavatar", hash, size);
    }
}
