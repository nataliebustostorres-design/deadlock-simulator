
package edu.keiser.deadlock.ui;

import edu.keiser.deadlock.banker.BankersAlgorithm;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BankersLiveScreen extends JPanel {

    private static final Color ACCENT = new Color(0x185FA5);
    private static final Color AMBER = new Color(0xB77916);
    private static final Color GREEN = new Color(0x3B6D11);

    // Button colours (solid fill with white text).
    private static final Color SLATE = new Color(0x64748B);
    private static final Color PLAY_GREEN = new Color(0x2F7D32);
    private static final Color PAUSE_AMBER = new Color(0xD97706);
    private static final Color RESTART_RED = new Color(0xC0392B);
    private static final Color LOAD_BLUE = new Color(0x103A6B);
    private static final Color DISABLED_TEXT = new Color(0x9AA3AD);

    private static final int STEP_DELAY_MS = 1600;

    // PARKING VISUALIZATION

    private final BankersParkingPanel parkingLot =
            new BankersParkingPanel();

    // HEADER

    private final JButton loadDemoButton =
            new JButton("Load Demo");

    private final JProgressBar progress =
            new JProgressBar();

    private final JLabel progressText =
            new JLabel("Not started");

    // EXPLANATION

    private final JLabel narration = new JLabel(
            "<html>Load a demo to begin the simulation.</html>"
    );

    // PLAYBACK CONTROLS

    private final JButton previousButton =
            new JButton("Previous");

    private final JButton playButton =
            new JButton("Play");

    private final JButton pauseButton =
            new JButton("Pause");

    private final JButton nextButton =
            new JButton("Next Step");

    private final JButton restartButton =
            new JButton("Restart");

    private Timer timer;

    private boolean demoLoaded;

    // BANKER'S ALGORITHM DATA

    private int[][] allocationData;
    private int[][] needData;
    private int[] availableData;
    private int[] sequence;

    private final List<TraceEvent> trace =
            new ArrayList<>();

    private boolean traceSafe;

    // -1 means simulation has not started.
    // Otherwise, this is the current event index.

    private int step = -1;

    private BankersGraphPanel.View currentView =
            BankersGraphPanel.View.empty();


    // =========================================================
    // TRACE EVENT
    // =========================================================

    private static class TraceEvent {

        final String type;
        final int process;

        final int[] workBefore;
        final int[] need;
        final int[] allocation;
        final int[] workAfter;

        final boolean canFinish;

        TraceEvent(
                String type,
                int process,
                int[] workBefore,
                int[] need,
                int[] allocation,
                int[] workAfter,
                boolean canFinish
        ) {

            this.type = type;
            this.process = process;

            this.workBefore = workBefore.clone();
            this.need = need.clone();
            this.allocation = allocation.clone();
            this.workAfter = workAfter.clone();

            this.canFinish = canFinish;
        }
    }


    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public BankersLiveScreen() {

        super(new BorderLayout(0, 10));

        setBackground(Theme.BACKGROUND);

        setBorder(
                new EmptyBorder(12, 16, 12, 16)
        );

        add(buildHeader(), BorderLayout.NORTH);
        add(buildGraphArea(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        // Load Demo

        loadDemoButton.addActionListener(e -> {

            stopTimer();
            loadDemo();
        });

        // Playback actions

        playButton.addActionListener(e -> play());

        pauseButton.addActionListener(e -> stopTimer());

        nextButton.addActionListener(e -> {

            stopTimer();
            advance();
        });

        previousButton.addActionListener(e -> {

            stopTimer();
            previous();
        });

        restartButton.addActionListener(e -> {

            stopTimer();
            restart();
        });

        setControlsEnabled(false);
    }


    // =========================================================
    // HEADER
    // =========================================================

    private JComponent buildHeader() {

        JPanel header = new JPanel(
                new BorderLayout(0, 9)
        );

        header.setOpaque(false);

        // TITLE AND CONTROLS

        JPanel top = new JPanel(
                new BorderLayout(10, 0)
        );

        top.setOpaque(false);

        JLabel title = new JLabel("Watch it Live");

        title.setFont(
                Theme.font(Font.BOLD, 19f)
        );

        title.setForeground(Theme.TEXT);

        // Playback controls

        JPanel actions = new JPanel(
                new FlowLayout(
                        FlowLayout.RIGHT,
                        5,
                        0
                )
        );

        actions.setOpaque(false);

        styleButton(previousButton, SLATE);
        styleButton(playButton, PLAY_GREEN);
        styleButton(pauseButton, PAUSE_AMBER);
        styleButton(nextButton, ACCENT);
        styleButton(restartButton, RESTART_RED);
        styleButton(loadDemoButton, LOAD_BLUE);

        previousButton.setToolTipText(
                "Go to the previous simulation event"
        );

        playButton.setToolTipText(
                "Automatically play the simulation"
        );

        pauseButton.setToolTipText(
                "Pause the simulation"
        );

        nextButton.setToolTipText(
                "Advance one simulation event"
        );

        restartButton.setToolTipText(
                "Return to the beginning of the simulation"
        );

        actions.add(previousButton);
        actions.add(playButton);
        actions.add(pauseButton);
        actions.add(nextButton);
        actions.add(restartButton);
        actions.add(loadDemoButton);

        top.add(title, BorderLayout.WEST);
        top.add(actions, BorderLayout.EAST);

        header.add(top, BorderLayout.NORTH);

        // RESOURCE LEGEND

        JPanel information = new JPanel(
                new BorderLayout(0, 6)
        );

        information.setOpaque(false);

        information.add(
                buildResourceLegend(),
                BorderLayout.NORTH
        );

        header.add(
                information,
                BorderLayout.CENTER
        );

        return header;
    }


    // =========================================================
    // RESOURCE LEGEND
    // =========================================================

    private JComponent buildResourceLegend() {

        JPanel legend = roundedCard(
                new Color(0xA6C2F4),
                9
        );

        legend.setPreferredSize(
                new Dimension(220, 55)
        );

        legend.setMinimumSize(
                new Dimension(220, 55)
        );

        legend.setMaximumSize(
                new Dimension(220, 55)
        );

        legend.setBorder(
                new EmptyBorder(5, 8, 5, 8)
        );

        JLabel explanation = new JLabel(
                "<html>"
                        + "<b>Understanding Resources</b><br>"
                        + "<b>R0</b> = Resource type 0"
                        + " &nbsp;&nbsp; "
                        + "<b>R1</b> = Resource type 1"
                        + " &nbsp;&nbsp; "
                        + "<b>R2</b> = Resource type 2"
                        + "<br>"
                        + "The numbers represent how many units "
                        + "of each resource type are involved."
                        + "</html>"
        );

        explanation.setFont(
                Theme.font(Font.PLAIN, 12f)
        );

        explanation.setForeground(Theme.TEXT);

        explanation.setVerticalAlignment(
                SwingConstants.CENTER
        );

        JPanel content = new JPanel(
                new BorderLayout()
        );

        content.setOpaque(false);

        content.setBorder(
                new EmptyBorder(2, 4, 2, 4)
        );

        content.add(
                explanation,
                BorderLayout.CENTER
        );

        legend.add(
                content,
                BorderLayout.CENTER
        );

        return legend;
    }


    // =========================================================
    // PARKING LOT
    // =========================================================

    private JComponent buildGraphArea() {

        JPanel card = roundedCard(
                new Color(0xD9ECFF),
                8
        );

        parkingLot.setOpaque(false);

        parkingLot.setPreferredSize(
                new Dimension(820, 280)
        );

        parkingLot.setMinimumSize(
                new Dimension(460, 280)
        );

        card.add(
                parkingLot,
                BorderLayout.CENTER
        );

        return card;
    }


    // =========================================================
    // BOTTOM PANEL - NARRATION ONLY
    // =========================================================

    private JComponent buildBottom() {

        JPanel bottom = new JPanel(
                new BorderLayout()
        );

        bottom.setOpaque(false);

        JPanel explanation = roundedCard(
                new Color(0xD9ECFF),
                11
        );

        explanation.setPreferredSize(
                new Dimension(800, 151)
        );

        narration.setFont(
                Theme.font(Font.PLAIN, 13f)
        );

        narration.setForeground(Theme.TEXT);

        narration.setVerticalAlignment(
                SwingConstants.TOP
        );

        narration.setHorizontalAlignment(
                SwingConstants.LEFT
        );

        JPanel narrationContent = new JPanel(
                new BorderLayout()
        );

        narrationContent.setOpaque(false);

        narrationContent.add(
                narration,
                BorderLayout.CENTER
        );

        explanation.add(
                narrationContent,
                BorderLayout.CENTER
        );

        bottom.add(
                explanation,
                BorderLayout.CENTER
        );

        return bottom;
    }


    // =========================================================
    // LOAD DEMO
    // =========================================================

    private void loadDemo() {

        BankersAlgorithm demo =
                new BankersAlgorithm(5, 3);

        File file = new File(
                "src/edu/keiser/deadlock/banker/banker-test.txt"
        );

        boolean loaded = false;

        if (file.exists()) {

            try {

                demo.readInputFromFile(
                        file.getAbsolutePath()
                );

                loaded = true;

            } catch (Exception ex) {

                System.err.println(
                        "Could not load demo file: "
                                + ex.getMessage()
                );
            }
        }

        // Fallback demonstration data.

        if (!loaded) {

            demo.setData(

                    new int[][]{
                            {7, 5, 3},
                            {3, 2, 2},
                            {9, 0, 2},
                            {2, 2, 2},
                            {4, 3, 3}
                    },

                    new int[][]{
                            {0, 1, 0},
                            {2, 0, 0},
                            {3, 0, 2},
                            {2, 1, 1},
                            {0, 0, 2}
                    },

                    new int[]{3, 3, 2}
            );
        }

        allocationData =
                demo.getAllocationMatrix();

        needData =
                demo.getNeedMatrix();

        availableData =
                demo.getAvailableResources();

        // Use the actual algorithm to determine safety.

        boolean safe = demo.isSafe();

        sequence = safe
                ? demo.getSafeSequence()
                : null;

        // Generate the visual trace using the same
        // process-search order as BankersAlgorithm.isSafe().

        buildTrace();

        if (safe != traceSafe) {

            throw new IllegalStateException(
                    "The visual trace does not match "
                            + "BankersAlgorithm."
            );
        }

        step = -1;

        demoLoaded = true;

        setControlsEnabled(true);

        render();

        revalidate();
        repaint();
    }


    // =========================================================
    // BUILD ALGORITHM TRACE
    // =========================================================

    private void buildTrace() {

        trace.clear();

        traceSafe = false;

        int n = allocationData.length;
        int r = availableData.length;

        int[] work = availableData.clone();

        boolean[] finish = new boolean[n];

        int completed = 0;

        while (completed < n) {

            boolean foundProcess = false;

            // Check processes in their original order.

            for (int p = 0; p < n; p++) {

                if (finish[p]) {
                    continue;
                }

                int[] before = work.clone();

                boolean canFinish = true;

                // Check Need <= Work.

                for (int j = 0; j < r; j++) {

                    if (needData[p][j] > work[j]) {

                        canFinish = false;
                        break;
                    }
                }

                // Record the process evaluation.

                trace.add(
                        new TraceEvent(
                                "CHECK",
                                p,
                                before,
                                needData[p],
                                allocationData[p],
                                before,
                                canFinish
                        )
                );

                if (canFinish) {

                    // Process finishes and releases resources.

                    for (int j = 0; j < r; j++) {

                        work[j] += allocationData[p][j];
                    }

                    finish[p] = true;

                    completed++;

                    foundProcess = true;

                    // Record resource release.

                    trace.add(
                            new TraceEvent(
                                    "FINISH",
                                    p,
                                    before,
                                    needData[p],
                                    allocationData[p],
                                    work,
                                    true
                            )
                    );

                    // Match the original algorithm:
                    // start checking again from P0.

                    break;
                }
            }

            // No unfinished process could finish.

            if (!foundProcess) {

                traceSafe = false;
                return;
            }
        }

        traceSafe = true;
    }


    // =========================================================
    // PLAYBACK
    // =========================================================

    private void play() {

        if (!demoLoaded || step >= trace.size() - 1) {
            return;
        }

        if (timer != null && timer.isRunning()) {
            return;
        }

        timer = new Timer(
                STEP_DELAY_MS,
                e -> advance()
        );

        timer.start();
    }


    private void stopTimer() {

        if (timer != null) {

            timer.stop();
            timer = null;
        }
    }


    private void advance() {

        if (!demoLoaded || step >= trace.size() - 1) {

            stopTimer();
            return;
        }

        step++;

        render();

        if (step >= trace.size() - 1) {
            stopTimer();
        }
    }


    private void previous() {

        if (!demoLoaded || step < 0) {
            return;
        }

        step--;

        render();
    }


    private void restart() {

        if (!demoLoaded) {
            return;
        }

        step = -1;

        render();
    }


    public void shutdown() {

        stopTimer();

        parkingLot.shutdown();
    }


    // =========================================================
    // RENDER CURRENT STATE
    // =========================================================

    private void render() {

        if (!demoLoaded) {
            return;
        }

        int n = allocationData.length;
        int r = availableData.length;

        boolean[] finished = new boolean[n];

        int current = -1;

        int[] work = availableData.clone();

        // Reconstruct state from event history.

        for (int i = 0;
             i <= step && i < trace.size();
             i++) {

            TraceEvent event = trace.get(i);

            if ("CHECK".equals(event.type)) {

                current = event.process;

                work = event.workBefore.clone();
            }

            if ("FINISH".equals(event.type)) {

                finished[event.process] = true;

                current = event.process;

                work = event.workAfter.clone();
            }
        }

        if (step < 0) {

            current = -1;

            work = availableData.clone();
        }

        // Calculate total resources.

        int[] total = new int[r];

        for (int[] row : allocationData) {

            for (int j = 0; j < r; j++) {

                total[j] += row[j];
            }
        }

        for (int j = 0; j < r; j++) {

            total[j] += availableData[j];
        }

        // Preserve existing graph View.

        currentView = new BankersGraphPanel.View(
                allocationData,
                needData,
                work,
                total,
                null,
                -1,
                current,
                finished
        );

        // Update parking visualization.

        parkingLot.setParkingState(
                allocationData,
                needData,
                work,
                finished,
                current
        );

        updateProgress();

        updateStory();

        parkingLot.repaint();
    }


    // =========================================================
    // EXPLANATION / NARRATION
    // =========================================================

    private void updateStory() {

        narration.setForeground(Theme.TEXT);

        // INITIAL STATE

        if (step < 0) {

            narration.setText(
                    "<html><b>Ready to begin.</b><br>"
                            + "Work (resources currently available): "
                            + labels(availableData)
                            + "<br>Need means the additional units "
                            + "a process requires to finish."
                            + "<br>A process can finish only when "
                            + "every value in Need is less than "
                            + "or equal to the corresponding value "
                            + "in Work."
                            + "</html>"
            );

            return;
        }

        if (step >= trace.size()) {
            return;
        }

        TraceEvent event = trace.get(step);

        int p = event.process;

        // UNSAFE STATE

        if (!traceSafe && step == trace.size() - 1) {

            narration.setText(
                    "<html><b>No process can finish with "
                            + "the current Work.</b><br>"
                            + "The remaining processes need more "
                            + "resources than the system can "
                            + "currently provide."
                            + "<br>The algorithm cannot find a "
                            + "safe sequence."
                            + "<br><br><b>The system is in an "
                            + "UNSAFE STATE.</b>"
                            + "</html>"
            );

            narration.setForeground(
                    Theme.DANGER_TEXT
            );

            return;
        }

        // PROCESS CHECK

        if ("CHECK".equals(event.type)) {

            if (event.canFinish) {

                narration.setText(
                        "<html><b>Checking P" + p
                                + ": It can finish.</b><br>"
                                + "<b>Need</b> (additional units "
                                + "required): "
                                + labels(event.need)
                                + "<br><b>Work</b> (units currently "
                                + "available): "
                                + labels(event.workBefore)
                                + "<br>Every resource need is less "
                                + "than or equal to the available "
                                + "amount. P" + p
                                + " can safely complete."
                                + "</html>"
                );

            } else {

                narration.setText(
                        "<html><b>Checking P" + p
                                + ": It must wait.</b><br>"
                                + "<b>Need</b> (additional units "
                                + "required): "
                                + labels(event.need)
                                + "<br><b>Work</b> (units currently "
                                + "available): "
                                + labels(event.workBefore)
                                + "<br>At least one resource need "
                                + "is greater than the available "
                                + "amount. P" + p
                                + " cannot finish yet. The algorithm "
                                + "checks the next process."
                                + "</html>"
                );

                narration.setForeground(AMBER);
            }

            return;
        }

        // PROCESS COMPLETION AND RESOURCE RELEASE

        if ("FINISH".equals(event.type)) {

            // FINAL SAFE STATE
            // Show the Safe Sequence only after every process finishes.

            if (traceSafe && step == trace.size() - 1) {

                narration.setText(
                        "<html><b>SAFE STATE</b><br>"
                                + "All processes completed successfully."
                                + "<br><br>"
                                + "<b>Safe Sequence:</b> "
                                + sequenceText(sequence)
                                + "<br><br>"
                                + "All resources were released."
                                + "</html>"
                );

                narration.setForeground(GREEN);

                return;
            }

            // NORMAL PROCESS COMPLETION

            narration.setText(
                    "<html><b>P" + p + " has finished!</b><br>"
                            + "The process returns the resources "
                            + "it was holding."
                            + "<br><b>Allocation</b> (units held "
                            + "by this process): "
                            + labels(event.allocation)
                            + "<br><b>Work before release:</b> "
                            + labels(event.workBefore)
                            + "<br><b>New Work:</b> "
                            + labels(event.workAfter)
                            + "<br>These returned units are now "
                            + "available to other processes."
                            + "<br>The algorithm starts checking "
                            + "again from P0."
                            + "</html>"
            );
        }
    }


    // =========================================================
    // PROGRESS
    // =========================================================

    private void updateProgress() {

        int completed = 0;

        for (int i = 0;
             i <= step && i < trace.size();
             i++) {

            if ("FINISH".equals(trace.get(i).type)) {

                completed++;
            }
        }

        int maximum = Math.max(
                1,
                allocationData.length
        );

        progress.setMaximum(maximum);

        progress.setValue(completed);

        progressText.setText(
                completed + " / "
                        + maximum + " completed"
        );
    }


    // =========================================================
    // BUTTON STATES
    // =========================================================

    private void setControlsEnabled(boolean enabled) {

        previousButton.setEnabled(enabled);

        playButton.setEnabled(enabled);

        pauseButton.setEnabled(enabled);

        nextButton.setEnabled(enabled);

        restartButton.setEnabled(enabled);
    }


    // =========================================================
    // EXISTING PUBLIC METHODS
    // =========================================================

    public BankersGraphPanel.View currentView() {

        return currentView;
    }


    public boolean isDemoLoaded() {

        return demoLoaded;
    }


    public boolean isPlaying() {

        return timer != null && timer.isRunning();
    }


    // =========================================================
    // UI HELPERS
    // =========================================================

    private static JPanel roundedCard(
            Color background,
            int padding
    ) {

        JPanel card = new JPanel(
                new BorderLayout(0, 6)
        ) {

            @Override
            protected void paintComponent(Graphics g) {

                Graphics2D g2 =
                        (Graphics2D) g.create();

                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                Shape shape =
                        new RoundRectangle2D.Double(
                                0.5,
                                0.5,
                                getWidth() - 1,
                                getHeight() - 1,
                                12,
                                12
                        );

                g2.setColor(background);

                g2.fill(shape);

                g2.setColor(Theme.CARD_BORDER);

                g2.draw(shape);

                g2.dispose();
            }
        };

        card.setOpaque(false);

        card.setBorder(
                new EmptyBorder(
                        padding,
                        padding,
                        padding,
                        padding
                )
        );

        return card;
    }


    private static void styleButton(JButton button, Color color) {

        button.setFont(
                Theme.font(Font.BOLD, 11f)
        );

        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setBorder(new EmptyBorder(6, 12, 6, 12));
        button.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );

        button.putClientProperty("button.color", color);
        button.addPropertyChangeListener("enabled", e -> applyButtonColors(button));
        applyButtonColors(button);
    }


    private static void applyButtonColors(JButton button) {

        Color color =
                (Color) button.getClientProperty("button.color");

        if (color == null) {
            return;
        }

        if (button.isEnabled()) {
            button.setBackground(color);
            button.setForeground(Color.WHITE);
        } else {
            button.setBackground(muted(color));
            button.setForeground(DISABLED_TEXT);
        }
    }


    private static Color muted(Color color) {

        float mix = 0.55f;
        return new Color(
                Math.round(color.getRed() * (1 - mix) + 255 * mix),
                Math.round(color.getGreen() * (1 - mix) + 255 * mix),
                Math.round(color.getBlue() * (1 - mix) + 255 * mix)
        );
    }


    // =========================================================
    // RESOURCE LABELS
    // =========================================================

    private static String labels(int[] values) {

        if (values == null || values.length == 0) {
            return "—";
        }

        StringBuilder text = new StringBuilder();

        for (int i = 0; i < values.length; i++) {

            if (i > 0) {
                text.append(", ");
            }

            text.append(values[i])
                    .append(" units of R")
                    .append(i);
        }

        return text.toString();
    }


    // =========================================================
    // SAFE SEQUENCE TEXT
    // =========================================================

    private static String sequenceText(int[] sequence) {

        if (sequence == null || sequence.length == 0) {
            return "—";
        }

        StringBuilder text = new StringBuilder();

        for (int i = 0; i < sequence.length; i++) {

            if (i > 0) {
                text.append(" → ");
            }

            text.append("P").append(sequence[i]);
        }

        return text.toString();
    }
}
