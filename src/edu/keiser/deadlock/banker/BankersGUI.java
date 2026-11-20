
package edu.keiser.deadlock.banker;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;

public class BankersGUI extends JFrame {

    private final JSpinner processSpinner;
    private final JSpinner resourceSpinner;

    private JTable maxTable;
    private JTable allocationTable;
    private JTable availableTable;

    private JPanel tablesPanel;
    private JPanel resultsPanel;

    private BankersAlgorithm algorithm;

    private final Color PAGE_BACKGROUND = new Color(242, 244, 247);
    private final Color HEADER_COLOR = new Color(52, 73, 94);
    private final Color BUTTON_COLOR = new Color(52, 120, 190);
    private final Color TEXT_COLOR = new Color(45, 55, 65);
    private final Color BORDER_COLOR = new Color(220, 225, 230);

    public BankersGUI() {
        setTitle("Banker's Algorithm Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 850);
        setMinimumSize(new Dimension(800, 650));
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setBackground(PAGE_BACKGROUND);
        mainPanel.setBorder(new EmptyBorder(25, 35, 30, 35));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Banker's Algorithm Simulator");
        title.setFont(new Font("SansSerif", Font.BOLD, 27));
        title.setForeground(HEADER_COLOR);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        mainPanel.add(title);
        mainPanel.add(Box.createVerticalStrut(25));

        // Input configuration section
        JPanel configurationPanel = createSection("Input Configuration");
        configurationPanel.setLayout(new BorderLayout(15, 15));

        JPanel fieldsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        fieldsPanel.setOpaque(false);

        processSpinner = new JSpinner(
                new SpinnerNumberModel(5, 1, 20, 1)
        );

        resourceSpinner = new JSpinner(
                new SpinnerNumberModel(3, 1, 15, 1)
        );

        fieldsPanel.add(createSpinnerField("Number of Processes", processSpinner));
        fieldsPanel.add(createSpinnerField("Number of Resources", resourceSpinner));

        JPanel configurationButtons = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 10, 5)
        );
        configurationButtons.setOpaque(false);

        JButton loadDemoButton = createButton("Load Demo");
        JButton generateButton = createButton("Generate Tables");

        configurationButtons.add(loadDemoButton);
        configurationButtons.add(generateButton);

        configurationPanel.add(fieldsPanel, BorderLayout.CENTER);
        configurationPanel.add(configurationButtons, BorderLayout.SOUTH);

        mainPanel.add(configurationPanel);
        mainPanel.add(Box.createVerticalStrut(18));

        // Dynamic tables section
        tablesPanel = new JPanel();
        tablesPanel.setOpaque(false);
        tablesPanel.setLayout(new BoxLayout(tablesPanel, BoxLayout.Y_AXIS));

        mainPanel.add(tablesPanel);
        mainPanel.add(Box.createVerticalStrut(18));

