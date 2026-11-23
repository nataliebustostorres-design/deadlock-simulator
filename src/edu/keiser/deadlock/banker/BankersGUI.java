
package edu.keiser.deadlock.banker;

import com.formdev.flatlaf.FlatLightLaf;
import edu.keiser.deadlock.ui.BankersGraphPanel;
import edu.keiser.deadlock.ui.BankersLiveScreen;
import edu.keiser.deadlock.ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;

public class BankersGUI extends JPanel {

    // ----------------------------- COLORS --------------------------------

    private static final Color ACCENT = new Color(0x185FA5);

    private static final Color FIND_COLOR = new Color(0x64748B);
    private static final Color RUN_COLOR = new Color(0x185FA5);
    private static final Color NEXT_COLOR = new Color(0x2878B8);
    private static final Color RESET_COLOR = new Color(0xC94C4C);
    private static final Color SUBMIT_COLOR = new Color(0x27864A);
    private static final Color CLEAR_COLOR = new Color(0xD97706);

    private static final Color INSTRUCTION_BG = new Color(0xEAF2FB);
    private static final Color INSTRUCTION_BORDER = new Color(0xC5D9EF);
    private static final Color INSTRUCTION_TEXT = new Color(0x24476A);

    private static final Color WARNING_BG = new Color(0xFFF4D6);
    private static final Color WARNING_BORDER = new Color(0xF0D58A);
    private static final Color WARNING_TEXT = new Color(0x946200);

    private static final Color GOOD_BG = new Color(0xE7F3DF);
    private static final Color BAD_BG = new Color(0xFCEBEB);
    private static final Color IDLE_BG = new Color(0xEFEDE7);
    private static final Color CURRENT_BG = new Color(0xE7F0FA);

    // ----------------------------- SPINNERS -------------------------------

    private final JSpinner processSpinner =
            new JSpinner(new CountModel(1, 20));

    private final JSpinner resourceSpinner =
            new JSpinner(new CountModel(1, 15));

