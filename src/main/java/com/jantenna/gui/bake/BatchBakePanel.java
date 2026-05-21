package com.jantenna.gui.bake;

import com.jantenna.gui.AppPreferences;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@SuppressWarnings("serial")
public class BatchBakePanel extends JPanel {

    private final JTextField           inputFolderField;
    private final JTextField           outputFolderField;
    private final JTextField           groupField;
    private final JTextField           combinedNameField;
    private final JButton              bakeAllButton;
    private final JProgressBar         progressBar;
    private final BatchResultTableModel tableModel;

    public BatchBakePanel() {
        super(new BorderLayout());

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        // Row 0: Input folder
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        formPanel.add(new JLabel("Input folder:"), gbc);
        inputFolderField = new JTextField(30);
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(inputFolderField, gbc);
        JButton browseInputBtn = new JButton("Browse…");
        gbc.gridx = 2; gbc.weightx = 0;
        formPanel.add(browseInputBtn, gbc);

        // Row 1: Output folder
        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Output folder:"), gbc);
        outputFolderField = new JTextField(30);
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(outputFolderField, gbc);
        JButton browseOutputBtn = new JButton("Browse…");
        gbc.gridx = 2; gbc.weightx = 0;
        formPanel.add(browseOutputBtn, gbc);

        // Row 2: Group
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Group:"), gbc);
        groupField = new JTextField("user", 30);
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(groupField, gbc);

        // Row 3: Combined type-13 output name
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        formPanel.add(new JLabel("Combined name:"), gbc);
        combinedNameField = new JTextField(30);
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(combinedNameField, gbc);

        // Row 4: Bake All + progress
        bakeAllButton = new JButton("Bake All");
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        formPanel.add(bakeAllButton, gbc);
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        gbc.gridx = 1; gbc.gridy = 4; gbc.weightx = 1.0;
        formPanel.add(progressBar, gbc);

        add(formPanel, BorderLayout.NORTH);

        // Center: results table
        tableModel = new BatchResultTableModel();
        JTable table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(50);
        table.getColumnModel().getColumn(2).setPreferredWidth(400);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Actions
        browseInputBtn.addActionListener(e  -> browseFolderInto(inputFolderField, true));
        browseOutputBtn.addActionListener(e -> browseFolderInto(outputFolderField, false));
        bakeAllButton.addActionListener(e   -> startBatchBake());
    }

    private void browseFolderInto(JTextField field, boolean isInput) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String pref = isInput ? AppPreferences.getLastInputDir() : AppPreferences.getLastOutputDir();
        if (pref != null) chooser.setCurrentDirectory(new File(pref));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File chosen = chooser.getSelectedFile();
            field.setText(chosen.getAbsolutePath());
            if (isInput) {
                AppPreferences.setLastInputDir(chosen.getAbsolutePath());
                if (combinedNameField.getText().isBlank()) {
                    combinedNameField.setText(chosen.getName());
                }
            } else {
                AppPreferences.setLastOutputDir(chosen.getAbsolutePath());
            }
        }
    }

    private void startBatchBake() {
        String inputText    = inputFolderField.getText().trim();
        String outputText   = outputFolderField.getText().trim();
        String group        = groupField.getText().trim();
        String combinedName = combinedNameField.getText().trim();

        if (inputText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an input folder.", "Missing Input", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (outputText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an output folder.", "Missing Output", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (group.isEmpty()) group = "user";
        Path inputFolder = Path.of(inputText);
        Path repoRoot    = Path.of(outputText);
        if (combinedName.isEmpty()) combinedName = inputFolder.getFileName().toString();

        List<Path> sourceFiles;
        try (Stream<Path> stream = Files.list(inputFolder)) {
            sourceFiles = stream
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".voa") || name.endsWith(".13") || name.endsWith(".t13");
                    })
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Cannot read input folder:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (sourceFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No .voa/.13/.t13 files found in: " + inputText,
                    "Nothing to Bake", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        tableModel.clear();
        progressBar.setValue(0);
        progressBar.setMaximum(100);

        for (Path f : sourceFiles) {
            tableModel.addRow(f.getFileName().toString(), "⏳", "Queued");
        }

        bakeAllButton.setEnabled(false);

        BatchBakeWorker worker = new BatchBakeWorker(
                sourceFiles, repoRoot, group, combinedName, bakeAllButton, progressBar, tableModel);
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });
        worker.execute();
    }

    public void triggerBrowseInputFolder() {
        browseFolderInto(inputFolderField, true);
    }
}