        // Action buttons
        JPanel actionsPanel = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 15, 8)
        );
        actionsPanel.setOpaque(false);

        JButton findNeedButton = createButton("Find Need");
        JButton runButton = createButton("Run Algorithm");
        JButton resetButton = createButton("Reset");

        actionsPanel.add(findNeedButton);
        actionsPanel.add(runButton);
        actionsPanel.add(resetButton);

        mainPanel.add(actionsPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // Results section
        resultsPanel = createSection("Results");
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        mainPanel.add(resultsPanel);

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(PAGE_BACKGROUND);

        setContentPane(scrollPane);

        // Button actions
        generateButton.addActionListener(e -> generateTables());

        loadDemoButton.addActionListener(e -> loadDemo());

        findNeedButton.addActionListener(e -> findNeed());

        runButton.addActionListener(e -> runAlgorithm());

        resetButton.addActionListener(e -> reset());

        generateTables();
        clearResults();
    }

    private JPanel createSection(String heading) {
        JPanel panel = new JPanel();
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(16, 18, 16, 18)
        ));

        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1000));

        JLabel label = new JLabel(heading);
        label.setFont(new Font("SansSerif", Font.BOLD, 18));
        label.setForeground(HEADER_COLOR);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(label);
        return panel;
    }

    private JPanel createSpinnerField(String labelText, JSpinner spinner) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(labelText);
        label.setFont(new Font("SansSerif", Font.PLAIN, 13));
        label.setForeground(TEXT_COLOR);

        spinner.setPreferredSize(new Dimension(100, 32));
        spinner.setMaximumSize(new Dimension(130, 32));
        spinner.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(label);
        panel.add(Box.createVerticalStrut(6));
        panel.add(spinner);

        return panel;
    }

    private JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(BUTTON_COLOR);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setBorder(new EmptyBorder(10, 18, 10, 18));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void generateTables() {
        int processes = (Integer) processSpinner.getValue();
        int resources = (Integer) resourceSpinner.getValue();

        maxTable = createResourceTable(processes, resources);
        allocationTable = createResourceTable(processes, resources);
        availableTable = createAvailableTable(resources);

        tablesPanel.removeAll();

        tablesPanel.add(createTableSection("Maximum Claim Matrix", maxTable));
        tablesPanel.add(Box.createVerticalStrut(15));

        tablesPanel.add(createTableSection("Allocation Matrix", allocationTable));
        tablesPanel.add(Box.createVerticalStrut(15));

        tablesPanel.add(createTableSection("Available Resources", availableTable));

        algorithm = null;
        clearResults();

        tablesPanel.revalidate();
        tablesPanel.repaint();
    }

    private JTable createResourceTable(int processes, int resources) {
        String[] columns = new String[resources + 1];
        columns[0] = "Process";

        for (int j = 0; j < resources; j++) {
            columns[j + 1] = "R" + j;
        }

        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column > 0;
            }
        };

        for (int i = 0; i < processes; i++) {
            Object[] row = new Object[resources + 1];
            row[0] = "P" + i;

            for (int j = 1; j <= resources; j++) {
                row[j] = 0;
            }

            model.addRow(row);
        }

        return styleTable(new JTable(model));
    }

    private JTable createAvailableTable(int resources) {
        String[] columns = new String[resources];

        for (int j = 0; j < resources; j++) {
            columns[j] = "R" + j;
        }

        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };

        Object[] row = new Object[resources];

        for (int j = 0; j < resources; j++) {
            row[j] = 0;
        }

        model.addRow(row);

        return styleTable(new JTable(model));
    }

    private JTable styleTable(JTable table) {
        table.setRowHeight(30);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setForeground(TEXT_COLOR);
        table.setGridColor(BORDER_COLOR);
        table.setSelectionBackground(new Color(220, 235, 250));
        table.setSelectionForeground(TEXT_COLOR);

        table.getTableHeader().setBackground(HEADER_COLOR);
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setFont(
                new Font("SansSerif", Font.BOLD, 13)
        );
        table.getTableHeader().setReorderingAllowed(false);

        return table;
    }

    private JPanel createTableSection(String heading, JTable table) {
        JPanel section = new JPanel();
        section.setBackground(Color.WHITE);
        section.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(14, 18, 14, 18)
        ));

        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(heading);
        label.setFont(new Font("SansSerif", Font.BOLD, 16));
        label.setForeground(HEADER_COLOR);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(new LineBorder(BORDER_COLOR));
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        tableScroll.setPreferredSize(new Dimension(850, table.getRowHeight()
                * (table.getRowCount() + 1) + 3));
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 500));

        section.add(label);
        section.add(Box.createVerticalStrut(10));
        section.add(tableScroll);

        return section;
    }

    private void loadDemo() {
        processSpinner.setValue(5);
        resourceSpinner.setValue(3);

        generateTables();

        File demoFile = new File("src/Algorithms/banker-test.txt");

        if (!demoFile.exists()) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select banker-test.txt");

            int choice = chooser.showOpenDialog(this);

            if (choice != JFileChooser.APPROVE_OPTION) {
                return;
            }

            demoFile = chooser.getSelectedFile();
        }

        try {
            algorithm = new BankersAlgorithm(5, 3);
            algorithm.readInputFromFile(demoFile.getAbsolutePath());

            populateTables(
                    algorithm.getMaxMatrix(),
                    algorithm.getAllocationMatrix(),
                    algorithm.getAvailableResources()
            );

            JOptionPane.showMessageDialog(
                    this,
                    "Demo data loaded successfully!",
                    "Demo Loaded",
                    JOptionPane.INFORMATION_MESSAGE
            );

            clearResults();

        } catch (FileNotFoundException ex) {
            showError("The selected TXT file could not be found.");
        } catch (Exception ex) {
            showError("Could not load the demo: " + ex.getMessage());
            algorithm = null;
        }
    }

    private void populateTables(
            int[][] max,
            int[][] allocation,
            int[] available
    ) {
        for (int i = 0; i < max.length; i++) {
            for (int j = 0; j < available.length; j++) {
                maxTable.setValueAt(max[i][j], i, j + 1);
                allocationTable.setValueAt(allocation[i][j], i, j + 1);
            }
        }

        for (int j = 0; j < available.length; j++) {
            availableTable.setValueAt(available[j], 0, j);
        }
    }

    private int[][] readMatrix(JTable table, int processes, int resources) {
        table.getCellEditor();

        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }

        int[][] matrix = new int[processes][resources];

        for (int i = 0; i < processes; i++) {
            for (int j = 0; j < resources; j++) {
                matrix[i][j] = readInteger(table.getValueAt(i, j + 1));
            }
        }

        return matrix;
    }

    private int[] readAvailable() {
        if (availableTable.isEditing()) {
            availableTable.getCellEditor().stopCellEditing();
        }

        int resources = (Integer) resourceSpinner.getValue();
        int[] available = new int[resources];

        for (int j = 0; j < resources; j++) {
            available[j] = readInteger(availableTable.getValueAt(0, j));
        }

        return available;
    }

    private int readInteger(Object value) {
        if (value == null || value.toString().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Please fill in all table cells."
            );
        }

        try {
            int number = Integer.parseInt(value.toString().trim());

            if (number < 0) {
                throw new IllegalArgumentException(
                        "Resource values cannot be negative."
                );
            }

            return number;

        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Only whole numbers are allowed in the tables."
            );
        }
    }

    private BankersAlgorithm readAlgorithmFromTables() {
        int processes = (Integer) processSpinner.getValue();
        int resources = (Integer) resourceSpinner.getValue();

        int[][] max = readMatrix(maxTable, processes, resources);
        int[][] allocation = readMatrix(
                allocationTable, processes, resources
        );
        int[] available = readAvailable();

        BankersAlgorithm current =
                new BankersAlgorithm(processes, resources);

        current.setData(max, allocation, available);

        algorithm = current;
        return algorithm;
    }

    private void findNeed() {
        try {
            BankersAlgorithm current = readAlgorithmFromTables();
            int[][] need = current.getNeedMatrix();

            showNeedMatrix(need);

        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void runAlgorithm() {
        try {
            BankersAlgorithm current = readAlgorithmFromTables();

            int[][] need = current.getNeedMatrix();
            boolean safe = current.isSafe();

            showNeedMatrix(need);
            addResultSeparator();

            JLabel status = new JLabel(
                    safe ? "SAFE STATE" : "UNSAFE STATE"
            );

            status.setFont(new Font("SansSerif", Font.BOLD, 20));
            status.setAlignmentX(Component.LEFT_ALIGNMENT);

            if (safe) {
                status.setForeground(new Color(34, 139, 80));

                int[] sequence = current.getSafeSequence();
                StringBuilder text = new StringBuilder("Safe Sequence: ");

                for (int i = 0; i < sequence.length; i++) {
                    text.append("P").append(sequence[i]);

                    if (i < sequence.length - 1) {
                        text.append(" \u2192 ");
                    }
                }

                JLabel sequenceLabel = new JLabel(text.toString());
                sequenceLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
                sequenceLabel.setForeground(TEXT_COLOR);
                sequenceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

                resultsPanel.add(Box.createVerticalStrut(12));
                resultsPanel.add(status);
                resultsPanel.add(Box.createVerticalStrut(8));
                resultsPanel.add(sequenceLabel);

            } else {
                status.setForeground(new Color(190, 55, 55));

                JLabel message = new JLabel(
                        "No safe sequence exists for the current resource allocation."
                );
                message.setForeground(TEXT_COLOR);
                message.setAlignmentX(Component.LEFT_ALIGNMENT);

                resultsPanel.add(Box.createVerticalStrut(12));
                resultsPanel.add(status);
                resultsPanel.add(Box.createVerticalStrut(8));
                resultsPanel.add(message);
            }

            resultsPanel.revalidate();
            resultsPanel.repaint();

        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void showNeedMatrix(int[][] need) {
        removeResultContent();

        JLabel heading = new JLabel("Need Matrix");
        heading.setFont(new Font("SansSerif", Font.BOLD, 16));
        heading.setForeground(HEADER_COLOR);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTable needTable = createResourceTable(
                need.length,
                need[0].length
        );

        for (int i = 0; i < need.length; i++) {
            for (int j = 0; j < need[i].length; j++) {
                needTable.setValueAt(need[i][j], i, j + 1);
            }
        }

        needTable.setEnabled(false);

        JScrollPane scroll = new JScrollPane(needTable);
        scroll.setBorder(new LineBorder(BORDER_COLOR));
        scroll.setPreferredSize(new Dimension(850, 30 * (need.length + 1)));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 500));

        resultsPanel.add(Box.createVerticalStrut(12));
        resultsPanel.add(heading);
        resultsPanel.add(Box.createVerticalStrut(8));
        resultsPanel.add(scroll);

        resultsPanel.revalidate();
        resultsPanel.repaint();
    }

    private void addResultSeparator() {
        resultsPanel.add(Box.createVerticalStrut(15));
        resultsPanel.add(new JSeparator());
    }

    private void removeResultContent() {
        while (resultsPanel.getComponentCount() > 1) {
            resultsPanel.remove(1);
        }
    }

    private void clearResults() {
        removeResultContent();
        resultsPanel.revalidate();
        resultsPanel.repaint();
    }

    private void reset() {
        processSpinner.setValue(5);
        resourceSpinner.setValue(3);

        generateTables();
        clearResults();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Input Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    public static void main(String[] args) {

        try {
            FlatLightLaf.setup();

            UIManager.put("Button.arc", 15);
            UIManager.put("Component.arc", 15);
            UIManager.put("TextComponent.arc", 15);
            UIManager.put("ScrollBar.width", 12);

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            BankersGUI gui = new BankersGUI();
            gui.setVisible(true);
        });
    }
}
