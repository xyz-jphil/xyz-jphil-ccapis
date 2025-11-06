package xyz.jphil.ccapis.util;

/**
 * Console output utility
 * Provides convenient methods for printing multiple lines
 */
public class Console {

    /**
     * Print multiple lines to console
     * Each string argument is printed on a new line
     *
     * @param lines the lines to print
     */
    public static void printLines(String... lines) {
        for (var line : lines) {
            System.out.println(line);
        }
    }

    /**
     * Print multiple lines with a blank line before
     *
     * @param lines the lines to print
     */
    public static void printLinesWithGap(String... lines) {
        System.out.println();
        printLines(lines);
    }

    /**
     * Print multiple lines with a blank line after
     *
     * @param lines the lines to print
     */
    public static void printLinesWithGapAfter(String... lines) {
        printLines(lines);
        System.out.println();
    }

    /**
     * Print multiple lines with blank lines before and after
     *
     * @param lines the lines to print
     */
    public static void printBlock(String... lines) {
        System.out.println();
        printLines(lines);
        System.out.println();
    }
}
