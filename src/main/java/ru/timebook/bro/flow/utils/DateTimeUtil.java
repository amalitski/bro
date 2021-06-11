package ru.timebook.bro.flow.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateTimeUtil {
    /**
     * https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html
     */
    public static String getDate(String pattern) {
        var time = LocalDateTime.now();
        return time.format(DateTimeFormatter.ofPattern(pattern));
    }

    public static LocalDate toLocalDate(Date date) {
        LocalDateTime conv = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        return conv.toLocalDate();
    }
    public static String formatFull(LocalDateTime date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:ss");
        return date.format(formatter);
    }
}
