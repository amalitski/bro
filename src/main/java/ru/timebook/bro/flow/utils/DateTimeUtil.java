package ru.timebook.bro.flow.utils;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Component
public class DateTimeUtil {
    /**
     * https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html
     */
    public String getDate(String pattern) {
        var time = LocalDateTime.now();
        return time.format(DateTimeFormatter.ofPattern(pattern));
    }

    public LocalDate toLocalDate(Date date) {
        LocalDateTime conv = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        return conv.toLocalDate();
    }

    public String formatFull(LocalDateTime date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        return date.format(formatter);
    }

    public LocalDateTime duration(String duration) {
        return LocalDateTime.now().plusNanos(Duration.parse(duration).toNanos());
    }
}
