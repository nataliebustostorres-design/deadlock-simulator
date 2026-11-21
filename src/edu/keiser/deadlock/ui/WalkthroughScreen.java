package edu.keiser.deadlock.ui;

import edu.keiser.deadlock.philosophers.PhilosopherState;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static edu.keiser.deadlock.philosophers.PhilosophersSimulation.FREE;
import static  edu.keiser.deadlock.philosophers.PhilosophersSimulation.N;

public class WalkthroughScreen extends JPanel {

    private record Step(String title, String body, String inCode,
                        String states, int[] forks, int[][] waits,
                        boolean deadlock, int ring, int... hiForks) {
    }

    private static final int F = FREE;

    private static final List<Step> STEPS = List.of(
            new Step("Five philosophers, five forks",
                    "Five philosophers sit at a round table, each with a plate. There is one fork "
                            + "between each pair of plates: five forks in total.<br><br>"
                            + "To eat, a philosopher needs <b>both</b> forks next to him.",
                    "5 threads share 5 resources.",
                    "TTTTT", new int[]{F, F, F, F, F}, new int[][]{}, false, -1),

            new Step("Who uses which fork",
                    "P1 can only use F1 (on his left) and F2 (on his right). Each fork is shared "
                            + "with a neighbor: F2 is also P2's fork.<br><br>"
                            + "A fork can be in only one hand at a time.",
                    "a fork is a lock: only one thread can hold it.",
                    "TTTTT", new int[]{F, F, F, F, F}, new int[][]{}, false, 0, 0, 1),

            new Step("P1 gets hungry",
                    "P1 follows a simple rule: <b>pick up the left fork first, then the right one.</b>"
                            + "<br><br>He picks up F1. So far, so good.",
                    "the thread locks its first resource.",
                    "HTTTT", new int[]{0, F, F, F, F}, new int[][]{}, false, -1),

            new Step("Everyone gets hungry at once",
                    "Now all five get hungry at the same moment, and all five follow the same rule."
                            + "<br><br>Each one picks up his left fork. Look at the table: "
                            + "<b>no forks are left.</b>",
                    "each thread holds one lock.",
                    "HHHHH", new int[]{0, 1, 2, 3, 4}, new int[][]{}, false, -1),

            new Step("Each one reaches for the right fork...",
                    "Now each philosopher wants his right fork. But it is in his neighbor's hand."
                            + "<br><br>The arrow <b>P1 &rarr; P2</b> means: P1 is waiting for a fork "
                            + "that P2 holds.",
                    "each thread waits for a lock held by another thread.",
                    "HHHHH", new int[]{0, 1, 2, 3, 4}, new int[][]{{0, 1}, {1, 2}, {2, 3}}, false, -1),

            new Step("Deadlock",
                    "The arrows close a circle: P1 waits for P2, P2 for P3 ... and P5 waits for P1."
                            + "<br><br>Nobody lets go, so nobody can ever eat. The program "
                            + "<b>freezes forever</b>, with no error message.",
                    "this is deadlock: a circle of threads, each waiting for the next.",
                    "HHHHH", new int[]{0, 1, 2, 3, 4},
                    new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 0}}, true, -1),

            new Step("The fix: one new rule",
                    "New rule: <b>always pick up the lower-numbered fork first.</b><br><br>"
                            + "For P1 to P4 nothing changes. Only P5 changes: his forks are F5 and F1, "
                            + "so now he must pick up <b>F1 first</b>.",
                    "resource ordering: always lock resources in the same global order.",
                    "TTTTT", new int[]{F, F, F, F, F}, new int[][]{}, false, 4, 0, 4),

            new Step("P1 and P5 compete for F1",
                    "Everyone gets hungry at once again. P1 and P5 both want F1 first. "
                            + "Only one can win: P1 gets it.<br><br>"
                            + "P5 waits <b>with empty hands</b>. That leaves F5 free on the table.",
                    "one thread waits without holding anything, so the circle cannot close.",
                    "HHHHH", new int[]{0, 1, 2, 3, F}, new int[][]{{4, 0}}, false, -1),

            new Step("P4 can eat",
                    "Since P5 is not holding F5, P4 picks it up. P4 now has <b>both</b> forks and eats.",
                    "at least one thread always gets everything it needs.",
                    "HHHEH", new int[]{0, 1, 2, 3, 3},
                    new int[][]{{4, 0}, {0, 1}, {1, 2}, {2, 3}}, false, -1),

            new Step("The chain unwinds",
                    "When P4 finishes, he puts down F4 and F5. P3 takes F4 and eats. "
                            + "Then P2, then P1, then P5.<br><br>"
                            + "Everyone eats. <b>No deadlock, ever.</b>",
                    "the waiting chain always has an end, so it always moves.",
                    "HHETH", new int[]{0, 1, 2, 2, F}, new int[][]{{4, 0}, {0, 1}, {1, 2}}, false, -1)
    );

    private static final int TEXT_WIDTH = 400;

    private int index = 0;

    private final JPanel canvas = new JPanel() {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            TableRenderer.paint(g2, 10, 10, getWidth() - 20, getHeight() - 20, viewOf(STEPS.get(index)));
            g2.dispose();
        }
    };
    private final JLabel counter = new JLabel();
    private final JLabel title = new JLabel();
    private final JLabel body = new JLabel();
    private final JLabel inCode = new JLabel();
    private final JButton back = new JButton("Back");
    private final JButton next = new JButton("Next");

    public WalkthroughScreen() {
        super(new BorderLayout());
        setBackground(Theme.BACKGROUND);

        canvas.setBackground(Theme.BACKGROUND);
        canvas.setPreferredSize(new Dimension(640, 620));
        add(canvas, BorderLayout.CENTER);
        add(buildText(), BorderLayout.EAST);

        back.addActionListener(e -> show(index - 1));
        next.addActionListener(e -> show((index + 1) % STEPS.size()));
        show(0);
    }

    private JComponent buildText() {
        counter.setFont(Theme.font(Font.PLAIN, 15f));
        counter.setForeground(Theme.TEXT_MUTED);
        title.setFont(Theme.font(Font.BOLD, 26f));
        body.setFont(Theme.font(Font.PLAIN, 19f));
        body.setForeground(Theme.TEXT);
        body.setVerticalAlignment(SwingConstants.TOP);
        inCode.setFont(Theme.font(Font.PLAIN, 16f));
        inCode.setForeground(Theme.TEXT_MUTED);
        inCode.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(0x378ADD)),
                BorderFactory.createEmptyBorder(4, 12, 4, 0)));
        back.setFont(Theme.font(Font.PLAIN, 16f));
        next.setFont(Theme.font(Font.BOLD, 16f));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(back);
        buttons.add(Box.createHorizontalStrut(10));
        buttons.add(next);

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setBackground(Theme.BACKGROUND);
        column.setBorder(BorderFactory.createEmptyBorder(40, 10, 30, 40));
        for (JComponent c : new JComponent[]{counter, title, body, inCode, buttons}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        column.add(counter);
        column.add(Box.createVerticalStrut(8));
        column.add(title);
        column.add(Box.createVerticalStrut(16));
        column.add(body);
        column.add(Box.createVerticalStrut(20));
        column.add(inCode);
        column.add(Box.createVerticalStrut(28));
        column.add(buttons);
        column.add(Box.createVerticalGlue());
        column.setPreferredSize(new Dimension(TEXT_WIDTH + 70, 600));
        return column;
    }

    private void show(int newIndex) {
        if (newIndex < 0) {
            return;
        }
        index = newIndex;
        Step step = STEPS.get(index);

        counter.setText("Step " + (index + 1) + " of " + STEPS.size());
        title.setText(step.title());
        title.setForeground(step.deadlock() ? Theme.DANGER_TEXT : Theme.TEXT);
        body.setText("<html><div style='width:" + (TEXT_WIDTH * 3 / 4) + "px'>" + step.body() + "</div></html>");
        inCode.setText("<html><div style='width:" + ((TEXT_WIDTH - 20) * 3 / 4) + "px'><b>In code:</b> "
                + step.inCode() + "</div></html>");
        back.setVisible(index > 0);
        next.setText(index == STEPS.size() - 1 ? "Start over" : "Next");
        canvas.repaint();
    }

    private static TableRenderer.View viewOf(Step step) {
        PhilosopherState[] states = new PhilosopherState[N];
        for (int i = 0; i < N; i++) {
            states[i] = switch (step.states().charAt(i)) {
                case 'H' -> PhilosopherState.HUNGRY;
                case 'E' -> PhilosopherState.EATING;
                default -> PhilosopherState.THINKING;
            };
        }
        List<int[]> waits = new ArrayList<>(List.of(step.waits()));
        boolean[] hiForks = new boolean[N];
        for (int f : step.hiForks()) {
            hiForks[f] = true;
        }
        return new TableRenderer.View(states, step.forks(), waits, step.deadlock(), step.ring(), hiForks);
    }
}
