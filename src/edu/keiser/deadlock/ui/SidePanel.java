package edu.keiser.deadlock.ui;

import edu.keiser.deadlock.core.Mode;
import edu.keiser.deadlock.philosophers.PhilosophersSimulation.Snapshot;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class SidePanel extends JPanel {

    private static final int WIDTH = 360;
    private static final int TEXT_WIDTH = (WIDTH - 50) * 3 / 4;

    private final JLabel conditions = new JLabel();
    private String lastConditions = "";

    public SidePanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 16));
        setPreferredSize(new Dimension(WIDTH, 600));

        JLabel concepts = new JLabel(conceptsHtml());
        concepts.setFont(Theme.font(Font.PLAIN, 14f));
        conditions.setFont(Theme.font(Font.PLAIN, 14f));

        add(card("What you are seeing", concepts));
        add(Box.createVerticalStrut(12));
        add(card("The 4 conditions for deadlock", conditions));
        add(Box.createVerticalStrut(12));

        add(Box.createVerticalGlue());

        setSnapshot(null);
    }

    public void setSnapshot(Snapshot s) {
        String html = conditionsHtml(s);
        if (!html.equals(lastConditions)) {
            conditions.setText(html);
            lastConditions = html;
        }
    }

    private static String conceptsHtml() {
        return html(
                row(Theme.THINKING, "Philosopher", "a <b>thread</b>")
                        + row(Theme.FORK, "Fork", "a <b>lock</b> on a shared resource (file, printer, account)")
                        + row(Theme.WORKING, "Eating", "the <b>critical section</b>")
                        + row(Theme.WAITING, "Waiting", "the thread is <b>blocked</b> on a lock"));
    }

    private static String row(Color color, String title, String text) {
        return "<p style='margin-bottom:6px'><font color='" + Theme.hex(color) + "'>&#9679;</font> "
                + "<b>" + title + "</b> = " + text + "</p>";
    }

    private static String conditionsHtml(Snapshot s) {
        boolean started = s != null;
        boolean deadlock = started && s.deadlock();
        boolean safe = started && s.mode() == Mode.SAFE;
        Color presentColor = deadlock ? Theme.DANGER_TEXT : Theme.WARNING_TEXT;

        String body = condition("1. Mutual exclusion",
                "A fork has only one owner at a time.",
                started ? "Present" : "", presentColor)
                + condition("2. Hold and wait",
                "He keeps one fork while waiting for the other.",
                !started ? "" : s.holdAndWait() ? "Present" : "Not right now",
                !started ? Theme.TEXT_MUTED : s.holdAndWait() ? presentColor : Theme.TEXT_MUTED)
                + condition("3. No preemption",
                "Nobody can take a fork away from its owner.",
                started ? "Present" : "", presentColor)
                + condition("4. Circular wait",
                circularText(s, deadlock, safe),
                !started ? "" : deadlock ? "Present" : safe ? "Prevented" : "Possible",
                !started ? Theme.TEXT_MUTED : deadlock ? Theme.DANGER_TEXT : safe ? Theme.SUCCESS_TEXT : Theme.WARNING_TEXT);

        String footer;
        if (deadlock) {
            footer = "<font color='" + Theme.hex(Theme.DANGER_TEXT) + "'><b>All four at once &rarr; DEADLOCK.</b></font>";
        } else if (safe) {
            footer = "<font color='" + Theme.hex(Theme.SUCCESS_TEXT) + "'><b>Condition 4 is impossible &rarr; no deadlock.</b></font>";
        } else {
            footer = "<font color='" + Theme.hex(Theme.TEXT_MUTED) + "'>Deadlock needs all four at once. Break one and it can't happen.</font>";
        }
        return html(body + "<p style='margin-top:4px'>" + footer + "</p>");
    }

    private static String circularText(Snapshot s, boolean deadlock, boolean safe) {
        if (deadlock) {
            return "P1 &rarr; P2 &rarr; P3 &rarr; P4 &rarr; P5 &rarr; P1";
        }
        if (safe) {
            return "Forks are taken in order, so the chain always ends.";
        }
        return "A closed chain: each one waits for the next.";
    }

    private static String condition(String title, String text, String status, Color statusColor) {
        String badge = status.isEmpty() ? ""
                : " &nbsp;<font color='" + Theme.hex(statusColor) + "'><b>" + status + "</b></font>";
        return "<p style='margin-bottom:6px'><b>" + title + "</b>" + badge + "<br>"
                + "<font color='" + Theme.hex(Theme.TEXT_MUTED) + "'>" + text + "</font></p>";
    }

    private static String html(String body) {
        return "<html><div style='width:" + TEXT_WIDTH + "px'>" + body + "</div></html>";
    }

    private static JPanel card(String title, JComponent content) {
        JPanel card = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.setColor(Theme.CARD);
                g2.fill(shape);
                g2.setColor(Theme.CARD_BORDER);
                g2.draw(shape);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.font(Font.BOLD, 16f));
        titleLabel.setForeground(Theme.TEXT);
        card.add(titleLabel, BorderLayout.NORTH);
        content.setForeground(Theme.TEXT);
        card.add(content, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(WIDTH, card.getPreferredSize().height + 400));
        return card;
    }
}
