package ru.timebook.bro.flow.utils;

import lombok.extern.slf4j.Slf4j;

import java.text.NumberFormat;

@Slf4j
public class InfoUtil {
    private InfoUtil() {
    }

    public static void showUsage() {
        Runtime runtime = Runtime.getRuntime();
        final NumberFormat format = NumberFormat.getInstance();
        final long maxMemory = runtime.maxMemory();
        final long allocatedMemory = runtime.totalMemory();
        final long freeMemory = runtime.freeMemory();
        final long availableProcessors = runtime.availableProcessors();
        final long mb = 1024L * 1024L;
        final String mega = " MB";
        final String javaOpts = System.getenv("JAVA_OPTS");

        final var freeMemData = format.format(freeMemory / mb) + mega;
        final var allocatedMemData = format.format(allocatedMemory / mb) + mega;
        final var maxMemData = format.format(maxMemory / mb) + mega;
        final var finalMemData = format.format((freeMemory + (maxMemory - allocatedMemory)) / mb) + mega;
        final var procData = format.format(availableProcessors);

        log.info("========================== Info ==========================");
        log.info("Free memory: {}", freeMemData);
        log.info("Allocated memory: {}", allocatedMemData);
        log.info("Max memory: {}", maxMemData);
        log.info("Total free memory: {}", finalMemData);
        log.info("Available processors: {}", procData);
        log.info("JAVA_OPTS: {}", javaOpts);
        log.info("=================================================================");
    }


    public static void showConfig(Object config) {
        var conf = JsonUtil.serialize(config);
        log.info("========================== Configuration ==========================");
        log.info("Configuration: {} ", conf);
        log.info("=================================================================");
    }
}
