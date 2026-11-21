package edu.keiser.deadlock.ui;

import edu.keiser.deadlock.banker.BankersAlgorithm;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * "Watch it Live" tab: a short visual lesson with a single Load Demo button.
 *
 * <p>The predefined demo loads only when the student clicks <b>Load Demo</b>.
 * There are no editable matrices or playback controls here — entering data
 * belongs to the "Tables &amp; Safety Check" tab. This tab uses the same
 * {@link BankersGraphPanel} design as the tables tab, but with its own data.</p>
 */
public class BankersLiveScreen extends JPanel {

    private static final Color ACCENT = new Color(0x185FA5);
    private static final Color BLUE = new Color(0x185FA5);
    private static final Color AMBER = new Color(0xB77916);
    private static final Color GREEN = new Color(0x3B6D11);
    private static final int STEP_DELAY_MS = 1100;

    private final BankersGraphPanel graph = new BankersGraphPanel();

    private final JLabel rule = new JLabel(
            "A state is safe when every process can finish in some order.");
    private final JLabel narration = new JLabel(
            "<html>Press <b>Load Demo</b> to watch the safety check.</html>");
    private final JTextArea history = new JTextArea(4, 40);
    private final JProgressBar progress = new JProgressBar();

    private final JButton loadDemoButton = new JButton("Load Demo");

    private Timer timer;
    private boolean demoLoaded;

    private int[][] allocationData;
    private int[][] needData;
    private int[] availableData;
    private int[] sequence;
    private int step = -1;

    private BankersGraphPanel.View currentView = BankersGraphPanel.View.empty();

    public BankersLiveScreen() {
        super(new BorderLayout(0, 10));
        setBackground(Theme.BACKGROUND);
        setBorder(new EmptyBorder(10, 14, 10, 14));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildGraphArea(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        loadDemoButton.addActionListener(e -> {
            stopTimer();
            loadDemo();
            playOnce();
        });
    }

    /* ----------------------------- construction ---------------------------- */

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setOpaque(false);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(primary(loadDemoButton));
        header.add(buttonRow, BorderLayout.NORTH);

        rule.setFont(Theme.font(Font.BOLD, 15f));
        rule.setForeground(Theme.SUCCESS_TEXT);
        header.add(rule, BorderLayout.CENTER);

        progress.setForeground(ACCENT);
        progress.setBackground(new Color(0xE7F0FA));
        progress.setBorderPainted(false);
        progress.setMaximum(1);
        header.add(progress, BorderLayout.SOUTH);
        return header;
    }