    private final JSpinner requestProcessSpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, 4, 1));

    // ----------------------------- TABLES ---------------------------------

    private final JTable maxTable;
    private final JTable allocationTable;
    private final JTable availableTable;
    private final JTable needTable;

    private final JPanel sequencePanel =
            new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));

    // ----------------------------- REQUEST --------------------------------

    private final JTextField[] requestFieldsHolder = new JTextField[0];

    private JPanel requestFieldsPanel =
            new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

    private JTextField[] requestFields = requestFieldsHolder;

    // ----------------------------- COMPONENTS -----------------------------

    private final BankersGraphPanel graph = new BankersGraphPanel();

    private final JLabel statusLabel = new JLabel();
    private final JLabel explanation = new JLabel();

    private final JButton nextButton = new JButton("Next Step");
    private final JButton advancedToggle = new JButton("Advanced  \u25be");

    private final JPanel advancedPanel =
            new JPanel(new BorderLayout(0, 8));

    private final JPanel tablesPanel;
    private final BankersLiveScreen live;

    // ----------------------------- ALGORITHM DATA -------------------------

    private BankersAlgorithm algorithm;

    private int[][] maxData;
    private int[][] allocationData;
    private int[][] needData;
    private int[] availableData;

    private int[] sequence;
    private int step = -1;

    private boolean checked;
    private boolean safe;

    private int[] lastRequest;
    private int lastRequestProcess = -1;

    private boolean requestChangedData;

    private RequestSnapshot preRequestSnapshot;

    private record RequestSnapshot(
            String status,
            Color foreground,
            Color background,
            String explanation,
            boolean checked,
            boolean safe,
            int[] sequence,
            int step
    ) {
    }

    // ----------------------------- CONSTRUCTOR ----------------------------

    public BankersGUI() {
        super(new BorderLayout());

        setBackground(Theme.BACKGROUND);
        setBorder(new EmptyBorder(10, 14, 10, 14));

        maxTable = makeTable();
        allocationTable = makeTable();
        availableTable = makeTable();
        needTable = makeTable();

        needTable.setEnabled(false);

        tablesPanel = new JPanel(new BorderLayout(0, 8));
        tablesPanel.setBackground(Theme.BACKGROUND);

        tablesPanel.add(buildToolbar(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                matricesPanel(),
                graphCard()
        );

        split.setResizeWeight(0.62);
        split.setDividerLocation(0.62);
        split.setBorder(null);

        tablesPanel.add(split, BorderLayout.CENTER);
        tablesPanel.add(buildBottom(), BorderLayout.SOUTH);

        live = new BankersLiveScreen();

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.font(Font.BOLD, 14f));

        tabs.addTab("Watch it Live", live);
        tabs.addTab("Tables & Safety Check", tablesPanel);

        add(tabs, BorderLayout.CENTER);

        processSpinner.addChangeListener(e -> generateTables());
        resourceSpinner.addChangeListener(e -> generateTables());

        clearTables();
    }

    // ----------------------------- TOOLBAR --------------------------------

    private JComponent buildToolbar() {

        JPanel bar = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 8, 4)
        );

        bar.setOpaque(false);

        bar.add(numberField("Processes", processSpinner));
        bar.add(numberField("Resources", resourceSpinner));

        bar.add(separator());

        bar.add(button(
                "Find Need",
                FIND_COLOR,
                this::findNeed
        ));

        JButton run = button(
                "Run Algorithm",
                RUN_COLOR,
                this::runAlgorithm
        );

        run.setFont(Theme.font(Font.BOLD, 14f));
        bar.add(run);

        nextButton.setFont(Theme.font(Font.BOLD, 13f));
        styleButton(nextButton, NEXT_COLOR);
        nextButton.addActionListener(e -> nextStep());

        bar.add(nextButton);

        bar.add(button(
                "Reset",
                RESET_COLOR,
                this::reset
        ));

        bar.add(separator());

        advancedToggle.setFont(Theme.font(Font.BOLD, 13f));
        advancedToggle.setForeground(ACCENT);
        advancedToggle.setBackground(INSTRUCTION_BG);
        advancedToggle.setOpaque(true);
        advancedToggle.setFocusPainted(false);
        advancedToggle.setBorder(
                new EmptyBorder(7, 12, 7, 12)
        );
        advancedToggle.setCursor(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        );

        advancedToggle.addActionListener(e -> toggleAdvanced());

        bar.add(advancedToggle);

        return bar;
    }

    // ----------------------------- MATRICES -------------------------------

    private JComponent matricesPanel() {

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(0, 0, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;

        panel.add(
                matrixCard(
                        "Maximum",
                        "Most resources this process may ever use.",
                        maxTable
                ),
                gbc
        );

        gbc.gridx = 1;

        panel.add(
                matrixCard(
                        "Allocated",
                        "Resources this process is using now.",
                        allocationTable
                ),
                gbc
        );

        gbc.gridx = 0;
        gbc.gridy = 1;

        panel.add(
                matrixCard(
                        "Available",
                        "Resources free right now.",
                        availableTable
                ),
                gbc
        );

        gbc.gridx = 1;

        panel.add(
                matrixCard(
                        "Still Needs",
                        "Resources this process still needs to finish.",
                        needTable
                ),
                gbc
        );

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Color.WHITE);

        return scroll;
    }

    // ----------------------------- GRAPH CARD -----------------------------

    private JComponent graphCard() {

        JPanel card = new JPanel(new BorderLayout(0, 6)) {

            @Override
            protected void paintComponent(Graphics g) {

                Graphics2D g2 = (Graphics2D) g.create();

                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                Shape shape = new RoundRectangle2D.Double(
                        0.5,
                        0.5,
                        getWidth() - 1,
                        getHeight() - 1,
                        12,
                        12
                );

                g2.setColor(Color.WHITE);
                g2.fill(shape);

                g2.setColor(Theme.CARD_BORDER);
                g2.draw(shape);

                g2.dispose();
            }
        };

        card.setOpaque(false);
        card.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Resource Allocation Graph");

        title.setFont(Theme.font(Font.BOLD, 14f));
        title.setForeground(Theme.TEXT);

        card.add(title, BorderLayout.NORTH);

        graph.setOpaque(false);

        card.add(graph, BorderLayout.CENTER);

        return card;
    }

    // ----------------------------- BOTTOM PANEL ---------------------------

    private JComponent buildBottom() {

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        statusLabel.setFont(Theme.font(Font.BOLD, 20f));
        statusLabel.setBorder(
                new EmptyBorder(6, 14, 6, 14)
        );
        statusLabel.setOpaque(true);

        JPanel statusRow = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 10, 0)
        );

        statusRow.setOpaque(false);
        statusRow.add(statusLabel);

        panel.add(statusRow, BorderLayout.NORTH);

        sequencePanel.setOpaque(false);
        sequencePanel.setVisible(false);

        explanation.setFont(Theme.font(Font.PLAIN, 13f));
        explanation.setForeground(INSTRUCTION_TEXT);

        JPanel explainCard = new JPanel(new BorderLayout());

        explainCard.setBackground(INSTRUCTION_BG);

        explainCard.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(
                                INSTRUCTION_BORDER,
                                1,
                                true
                        ),
                        new EmptyBorder(10, 12, 10, 12)
                )
        );

        explainCard.add(explanation, BorderLayout.CENTER);

        JPanel result = new JPanel(new BorderLayout(0, 6));
        result.setOpaque(false);

        result.add(sequencePanel, BorderLayout.NORTH);
        result.add(explainCard, BorderLayout.CENTER);

        panel.add(result, BorderLayout.CENTER);

        advancedPanel.setOpaque(false);
        advancedPanel.setVisible(false);

        advancedPanel.add(
                buildRequestControls(),
                BorderLayout.CENTER
        );

        panel.add(advancedPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ----------------------------- REQUEST CONTROLS -----------------------

    private JComponent buildRequestControls() {

        JPanel wrapper = new JPanel(new BorderLayout(0, 8)) {

            @Override
            protected void paintComponent(Graphics g) {

                Graphics2D g2 = (Graphics2D) g.create();

                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                Shape shape = new RoundRectangle2D.Double(
                        0.5,
                        0.5,
                        getWidth() - 1,
                        getHeight() - 1,
                        12,
                        12
                );

                g2.setColor(Theme.CARD);
                g2.fill(shape);

                g2.setColor(Theme.CARD_BORDER);
                g2.draw(shape);

                g2.dispose();
            }
        };

        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel note = new JLabel(
                "<html><b>IMPORTANT:</b> A request is granted only if the system stays safe.</html>"
        );

        note.setFont(Theme.font(Font.PLAIN, 12f));
        note.setForeground(WARNING_TEXT);
        note.setOpaque(true);
        note.setBackground(WARNING_BG);

        note.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(
                                WARNING_BORDER,
                                1,
                                true
                        ),
                        new EmptyBorder(9, 12, 9, 12)
                )
        );

        wrapper.add(note, BorderLayout.NORTH);

        JPanel row = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 8, 4)
        );

        row.setOpaque(false);

        row.add(smallLabel("Process"));

        requestProcessSpinner.setPreferredSize(
                new Dimension(70, 28)
        );

        row.add(requestProcessSpinner);
        row.add(requestFieldsPanel);

        row.add(button(
                "Submit Request",
                SUBMIT_COLOR,
                this::submitRequest
        ));

        row.add(button(
                "Clear",
                CLEAR_COLOR,
                this::clearRequest
        ));

        wrapper.add(row, BorderLayout.CENTER);

        return wrapper;
    }

    // ----------------------------- ACTIONS --------------------------------

    private void generateTables() {

        Integer processes = (Integer) processSpinner.getValue();
        Integer resources = (Integer) resourceSpinner.getValue();

        if (processes == null || resources == null) {
            clearTables();
            return;
        }

        rebuildTables(processes, resources, true);
    }

    private void clearTables() {

        maxTable.setModel(new DefaultTableModel());
        allocationTable.setModel(new DefaultTableModel());
        availableTable.setModel(new DefaultTableModel());
        needTable.setModel(new DefaultTableModel());

        buildRequestFields(0, 0);

        algorithm = null;

        clearResults();
    }

    private void rebuildTables(
            int processes,
            int resources,
            boolean blank
    ) {

        maxTable.setModel(
                resourceModel(processes, resources, blank)
        );

        allocationTable.setModel(
                resourceModel(processes, resources, blank)
        );

        availableTable.setModel(
                availableModel(resources, blank)
        );

        needTable.setModel(
                resourceModel(processes, resources, blank)
        );

        buildRequestFields(processes, resources);

        algorithm = null;

        clearResults();
    }

    private void findNeed() {
        calculateNeed();
    }

    private void runAlgorithm() {
        runSafetyCheck();
    }

    private void requestResources() {
        submitRequest();
    }

    public void reset() {

        commitTableEdits();

        processSpinner.setValue(null);
        resourceSpinner.setValue(null);

        clearTables();

        requestProcessSpinner.setValue(0);
    }

    public void shutDown() {
        live.shutdown();
    }

    private void buildRequestFields(
            int processes,
            int resources
    ) {

        requestProcessSpinner.setModel(
                new SpinnerNumberModel(
                        0,
                        0,
                        Math.max(0, processes - 1),
                        1
                )
        );

        requestFieldsPanel.removeAll();

        requestFields = new JTextField[resources];

        for (int j = 0; j < resources; j++) {

            JPanel field = new JPanel();

            field.setOpaque(false);
            field.setLayout(
                    new BoxLayout(field, BoxLayout.Y_AXIS)
            );

            JLabel label = new JLabel("R" + j);

            label.setFont(Theme.font(Font.PLAIN, 11f));
            label.setForeground(Theme.TEXT_MUTED);
            label.setAlignmentX(Component.CENTER_ALIGNMENT);

            JTextField input = new JTextField("0", 3);

            input.setFont(Theme.font(Font.PLAIN, 13f));
            input.setMaximumSize(new Dimension(60, 28));
            input.setHorizontalAlignment(JTextField.CENTER);

            requestFields[j] = input;

            field.add(label);
            field.add(Box.createVerticalStrut(3));
            field.add(input);

            requestFieldsPanel.add(field);
        }

        requestFieldsPanel.revalidate();
        requestFieldsPanel.repaint();
    }

    // ----------------------------- NEED CALCULATION -----------------------

    private void calculateNeed() {

        try {

            commitTableEdits();

            BankersAlgorithm current = readAlgorithmFromTables();

            syncFrom(current);
            updateNeedTable();

            checked = false;
            safe = false;

            sequence = null;
            step = -1;

            lastRequest = null;
            lastRequestProcess = -1;

            requestChangedData = false;
            preRequestSnapshot = null;

            sequencePanel.setVisible(false);

            setStatus(
                    "NEEDS FOUND",
                    Theme.TEXT,
                    IDLE_BG
            );

            String example = needData.length > 0
                    ? labels(needData[0])
                    : "\u2014";

            explain(
                    "Need = Maximum \u2212 Allocated. For P0: "
                            + example + "."
            );

            publish();

        } catch (Exception ex) {

            clearResults();
            showError(ex.getMessage());
        }
    }

    // ----------------------------- SAFETY CHECK ---------------------------

    private void runSafetyCheck() {

        try {

            commitTableEdits();

            BankersAlgorithm current = readAlgorithmFromTables();

            syncFrom(current);
            updateNeedTable();

            checked = true;
            safe = current.isSafe();

            sequence = safe
                    ? current.getSafeSequence()
                    : null;

            step = -1;

            lastRequest = null;
            lastRequestProcess = -1;

            requestChangedData = false;
            preRequestSnapshot = null;

            showSafetyResult();
            publish();

        } catch (Exception ex) {

            clearResults();
            showError(ex.getMessage());
        }
    }

    // ----------------------------- NEXT STEP ------------------------------

    private void nextStep() {

        if (!checked || !safe || sequence == null
                || sequence.length == 0) {

            explain(
                    "Run the algorithm first to find a safe order."
            );

            publish();
            return;
        }

        if (step >= sequence.length - 1) {

            showFinalResult();
            publish();
            return;
        }

        step++;

        showSequenceChips();

        int p = sequence[step];

        int[] before = workBefore(step);
        int[] after = workBefore(step + 1);

        String stepText =
                "<b>What happened:</b> Step " + (step + 1)
                        + " of " + sequence.length
                        + ": P" + p + " can finish. It returns "
                        + labels(allocationData[p])
                        + ", so Available becomes " + labels(after)
                        + ".<br><b>Why:</b> its Need "
                        + labels(needData[p])
                        + " fits Work " + labels(before)
                        + ".<br><b>Next:</b> use the remaining "
                        + "Available to find the next process.";

        if (step == sequence.length - 1) {

            setStatus(
                    "SAFE STATE",
                    Theme.SUCCESS_TEXT,
                    GOOD_BG
            );

            explain(
                    stepText
                            + " <b>All processes can finish: "
                            + sequenceText(sequence) + ".</b>"
            );

        } else {

            explain(stepText);
        }

        publish();
    }

    private void showFinalResult() {

        setStatus(
                "SAFE STATE",
                Theme.SUCCESS_TEXT,
                GOOD_BG
        );

        explain(
                "<b>All processes can finish: "
                        + sequenceText(sequence)
                        + ".</b> The system is safe. "
                        + "This was only a check, so Available is unchanged."
        );
    }

    // ----------------------------- TABLE EDITS ----------------------------

    private void commitTableEdits() {

        JTable[] tables = {
                maxTable,
                allocationTable,
                availableTable,
                needTable
        };

        for (JTable table : tables) {

            if (table.isEditing()) {

                TableCellEditor editor = table.getCellEditor();

                if (editor == null || !editor.stopCellEditing()) {

                    if (editor != null) {
                        editor.cancelCellEditing();
                    }
                }
            }
        }
    }

    // ----------------------------- RESOURCE REQUEST -----------------------

    private void submitRequest() {

        if (preRequestSnapshot == null) {
            preRequestSnapshot = captureRequestSnapshot();
        }

        try {

            commitTableEdits();

            BankersAlgorithm current = readAlgorithmFromTables();

            int process = (Integer) requestProcessSpinner.getValue();

            int[] request = new int[requestFields.length];

            for (int j = 0; j < request.length; j++) {
                request[j] = readInteger(
                        requestFields[j].getText()
                );
            }

            lastRequest = request;
            lastRequestProcess = process;

            int[][] need = current.getNeedMatrix();
            int[] available = current.getAvailableResources();

            for (int j = 0; j < request.length; j++) {

                if (request[j] > need[process][j]) {

                    deny(
                            "P" + process + " asked for R" + j
                                    + ": " + request[j]
                                    + ", but only needs "
                                    + need[process][j]
                                    + ". Nothing was given.",
                            current
                    );

                    return;
                }

                if (request[j] > available[j]) {

                    deny(
                            "P" + process + " asked for R" + j
                                    + ": " + request[j]
                                    + ", but only " + available[j]
                                    + " is free. Nothing was given.",
                            current
                    );

                    return;
                }
            }

            boolean granted = current.requestResources(
                    process,
                    request
            );

            syncFrom(current);
            updateNeedTable();

            checked = true;
            safe = granted;

            sequence = granted
                    ? current.getSafeSequence()
                    : null;

            step = -1;

            if (granted) {

                populateTables(
                        current.getMaxMatrix(),
                        current.getAllocationMatrix(),
                        current.getAvailableResources()
                );

                requestChangedData = true;

                setStatus(
                        "REQUEST GRANTED",
                        Theme.SUCCESS_TEXT,
                        GOOD_BG
                );

                explain(
                        "P" + process + " gets " + labels(request)
                                + ". The system stays safe: "
                                + sequenceText(sequence) + "."
                );

            } else {

                deny(
                        "P" + process
                                + "'s request leaves no safe order. "
                                + "Nothing was given.",
                        current
                );

                return;
            }

            publish();

        } catch (Exception ex) {

            clearResults();
            showError(ex.getMessage());
        }
    }

    private void deny(
            String reason,
            BankersAlgorithm current
    ) {

        syncFrom(current);
        updateNeedTable();

        checked = false;
        safe = false;

        sequence = null;
        step = -1;

        setStatus(
                "REQUEST DENIED",
                Theme.DANGER_TEXT,
                BAD_BG
        );

        explain(reason);
        publish();
    }

    // ----------------------------- CLEAR REQUEST --------------------------

    private void clearRequest() {

        for (JTextField field : requestFields) {
            field.setText("");
        }

        requestProcessSpinner.setValue(0);

        if (preRequestSnapshot != null) {

            if (requestChangedData) {

                RequestSnapshot saved = preRequestSnapshot;
                preRequestSnapshot = null;

                showCurrentSafetyResult(saved.step());

            } else {

                restoreRequestSnapshot();
            }
        }

        lastRequest = null;
        lastRequestProcess = -1;

        requestChangedData = false;

        publish();
    }

    private void toggleAdvanced() {

        boolean show = !advancedPanel.isVisible();

        advancedPanel.setVisible(show);

        advancedToggle.setText(
                show ? "Advanced  \u25b4" : "Advanced  \u25be"
        );

        revalidate();
        repaint();
    }

    // ----------------------------- CLEAR RESULTS --------------------------

    private void clearResults() {

        algorithm = null;

        maxData = null;
        allocationData = null;
        needData = null;
        availableData = null;

        checked = false;
        safe = false;

        sequence = null;
        step = -1;

        lastRequest = null;
        lastRequestProcess = -1;

        requestChangedData = false;
        preRequestSnapshot = null;

        sequencePanel.setVisible(false);

        setStatus(
                "",
                Theme.TEXT_MUTED,
                IDLE_BG
        );

        explain(
                "Choose how many processes and resources you need. "
                        + "Then fill in <b>Maximum</b> and <b>Allocated</b>, "
                        + "and use <b>Find Need</b> and <b>Run Algorithm</b>."
        );

        publish();
    }

    // ----------------------------- SNAPSHOTS -------------------------------

    private RequestSnapshot captureRequestSnapshot() {

        return new RequestSnapshot(
                statusLabel.getText(),
                statusLabel.getForeground(),
                statusLabel.getBackground(),
                explanation.getText(),
                checked,
                safe,
                sequence == null ? null : sequence.clone(),
                step
        );
    }

    private void restoreRequestSnapshot() {

        RequestSnapshot saved = preRequestSnapshot;

        statusLabel.setText(saved.status());
        statusLabel.setForeground(saved.foreground());
        statusLabel.setBackground(saved.background());

        statusLabel.setVisible(
                saved.status() != null && !saved.status().isEmpty()
        );

        explanation.setText(saved.explanation());

        checked = saved.checked();
        safe = saved.safe();

        sequence = saved.sequence() == null
                ? null
                : saved.sequence().clone();

        step = saved.step();

        preRequestSnapshot = null;
    }

    private void showCurrentSafetyResult(int previousStep) {

        try {

            commitTableEdits();

            BankersAlgorithm current = readAlgorithmFromTables();

            checked = true;
            safe = current.isSafe();

            sequence = safe
                    ? current.getSafeSequence()
                    : null;

            step = previousStep >= 0 && sequence != null
                    ? Math.min(previousStep, sequence.length - 1)
                    : -1;

            showSafetyResult();

        } catch (Exception ex) {

            clearResults();
            showError(ex.getMessage());
        }
    }

    // ----------------------------- SAFETY RESULT --------------------------

    private void showSafetyResult() {

        if (safe && sequence != null) {

            setStatus(
                    "SAFE STATE",
                    Theme.SUCCESS_TEXT,
                    GOOD_BG
            );

            showSequenceChips();

            explain(
                    "<b>What happened:</b> the state is safe. Finish order: "
                            + sequenceText(sequence)
                            + ".<br><b>Why:</b> Each Need fits Available at "
                            + "some step, so every process can finish and return "
                            + "what it holds."
                            + "<br><b>Next:</b> press <b>Next Step</b> "
                            + "to watch each return."
            );

        } else {

            setStatus(
                    "UNSAFE STATE",
                    Theme.DANGER_TEXT,
                    BAD_BG
            );

            sequencePanel.setVisible(false);

            explain(
                    "<b>What happened:</b> the state is unsafe. "
                            + firstBlockedProcess()
                            + " cannot start with Available "
                            + labels(availableData)
                            + ".<br><b>Why:</b> No order lets every process finish: "
                            + needSummary(needData)
                            + ".<br><b>Next:</b> change the tables and run "
                            + "the check again. A deadlock may happen later, "
                            + "but not now."
            );
        }
    }

    // ----------------------------- SEQUENCE CHIPS -------------------------

    private void showSequenceChips() {

        sequencePanel.removeAll();

        if (sequence == null) {

            sequencePanel.setVisible(false);
            return;
        }

        for (int i = 0; i < sequence.length; i++) {

            JLabel chip = new JLabel(
                    "P" + sequence[i]
            );

            chip.setFont(Theme.font(Font.BOLD, 12f));

            chip.setForeground(
                    i <= step
                            ? Theme.SUCCESS_TEXT
                            : Theme.TEXT_MUTED
            );

            chip.setOpaque(true);

            chip.setBackground(
                    i <= step
                            ? GOOD_BG
                            : Theme.CARD
            );

            chip.setBorder(
                    new EmptyBorder(4, 9, 4, 9)
            );

            sequencePanel.add(chip);

            if (i < sequence.length - 1) {

                JLabel arrow = new JLabel("\u2192");

                arrow.setFont(Theme.font(Font.PLAIN, 12f));
                arrow.setForeground(Theme.TEXT_MUTED);

                sequencePanel.add(arrow);
            }
        }

        sequencePanel.setVisible(true);
    }

    @SuppressWarnings("unused")
    private void updateSequenceChips() {
        showSequenceChips();
    }

    private String firstBlockedProcess() {

        if (needData == null || availableData == null) {
            return "No process";
        }

        for (int i = 0; i < needData.length; i++) {

            for (int j = 0;
                 j < availableData.length && j < needData[i].length;
                 j++) {

                if (needData[i][j] > availableData[j]) {
                    return "P" + i;
                }
            }
        }

        return "No process";
    }

    // ----------------------------- HELPERS --------------------------------

    private int[] workBefore(int count) {

        int[] work = availableData == null
                ? new int[0]
                : availableData.clone();

        if (sequence == null || allocationData == null) {
            return work;
        }

        for (int i = 0;
             i < count && i < sequence.length;
             i++) {

            for (int j = 0; j < work.length; j++) {

                work[j] += allocationData[sequence[i]][j];
            }
        }

        return work;
    }

    private void syncFrom(BankersAlgorithm current) {

        maxData = current.getMaxMatrix();
        allocationData = current.getAllocationMatrix();
        needData = current.getNeedMatrix();
        availableData = current.getAvailableResources();
    }

    private void populateTables(
            int[][] max,
            int[][] allocation,
            int[] available
    ) {

        for (int i = 0; i < max.length; i++) {

            for (int j = 0; j < available.length; j++) {

                maxTable.setValueAt(max[i][j], i, j + 1);

                allocationTable.setValueAt(
                        allocation[i][j],
                        i,
                        j + 1
                );
            }
        }

        for (int j = 0; j < available.length; j++) {

            availableTable.setValueAt(
                    available[j],
                    0,
                    j
            );
        }
    }

    private void updateNeedTable() {

        if (needData == null) {
            return;
        }

        for (int i = 0; i < needData.length; i++) {

            for (int j = 0; j < needData[i].length; j++) {

                needTable.setValueAt(
                        needData[i][j],
                        i,
                        j + 1
                );
            }
        }
    }

    // ----------------------------- ALGORITHM INPUT ------------------------

    private BankersAlgorithm readAlgorithmFromTables() {

        Integer processCount =
                (Integer) processSpinner.getValue();

        Integer resourceCount =
                (Integer) resourceSpinner.getValue();

        if (processCount == null || resourceCount == null) {

            throw new IllegalArgumentException(
                    "Choose how many processes and resources you need first."
            );
        }

        int processes = processCount;
        int resources = resourceCount;

        int[][] max = readMatrix(
                maxTable,
                processes,
                resources
        );

        int[][] allocation = readMatrix(
                allocationTable,
                processes,
                resources
        );

        int[] available = readAvailable(resources);

        BankersAlgorithm current =
                new BankersAlgorithm(processes, resources);

        current.setData(
                max,
                allocation,
                available
        );

        algorithm = current;

        return current;
    }

    private int[][] readMatrix(
            JTable table,
            int processes,
            int resources
    ) {

        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }

        int[][] matrix = new int[processes][resources];

        for (int i = 0; i < processes; i++) {

            for (int j = 0; j < resources; j++) {

                matrix[i][j] = readInteger(
                        table.getValueAt(i, j + 1)
                );
            }
        }

        return matrix;
    }

    private int[] readAvailable(int resources) {

        if (availableTable.isEditing()) {
            availableTable.getCellEditor().stopCellEditing();
        }

        int[] result = new int[resources];

        for (int j = 0; j < resources; j++) {

            result[j] = readInteger(
                    availableTable.getValueAt(0, j)
            );
        }

        return result;
    }

    private int readInteger(Object value) {

        if (value == null || value.toString().trim().isEmpty()) {
            return 0;
        }

        try {

            int number = Integer.parseInt(
                    value.toString().trim()
            );

            if (number < 0) {

                throw new IllegalArgumentException(
                        "Resource values cannot be negative."
                );
            }

            return number;

        } catch (NumberFormatException ex) {

            throw new IllegalArgumentException(
                    "Use whole numbers only in the tables."
            );
        }
    }

    // ----------------------------- PUBLISH GRAPH --------------------------

    private void publish() {

        int n = needData == null
                ? 0
                : needData.length;

        int r = availableData == null
                ? 0
                : availableData.length;

        boolean[] finished = new boolean[n];

        int current = -1;

        if (checked && safe && sequence != null
                && step >= 0 && step < sequence.length) {

            for (int i = 0; i <= step; i++) {

                finished[sequence[i]] = true;
            }

            current = sequence[step];
        }

        int[] total = new int[r];

        if (allocationData != null) {

            for (int[] row : allocationData) {

                for (int j = 0;
                     j < r && row != null && j < row.length;
                     j++) {

                    total[j] += row[j];
                }
            }
        }

        if (availableData != null) {

            for (int j = 0; j < r; j++) {

                total[j] += availableData[j];
            }
        }

        BankersGraphPanel.View view =
                new BankersGraphPanel.View(
                        allocationData == null
                                ? new int[0][0]
                                : allocationData,

                        needData == null
                                ? new int[0][0]
                                : needData,

                        availableData == null
                                ? new int[0]
                                : availableData,

                        total,

                        lastRequest,
                        lastRequestProcess,
                        current,
                        finished
                );

        graph.setView(view);

        nextButton.setEnabled(
                checked
                        && safe
                        && sequence != null
                        && step < sequence.length - 1
        );

        maxTable.repaint();
        allocationTable.repaint();
        needTable.repaint();
    }

    // ----------------------------- STATUS ---------------------------------

    private void setStatus(
            String text,
            Color foreground,
            Color background
    ) {

        statusLabel.setText(text);
        statusLabel.setForeground(foreground);
        statusLabel.setBackground(background);

        statusLabel.setVisible(
                text != null && !text.isEmpty()
        );
    }

    private void explain(String html) {

        explanation.setText(
                "<html><div style='width:1040px'>"
                        + html
                        + "</div></html>"
        );
    }

    // ----------------------------- TEXT HELPERS ---------------------------

    private static String labels(int[] values) {

        if (values == null || values.length == 0) {
            return "\u2014";
        }

        StringBuilder text = new StringBuilder();

        for (int i = 0; i < values.length; i++) {

            if (i > 0) {
                text.append(", ");
            }

            text.append("R")
                    .append(i)
                    .append(": ")
                    .append(values[i]);
        }

        return text.toString();
    }

    private static String needSummary(int[][] need) {

        if (need == null || need.length == 0) {
            return "Need is \u2014";
        }

        StringBuilder text = new StringBuilder("Need is ");

        for (int i = 0; i < need.length; i++) {

            if (i > 0) {
                text.append("; ");
            }

            text.append("P")
                    .append(i)
                    .append(" ")
                    .append(labels(need[i]));
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

    // ----------------------------- TABLE WIDGETS --------------------------

    private JTable makeTable() {

        JTable table = new JTable();

        table.setRowHeight(26);
        table.setFont(Theme.font(Font.PLAIN, 13f));
        table.setForeground(Theme.TEXT);
        table.setGridColor(Theme.CARD_BORDER);

        table.setSelectionBackground(CURRENT_BG);
        table.setSelectionForeground(Theme.TEXT);

        table.setDefaultRenderer(
                Object.class,
                new ValueRenderer()
        );

        table.getTableHeader().setDefaultRenderer(
                new HeaderRenderer()
        );

        table.getTableHeader().setReorderingAllowed(false);

        return table;
    }

    private JPanel matrixCard(
            String title,
            String hint,
            JTable table
    ) {

        JPanel card = new JPanel(new BorderLayout(0, 6)) {

            @Override
            protected void paintComponent(Graphics g) {

                Graphics2D g2 = (Graphics2D) g.create();

                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                Shape shape = new RoundRectangle2D.Double(
                        0.5,
                        0.5,
                        getWidth() - 1,
                        getHeight() - 1,
                        12,
                        12
                );

                g2.setColor(Color.WHITE);
                g2.fill(shape);

                g2.setColor(Theme.CARD_BORDER);
                g2.draw(shape);

                g2.dispose();
            }
        };

        card.setOpaque(false);
        card.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel head = new JPanel();

        head.setOpaque(false);
        head.setLayout(
                new BoxLayout(head, BoxLayout.Y_AXIS)
        );

        JLabel titleLabel = new JLabel(title);

        titleLabel.setFont(Theme.font(Font.BOLD, 14f));
        titleLabel.setForeground(Theme.TEXT);

        JLabel hintLabel = new JLabel(hint);

        hintLabel.setFont(Theme.font(Font.PLAIN, 11f));
        hintLabel.setForeground(Theme.TEXT_MUTED);

        head.add(titleLabel);
        head.add(Box.createVerticalStrut(2));
        head.add(hintLabel);

        JScrollPane scroll = new JScrollPane(table);

        scroll.setBorder(
                new LineBorder(Theme.CARD_BORDER)
        );

        scroll.getViewport().setBackground(Color.WHITE);

        card.add(head, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);

        return card;
    }

    // ----------------------------- TABLE MODELS ---------------------------

    private static DefaultTableModel resourceModel(
            int processes,
            int resources
    ) {

        return resourceModel(
                processes,
                resources,
                false
        );
    }

    private static DefaultTableModel resourceModel(
            int processes,
            int resources,
            boolean blank
    ) {

        String[] columns = new String[resources + 1];

        columns[0] = "Process";

        for (int j = 0; j < resources; j++) {
            columns[j + 1] = "R" + j;
        }

        DefaultTableModel model =
                new DefaultTableModel(columns, 0) {

                    @Override
                    public boolean isCellEditable(
                            int row,
                            int column
                    ) {
                        return column > 0;
                    }
                };

        Object value = blank ? "" : 0;

        for (int i = 0; i < processes; i++) {

            Object[] row = new Object[resources + 1];

            row[0] = "P" + i;

            Arrays.fill(
                    row,
                    1,
                    row.length,
                    value
            );

            model.addRow(row);
        }

        return model;
    }

    private static DefaultTableModel availableModel(
            int resources
    ) {

        return availableModel(
                resources,
                false
        );
    }

    private static DefaultTableModel availableModel(
            int resources,
            boolean blank
    ) {

        String[] columns = new String[resources];

        for (int j = 0; j < resources; j++) {
            columns[j] = "R" + j;
        }

        DefaultTableModel model =
                new DefaultTableModel(columns, 0);

        Object[] row = new Object[resources];

        Arrays.fill(
                row,
                blank ? "" : 0
        );

        model.addRow(row);

        return model;
    }

    // ----------------------------- RENDERERS ------------------------------

    private class ValueRenderer
            extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean selected,
                boolean focused,
                int row,
                int column
        ) {

            super.getTableCellRendererComponent(
                    table,
                    value,
                    selected,
                    focused,
                    row,
                    column
            );

            setHorizontalAlignment(SwingConstants.CENTER);

            if (!selected
                    && checked
                    && safe
                    && sequence != null
                    && step >= 0
                    && step < sequence.length
                    && row == sequence[step]
                    && table != availableTable) {

                setBackground(CURRENT_BG);
                setForeground(ACCENT);

            } else if (!selected) {

                setBackground(Color.WHITE);
                setForeground(Theme.TEXT);
            }

            return this;
        }
    }

    private static class HeaderRenderer
            extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean selected,
                boolean focused,
                int row,
                int column
        ) {

            super.getTableCellRendererComponent(
                    table,
                    value,
                    false,
                    false,
                    row,
                    column
            );

            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(Theme.font(Font.BOLD, 12f));

            setBackground(Theme.TABLE);
            setForeground(Theme.TEXT);

            return this;
        }
    }

    // ----------------------------- SPINNER MODEL --------------------------

    private static class CountModel
            extends AbstractSpinnerModel {

        private final int minimum;
        private final int maximum;

        private Integer value;

        CountModel(
                int minimum,
                int maximum
        ) {

            this.minimum = minimum;
            this.maximum = maximum;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public void setValue(Object newValue) {

            Integer next = parse(newValue);

            boolean changed =
                    (value == null) != (next == null)
                            || (value != null
                            && next != null
                            && value.intValue() != next.intValue());

            if (changed) {

                value = next;
                fireStateChanged();
            }
        }

        @Override
        public Object getNextValue() {

            int base = value == null
                    ? minimum
                    : value;

            return base >= maximum
                    ? null
                    : base + 1;
        }

        @Override
        public Object getPreviousValue() {

            int base = value == null
                    ? minimum
                    : value;

            return base <= minimum
                    ? null
                    : base - 1;
        }

        private Integer parse(Object newValue) {

            if (newValue == null) {
                return null;
            }

            try {

                int number = newValue instanceof Number n
                        ? n.intValue()
                        : Integer.parseInt(
                        newValue.toString().trim()
                );

                return Math.max(
                        minimum,
                        Math.min(maximum, number)
                );

            } catch (RuntimeException ex) {

                return value;
            }
        }
    }

    // ----------------------------- SMALL WIDGETS --------------------------

    private static JComponent separator() {

        JSeparator separator =
                new JSeparator(SwingConstants.VERTICAL);

        separator.setPreferredSize(
                new Dimension(1, 24)
        );

        return separator;
    }

    private static JPanel numberField(
            String text,
            JSpinner spinner
    ) {

        JPanel panel = new JPanel();

        panel.setOpaque(false);

        panel.setLayout(
                new BoxLayout(panel, BoxLayout.Y_AXIS)
        );

        JLabel label = new JLabel(text);

        label.setFont(Theme.font(Font.PLAIN, 11f));
        label.setForeground(Theme.TEXT_MUTED);

        spinner.setPreferredSize(
                new Dimension(72, 28)
        );

        panel.add(label);
        panel.add(spinner);

        return panel;
    }

    private static JLabel smallLabel(String text) {

        JLabel label = new JLabel(text);

        label.setFont(Theme.font(Font.PLAIN, 12f));
        label.setForeground(Theme.TEXT_MUTED);

        return label;
    }

    // ----------------------------- BUTTON STYLING -------------------------

    private static JButton button(
            String text,
            Color background,
            Runnable action
    ) {

        JButton button = new JButton(text);

        button.setFont(Theme.font(Font.BOLD, 13f));

        styleButton(button, background);

        button.addActionListener(e -> action.run());

        return button;
    }

    private static void styleButton(
            JButton button,
            Color background
    ) {

        button.setBackground(background);
        button.setForeground(Color.WHITE);

        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);

        button.setBorder(
                new EmptyBorder(8, 14, 8, 14)
        );

        button.setCursor(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        );
    }

    // ----------------------------- ERROR ----------------------------------

    private void showError(String message) {

        JOptionPane.showMessageDialog(
                this,
                message,
                "Input Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    // ----------------------------- MAIN -----------------------------------

    public static void main(String[] args) {

        try {

            FlatLightLaf.setup();

            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 12);
            UIManager.put("TextComponent.arc", 12);

        } catch (Exception ex) {

            ex.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {

            JFrame frame = new JFrame(
                    "Banker's Algorithm Simulator"
            );

            frame.setDefaultCloseOperation(
                    JFrame.EXIT_ON_CLOSE
            );

            frame.setContentPane(
                    new BankersGUI()
            );

            frame.setSize(1280, 900);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
