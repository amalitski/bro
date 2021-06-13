package ru.timebook.bro.flow.utils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.security.SecureRandom;
import java.util.Random;

public class StringUtil {
    private static final Random RANDOM = new SecureRandom();

    @SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE")
    public static String random(int min, int max) {
        return String.valueOf(RANDOM.nextInt(max - min) + min);
    }

    public static String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