    private JComponent buildGraphArea() {
        JPanel card = roundedCard(Color.WHITE, 10);
        graph.setOpaque(false);
        graph.setPreferredSize(new Dimension(760, 410));
        card.add(graph, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildBottom() {
        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);

        narration.setFont(Theme.font(Font.BOLD, 17f));
        narration.setForeground(Theme.TEXT);
        narration.setVerticalAlignment(SwingConstants.TOP);
        JPanel narrationCard = roundedCard(Theme.CARD, 10);
        narrationCard.add(narration, BorderLayout.CENTER);
        bottom.add(narrationCard, BorderLayout.NORTH);

        history.setEditable(false);
        history.setFocusable(false);
        history.setFont(Theme.font(Font.PLAIN, 13f));
        history.setForeground(Theme.TEXT_MUTED);
        history.setBackground(Color.WHITE);
        history.setBorder(new EmptyBorder(6, 10, 6, 10));
        JScrollPane scroll = new JScrollPane(history);
        scroll.setBorder(new LineBorder(Theme.CARD_BORDER));
        bottom.add(scroll, BorderLayout.CENTER);

        JPanel info = new JPanel(new GridLayout(1, 2, 12, 0));
        info.setOpaque(false);
        JLabel workLabel = new JLabel("Work grows when a process returns what it holds.");
        workLabel.setFont(Theme.font(Font.PLAIN, 12f));
        workLabel.setForeground(ACCENT);
        workLabel.setBorder(new EmptyBorder(4, 8, 4, 8));
        JPanel workCard = roundedCard(Color.WHITE, 8);
        workCard.add(workLabel, BorderLayout.CENTER);
        info.add(workCard);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        legend.setOpaque(false);
        legend.add(chip(BLUE, "allocated"));
        legend.add(chip(AMBER, "still needs"));
        legend.add(chip(GREEN, "released / finished"));
        JPanel legendCard = roundedCard(Color.WHITE, 8);
        legendCard.add(legend, BorderLayout.CENTER);
        info.add(legendCard);
        bottom.add(info, BorderLayout.SOUTH);
        return bottom;
    }

    /* -------------------------------- demo --------------------------------- */

    /** Loads the predefined demo. Only the Load Demo button calls this method. */
    private void loadDemo() {
        BankersAlgorithm demo = new BankersAlgorithm(5, 3);
        File file = new File("src/edu/keiser/deadlock/banker/banker-test.txt");
        boolean loaded = false;
        if (file.exists()) {
            try {
                demo.readInputFromFile(file.getAbsolutePath());
                loaded = true;
            } catch (Exception ignored) {
                // Fall through to the built-in demo values.
            }
        }
        if (!loaded) {
            demo.setData(
                    new int[][]{{7, 5, 3}, {3, 2, 2}, {9, 0, 2}, {2, 2, 2}, {4, 3, 3}},
                    new int[][]{{0, 1, 0}, {2, 0, 0}, {3, 0, 2}, {2, 1, 1}, {0, 0, 2}},
                    new int[]{3, 3, 2});
        }

        allocationData = demo.getAllocationMatrix();
        needData = demo.getNeedMatrix();
        availableData = demo.getAvailableResources();
        boolean safe = demo.isSafe();
        sequence = safe ? demo.getSafeSequence() : null;
        step = -1;
        demoLoaded = true;
        render();
    }

    /** Plays one pass through the safe sequence, then stops. */
    private void playOnce() {
        if (!demoLoaded || sequence == null || sequence.length == 0) {
            return;
        }
        stopTimer();
        timer = new Timer(STEP_DELAY_MS, e -> advance());
        timer.setInitialDelay(STEP_DELAY_MS);
        timer.start();
    }

    private void advance() {
        if (!demoLoaded || sequence == null || step >= sequence.length) {
            stopTimer();
            return;
        }
        step++;
        if (step >= sequence.length) {
            stopTimer();
        }
        render();
    }

    /** Rebuilds the picture and the explanation for the current step. */
    private void render() {
        if (!demoLoaded) {
            return;
        }
        int n = allocationData == null ? 0 : allocationData.length;
        int r = availableData == null ? 0 : availableData.length;

        boolean[] finished = new boolean[n];
        int current = -1;
        if (sequence != null) {
            for (int i = 0; i <= step && i < sequence.length; i++) {
                finished[sequence[i]] = true;
            }
            if (step >= 0 && step < sequence.length) {
                current = sequence[step];
            }
        }

        int[] total = new int[r];
        if (allocationData != null) {
            for (int[] row : allocationData) {
                for (int j = 0; j < r; j++) {
                    total[j] += row[j];
                }
            }
        }
        if (availableData != null) {
            for (int j = 0; j < r; j++) {
                total[j] += availableData[j];
            }
        }

        currentView = new BankersGraphPanel.View(
                allocationData, needData, availableData, total,
                null, -1, current, finished);
        graph.setView(currentView);

        int steps = sequence == null ? 0 : sequence.length;
        progress.setMaximum(Math.max(1, steps));
        progress.setValue(Math.min(steps, Math.max(0, step + 1)));

        updateStory();
    }

    private void updateStory() {
        if (step < 0) {
            narration.setText("<html><b>Start with " + labels(availableData) + " free.</b>"
                    + "<br>Blue arrows show what is allocated. Can every process finish?</html>");
            narration.setForeground(Theme.TEXT);
        } else if (sequence == null) {
            narration.setText("<html><b>No safe order exists.</b> Some processes cannot finish.</html>");
            narration.setForeground(Theme.DANGER_TEXT);
        } else if (step < sequence.length) {
            int p = sequence[step];
            narration.setText("<html><b>P" + p + " can finish.</b> Needs "
                    + labels(needData[p]) + "; Work has " + labels(workBefore(step))
                    + ". It returns " + labels(allocationData[p]) + ", so Work becomes "
                    + labels(workBefore(step + 1)) + ".</html>");
            narration.setForeground(Theme.TEXT);
        } else {
            narration.setText("<html><b>Safe.</b> All processes can finish: "
                    + sequenceText(sequence) + ".</html>");
            narration.setForeground(Theme.SUCCESS_TEXT);
        }
        history.setText(recentEvents());
    }

    private String recentEvents() {
        List<String> events = new ArrayList<>();
        events.add("Start · Free: " + labels(availableData));
        if (sequence != null) {
            for (int i = 0; i <= step && i < sequence.length; i++) {
                int p = sequence[i];
                events.add("P" + p + " finishes · Work: " + labels(workBefore(i + 1)));
            }
            if (step >= sequence.length) {
                events.add("Done · All processes can finish");
            }
        }
        int from = Math.max(0, events.size() - 6);
        return String.join("\n", events.subList(from, events.size()));
    }

    private int[] workBefore(int count) {
        int[] work = availableData == null ? new int[0] : availableData.clone();
        if (sequence == null || allocationData == null) {
            return work;
        }
        for (int i = 0; i < count && i < sequence.length; i++) {
            for (int j = 0; j < work.length; j++) {
                work[j] += allocationData[sequence[i]][j];
            }
        }
        return work;
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    public void shutdown() {
        stopTimer();
    }

    /** Exposed for smoke tests: the graph data currently on screen. */
    public BankersGraphPanel.View currentView() {
        return currentView;
    }

    public boolean isDemoLoaded() {
        return demoLoaded;
    }

    public boolean isPlaying() {
        return timer != null && timer.isRunning();
    }

    /* -------------------------------- helpers ------------------------------- */

    private static JPanel roundedCard(Color background, int padding) {
        JPanel card = new JPanel(new BorderLayout(0, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Shape shape = new RoundRectangle2D.Double(0.5, 0.5,
                        getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(background);
                g2.fill(shape);
                g2.setColor(Theme.CARD_BORDER);
                g2.draw(shape);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(padding, padding, padding, padding));
        return card;
    }

    private static JButton primary(JButton button) {
        button.setFont(Theme.font(Font.BOLD, 13f));
        button.setForeground(ACCENT);
        button.setFocusPainted(false);
        return button;
    }

    private static JLabel chip(Color color, String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.PLAIN, 11f));
        label.setForeground(Theme.TEXT_MUTED);
        label.setIcon(new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(color);
                g2.fillRoundRect(x, y + 2, 12, 12, 3, 3);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 14;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }
        });
        return label;
    }

    private static String labels(int[] values) {
        if (values == null || values.length == 0) {
            return "\u2014";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append("R").append(i).append(": ").append(values[i]);
        }
        return text.toString();
    }

    private static String sequenceText(int[] sequence) {
        if (sequence == null || sequence.length == 0) {
            return "\u2014";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < sequence.length; i++) {
            if (i > 0) {
                text.append(" \u2192 ");
            }
            text.append("P").append(sequence[i]);
        }
        return text.toString();
    }
}
