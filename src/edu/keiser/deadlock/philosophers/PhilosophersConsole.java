package edu.keiser.deadlock.philosophers;

import edu.keiser.deadlock.core.Mode;

import java.util.Arrays;

public class PhilosophersConsole {

    public static void main(String[] args) throws InterruptedException {
        Mode mode = Mode.UNSAFE;
        System.out.println("Mode " + mode + "\n");

        PhilosophersSimulation sim = new PhilosophersSimulation();
        sim.start(mode);

        long end = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < end) {
            if (sim.snapshot().deadlock()) {
                System.out.println("\n*** DEADLOCK: everyone is waiting and nobody can make progress ***");
                break;
            }
            Thread.sleep(50);
        }
        sim.stop();
        System.out.println("\nMeals per philosopher: " + Arrays.toString(sim.snapshot().meals()));
    }
}