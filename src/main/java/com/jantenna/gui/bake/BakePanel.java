package com.jantenna.gui.bake;

import com.jantenna.PathNames;
import com.jantenna.gui.AppPreferences;
import com.jantenna.gui.FileSystemTreePanel;
import com.jantenna.gui.viewer.PatternViewerPanel;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

/**
 * Unified bake panel: two side-by-side file-system trees (input multi-select,
 * output directory) plus a combined-name field and Bake button.
 *
 * Selecting one file bakes a single-frequency .gtable; selecting several files
 * merges them into one multi-frequency .gtable via BatchBakeWorker.
 */
@SuppressWarnings("serial")
public class BakePanel extends JPanel {

    private static final String DEFAULT_GROUP = "user";

    private final FileSystemTreePanel  inputTree;
    private final FileSystemTreePanel  outputTree;
    private final JTextField           combinedNameField;
    private final JButton              bakeButton;
    private final JProgressBar         progressBar;
    private final BatchResultTableModel tableModel;
    private final PatternViewerPanel   viewerPanel;

    public BakePanel(PatternViewerPanel viewerPanel) {
        super(new BorderLayout(4, 4));
        this.viewerPanel = viewerPanel;

        // --- Two trees side by side ---
        inputTree  = new FileSystemTreePanel("Input files (.voa / .13 / .t13)", false);
        outputTree = new FileSystemTreePanel("Output directory", true);

        String lastIn  = AppPreferences.getLastInputDir();
        String lastOut = AppPreferences.getLastOutputDir();
        if (lastIn  != null) inputTree.setRoot(new java.io.File(lastIn));
        if (lastOut != null) outputTree.setRoot(new java.io.File(lastOut));

        JSplitPane treeSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputTree, outputTree);
        treeSplit.setResizeWeight(0.5);
        add(treeSplit, BorderLayout.CENTER);

        // --- Bottom form ---
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));

        JPanel formRow = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        formRow.add(new JLabel("Combined name:"), gbc);
        combinedNameField = new JTextField(20);
        gbc.gridx = 1; gbc.weightx = 1.0;
        formRow.add(combinedNameField, gbc);

        bakeButton = new JButton("Bake");
        gbc.gridx = 2; gbc.weightx = 0;
        formRow.add(bakeButton, gbc);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        gbc.gridx = 3; gbc.weightx = 0.6;
        formRow.add(progressBar, gbc);

        bottomPanel.add(formRow, BorderLayout.NORTH);

        // Results table
        tableModel = new BatchResultTableModel();
        JTable table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(180);
        table.getColumnModel().getColumn(1).setPreferredWidth(40);
        table.getColumnModel().getColumn(2).setPreferredWidth(360);
        bottomPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        // Auto-derive combined name when input selection changes
        inputTree.addSelectionListener(e -> updateCombinedName());

        // When output directory is selected, persist preference
        outputTree.addSelectionListener(e -> {
            Path dir = outputTree.getSelectedDirectoryPath();
            if (dir != null) AppPreferences.setLastOutputDir(dir.toString());
        });

        bakeButton.addActionListener(ev -> startBake());
    }

    // -------------------------------------------------------------------------

    private void updateCombinedName() {
        List<Path> files = inputTree.getSelectedFilePaths();
        if (files.isEmpty()) return;
        if (combinedNameField.getText().isBlank() || combinedNameField.isFocusOwner()) return;
        String stem = PathNames.stem(files.get(0));
        combinedNameField.setText(stem);

        Path parent = files.get(0).getParent();
        if (parent != null) AppPreferences.setLastInputDir(parent.toString());
    }

    private void startBake() {
        List<Path> sources = inputTree.getSelectedFilePaths();
        if (sources.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Select at least one antenna source file in the left tree.",
                    "No Files Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path outputDir = outputTree.getSelectedDirectoryPath();
        if (outputDir == null) {
            JOptionPane.showMessageDialog(this,
                    "Select an output directory in the right tree.",
                    "No Output Directory", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String name = combinedNameField.getText().trim();
        if (name.isEmpty()) name = PathNames.stem(sources.get(0));

        // Populate results table
        tableModel.clear();
        progressBar.setValue(0);
        for (Path p : sources) {
            tableModel.addRow(p.getFileName().toString(), "⏳", "Queued");
        }

        bakeButton.setEnabled(false);
        final String finalName = name;
        final Path   repoRoot  = outputDir;

        BatchBakeWorker worker = new BatchBakeWorker(
                sources, repoRoot, DEFAULT_GROUP, finalName,
                bakeButton, progressBar, tableModel);
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });
        worker.execute();
    }

}
