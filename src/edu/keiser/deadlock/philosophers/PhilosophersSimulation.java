package edu.keiser.deadlock.philosophers;

import edu.keiser.deadlock.core.Mode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class PhilosophersSimulation {

    public static final int N = 5;
    public static final int FREE = -1;

    private static final int MAX_EVENTS = 50;

    private final ReentrantLock[] forks = new ReentrantLock[N];

    private final PhilosopherState[] states = new PhilosopherState[N];
    private final int[] forkOwner = new int[N];
    private final int[] waitingFor = new int[N];
    private final int[] meals = new int[N];
    private final Deque<String> events = new ArrayDeque<>();

    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running;
    private volatile boolean started;
    private volatile boolean paused;
    private volatile long delayMs = 600;
    private volatile Mode mode = Mode.UNSAFE;
    private long startTime;

    public PhilosophersSimulation() {
        for (int f = 0; f < N; f++) {
            forks[f] = new ReentrantLock();
        }
    }

    public void start(Mode mode) {
        stop();

        this.mode = mode;
        paused = false;
        synchronized (this) {
            Arrays.fill(states, PhilosopherState.THINKING);
            Arrays.fill(forkOwner, FREE);
            Arrays.fill(waitingFor, FREE);
            Arrays.fill(meals, 0);
            events.clear();
        }

        startTime = System.currentTimeMillis();
        running = true;
        started = true;

        for (int i = 0; i < N; i++) {
            final int id = i;
            Thread t = new Thread(() -> philosopher(id), "Philosopher-" + (i + 1));
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
    }


    public void stop() {
        running = false;
        for (Thread t : threads) {
            t.interrupt();
        }
        for (Thread t : threads) {
            try {
                t.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        threads.clear();
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public boolean isStarted() {
        return started;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(mode, states.clone(), forkOwner.clone(), waitingFor.clone(),
                meals.clone(), List.copyOf(events));
    }


    private void philosopher(int i) {
        String me = "P" + (i + 1);

        int left = i;
        int right = (i + 1) % N;

        int first = left;
        int second = right;

        if (mode == Mode.SAFE) {
            first = Math.min(left, right);
            second = Math.max(left, right);
        }
        String orderNote = (mode == Mode.SAFE && first != left) ? " (lower number first)" : "";

        try {
            while (running) {
                // 1. Think
                setState(i, PhilosopherState.THINKING);
                if (mode == Mode.UNSAFE) {
                    pause(0.5 + 0.5 * i);
                } else {
                    pause(1 + 2 * ThreadLocalRandom.current().nextDouble());
                }

                // 2. Get hungry and take the first fork
                setState(i, PhilosopherState.HUNGRY);
                if (!take(i, first)) {
                    return;
                }
                log(me + " is hungry and picks up " + fork(first) + " " + side(i, first) + orderNote);

                try {
                    // 3. Pause before the second fork. In UNSAFE it must be longer than
                    pause(mode == Mode.UNSAFE ? 3 : 0.5);

                    log(me + " reaches for " + fork(second) + " " + side(i, second));
                    if (!take(i, second)) {
                        return;
                    }

                    try {
                        // 4. Eat: this is the critical section
                        setState(i, PhilosopherState.EATING);
                        synchronized (this) {
                            meals[i]++;
                        }
                        log(me + " has " + fork(first) + " and " + fork(second)
                                + " and eats (critical section)");
                        pause(2);
                        log(me + " puts down " + fork(first) + " and " + fork(second)
                                + " and goes back to thinking");
                    } finally {
                        release(second);
                    }
                } finally {
                    release(first);
                }
            }
        } catch (InterruptedException e) {
            // stop() interrupted us: just finish. The finally blocks already released the forks.
        }
    }


    private boolean take(int i, int fork) throws InterruptedException {
        synchronized (this) {
            waitingFor[i] = fork;
        }
        boolean announced = false;
        try {
            while (running) {
                waitWhilePaused();
                if (forks[fork].tryLock(100, TimeUnit.MILLISECONDS)) {
                    synchronized (this) {
                        forkOwner[fork] = i;
                    }
                    return true;
                }
                if (!announced) {
                    int owner;
                    synchronized (this) {
                        owner = forkOwner[fork];
                    }
                    if (owner != FREE) {
                        log("P" + (i + 1) + " must wait: " + fork(fork) + " is held by P" + (owner + 1));
                    }
                    announced = true;
                }
            }
            return false;
        } finally {
            synchronized (this) {
                waitingFor[i] = FREE;
            }
        }
    }

    private void release(int fork) {
        synchronized (this) {
            forkOwner[fork] = FREE;
        }
        forks[fork].unlock();
    }

    private synchronized void setState(int i, PhilosopherState state) {
        states[i] = state;
    }

    private void pause(double factor) throws InterruptedException {
        waitWhilePaused();
        Thread.sleep((long) (delayMs * factor));
        waitWhilePaused();
    }

    private void waitWhilePaused() throws InterruptedException {
        while (paused) {
            Thread.sleep(50);
        }
    }

    private void log(String message) {
        double seconds = (System.currentTimeMillis() - startTime) / 1000.0;
        String line = String.format(Locale.US, "%5.1f s  %s", seconds, message);
        System.out.println(line);
        synchronized (this) {
            events.addFirst(message);
            if (events.size() > MAX_EVENTS) {
                events.removeLast();
            }
        }
    }

    private static String fork(int f) {
        return "F" + (f + 1);
    }

    private static String side(int philosopher, int fork) {
        return fork == philosopher ? "(left)" : "(right)";
    }


    public record Snapshot(Mode mode,
                           PhilosopherState[] states,
                           int[] forkOwner,
                           int[] waitingFor,
                           int[] meals,
                           List<String> events) {

        public int forksHeldBy(int i) {
            int held = 0;
            for (int owner : forkOwner) {
                if (owner == i) {
                    held++;
                }
            }
            return held;
        }

        public int freeForks() {
            int free = 0;
            for (int owner : forkOwner) {
                if (owner == FREE) {
                    free++;
                }
            }
            return free;
        }

        public int blockedBy(int i) {
            int f = waitingFor[i];
            if (f == FREE) {
                return FREE;
            }
            int owner = forkOwner[f];
            return owner == i ? FREE : owner;
        }

        public boolean holdAndWait() {
            for (int i = 0; i < N; i++) {
                if (forksHeldBy(i) == 1 && blockedBy(i) != FREE) {
                    return true;
                }
            }
            return false;
        }

        public boolean deadlock() {
            for (int i = 0; i < N; i++) {
                if (states[i] != PhilosopherState.HUNGRY
                        || forksHeldBy(i) != 1
                        || blockedBy(i) == FREE) {
                    return false;
                }
            }
            return true;
        }
    }
}
