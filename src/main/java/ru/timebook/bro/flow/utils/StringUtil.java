package ru.timebook.bro.flow.utils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Random;

@Component
public class StringUtil {
    private final Random RANDOM = new SecureRandom();

    @SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE")
    public String random(int min, int max) {
        return String.valueOf(this.RANDOM.nextInt(max - min) + min);
    }

    public String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
