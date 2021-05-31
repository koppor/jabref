package org.jabref.model.openoffice.notforproduction;

/*
 * Measure execution time.
 */
public class TimeLap {

    /*
     * Usage:
     * long startTime = TimeLap.start();
     * // call to measure
     * startTime = TimeLap.now("label1", startTime);
     * // call to measure
     * startTime = TimeLap.now("label2", startTime);
     */
    // return time (nanoSeconds) for timing
    public static long start() {
        return System.nanoTime();
    }

    // return time (nanoSeconds) for next timing
    public static long now(String label, long startTime) {
        long endTime = System.nanoTime();
        long duration = (endTime - startTime);  // divide by 1000000 to get milliseconds.
        System.out.printf("%-40s: %10.3f ms\n", label, duration / 1000000.0);
        return endTime;
    }
}
