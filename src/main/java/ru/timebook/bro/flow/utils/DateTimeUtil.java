package ru.timebook.bro.flow.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateTimeUtil {
    private final static Logger logger = LoggerFactory.getLogger(DateTimeUtil.class);

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
}
