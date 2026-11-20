package edu.keiser.deadlock.ui;

import edu.keiser.deadlock.core.Mode;
import edu.keiser.deadlock.philosophers.PhilosophersSimulation;
import edu.keiser.deadlock.philosophers.PhilosophersSimulation.Snapshot;

import javax.swing.*;
import java.awt.*;
import java.util.List;


public class PhilosophersScreen extends JPanel {

    private static final String RULE_UNSAFE =
            "Rule: every philosopher picks up his LEFT fork first, then the right one. "
                    + "Watch what happens when all five do it at the same time.";
    private static final String RULE_SAFE =
            "Rule: every philosopher picks up the LOWER-numbered fork first. "
                    + "For P1 to P4 nothing changes. Only P5 changes: he takes F1 before F5.";

    private final PhilosophersSimulation simulation = new PhilosophersSimulation();
    private final TablePanel table = new TablePanel();
    private final SidePanel side = new SidePanel();

    private final JLabel rule = new JLabel(" ");
    private final JButton pauseButton = new JButton("Pause");
    private final JLabel narration = new JLabel(" ");
    private final JTextArea history = new JTextArea(4, 40);
    private final Timer timer;

    public PhilosophersScreen() {
        super(new BorderLayout());
        setBackground(Theme.BACKGROUND);

        add(buildTop(), BorderLayout.NORTH);
        add(table, BorderLayout.CENTER);
        add(side, BorderLayout.EAST);
        add(buildBottom(), BorderLayout.SOUTH);

        timer = new Timer(100, e -> refresh());
        timer.start();
    }

    public void shutdown() {
        timer.stop();
        simulation.stop();
    }

    private JComponent buildTop() {
        JButton problem = new JButton("Show the problem");
        JButton solution = new JButton("Show the solution");
        problem.setFont(Theme.font(Font.BOLD, 15f));
        solution.setFont(Theme.font(Font.BOLD, 15f));
        pauseButton.setFont(Theme.font(Font.PLAIN, 15f));
        pauseButton.setEnabled(false);

        problem.addActionListener(e -> start(Mode.UNSAFE));
        solution.addActionListener(e -> start(Mode.SAFE));
        pauseButton.addActionListener(e -> togglePause());

        JSlider speed = new JSlider(1, 5, 2);
        speed.setPreferredSize(new Dimension(140, speed.getPreferredSize().height));
        speed.addChangeListener(e -> simulation.setDelayMs(1200 / speed.getValue()));
        simulation.setDelayMs(1200 / speed.getValue());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(problem);
        buttons.add(solution);
        buttons.add(pauseButton);
        buttons.add(Box.createHorizontalStrut(18));
        buttons.add(label("Slow", 14f, Theme.TEXT_MUTED));
        buttons.add(speed);
        buttons.add(label("Fast", 14f, Theme.TEXT_MUTED));

        rule.setFont(Theme.font(Font.BOLD, 15f));
        rule.setForeground(Theme.TEXT);

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setBackground(Theme.BACKGROUND);
        top.setBorder(BorderFactory.createEmptyBorder(14, 16, 4, 16));
        top.add(buttons, BorderLayout.NORTH);
        top.add(rule, BorderLayout.SOUTH);
        return top;
    }

    private JComponent buildBottom() {
        narration.setFont(Theme.font(Font.BOLD, 19f));
        narration.setText("Press \"Show the problem\" to start.");
        narration.setForeground(Theme.TEXT_MUTED);

        history.setEditable(false);
        history.setFocusable(false);
        history.setFont(Theme.font(Font.PLAIN, 14f));
        history.setForeground(Theme.TEXT_MUTED);
        history.setBackground(Theme.CARD);
        history.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        JScrollPane scroll = new JScrollPane(history);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER));

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setBackground(Theme.BACKGROUND);
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 16, 14, 16));
        bottom.add(narration, BorderLayout.NORTH);
        bottom.add(scroll, BorderLayout.CENTER);
        return bottom;
    }

    private void start(Mode mode) {
        simulation.start(mode);
        rule.setText(mode == Mode.UNSAFE ? RULE_UNSAFE : RULE_SAFE);
        rule.setForeground(mode == Mode.UNSAFE ? Theme.WARNING_TEXT : Theme.SUCCESS_TEXT);
        pauseButton.setEnabled(true);
        pauseButton.setText("Pause");
    }

    private void togglePause() {
        boolean nowPaused = !simulation.isPaused();
        simulation.setPaused(nowPaused);
        pauseButton.setText(nowPaused ? "Resume" : "Pause");
    }

    private void refresh() {
        if (!simulation.isStarted()) {
            return;
        }
        Snapshot s = simulation.snapshot();
        table.setSnapshot(s);
        side.setSnapshot(s);
        updateNarration(s);
        updateHistory(s.events());
    }

    private void updateNarration(Snapshot s) {
        String text;
        Color color;
        if (s.deadlock()) {
            text = "DEADLOCK: each philosopher holds one fork and waits for the next. Nobody will ever let go.";
            color = Theme.DANGER_TEXT;
        } else if (simulation.isPaused()) {
            text = "Paused. Look at the arrows: who is waiting for whom?";
            color = Theme.TEXT_MUTED;
        } else {
            text = s.events().isEmpty() ? "Everyone is thinking..." : s.events().get(0);
            color = Theme.TEXT;
        }
        if (!text.equals(narration.getText())) {
            narration.setText(text);
            narration.setForeground(color);
        }
    }

    private void updateHistory(List<String> events) {
        String text = String.join("\n", events.subList(Math.min(1, events.size()), Math.min(8, events.size())));
        if (!text.equals(history.getText())) {
            history.setText(text);
            history.setCaretPosition(0);
        }
    }

    private static JLabel label(String text, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.PLAIN, size));
        l.setForeground(color);
        return l;
    }
}