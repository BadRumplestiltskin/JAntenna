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
    private final JLabel               destinationHint;
    private final JLabel               inputError;
    private final JLabel               outputError;
    private final JLabel               nameError;

    private static final String REPO_ROOT_TOOLTIP =
            "<html>Repository root. Baked files are written to "
            + "<b>&lt;root&gt;/&lt;group&gt;/&lt;name&gt;.gtable</b>,<br>"
            + "not directly into this folder.</html>";

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
        inputError = FormFields.errorLabel();
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(FormFields.withError(inputFolderField, inputError), gbc);
        JButton browseInputBtn = new JButton("Browse…");
        gbc.gridx = 2; gbc.weightx = 0;
        formPanel.add(browseInputBtn, gbc);

        // Row 1: Output repository root
        gbc.gridx = 0; gbc.gridy = 1;
        JLabel outputLabel = new JLabel("Output repo root:");
        outputLabel.setToolTipText(REPO_ROOT_TOOLTIP);
        formPanel.add(outputLabel, gbc);
        outputFolderField = new JTextField(30);
        outputFolderField.setToolTipText(REPO_ROOT_TOOLTIP);
        outputError = FormFields.errorLabel();
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(FormFields.withError(outputFolderField, outputError), gbc);
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
        nameError = FormFields.errorLabel();
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(FormFields.withError(combinedNameField, nameError), gbc);

        // Row 4: Bake All + progress
        bakeAllButton = new JButton("Bake All");
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        formPanel.add(bakeAllButton, gbc);
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        gbc.gridx = 1; gbc.gridy = 4; gbc.weightx = 1.0;
        formPanel.add(progressBar, gbc);

        // Row 5: live destination hint
        destinationHint = new JLabel(" ");
        destinationHint.setFont(destinationHint.getFont().deriveFont(Font.ITALIC));
        gbc.gridx = 1; gbc.gridy = 5; gbc.weightx = 1.0;
        formPanel.add(destinationHint, gbc);

        add(formPanel, BorderLayout.NORTH);

        // Center: results table
        tableModel = new BatchResultTableModel();
        JTable table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(50);
        table.getColumnModel().getColumn(2).setPreferredWidth(400);
        // Destination paths outrun the column, so carry the full text in a tooltip.
        table.getColumnModel().getColumn(2).setCellRenderer(new TooltipCellRenderer());
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Actions
        // Button state is derived from the form, never toggled ad hoc at submit time.
        Runnable revalidate = () -> { updateDestinationHint(); updateBakeEnabled(); };
        FormFields.onTextChange(inputFolderField,  revalidate);
        FormFields.onTextChange(outputFolderField, revalidate);
        FormFields.onTextChange(groupField,        revalidate);
        FormFields.onTextChange(combinedNameField, revalidate);

        // Errors appear on blur rather than while the user is still typing.
        FormFields.onBlur(inputFolderField,  () -> validateForm(true));
        FormFields.onBlur(outputFolderField, () -> validateForm(true));
        FormFields.onBlur(combinedNameField, () -> validateForm(true));

        updateBakeEnabled();

        browseInputBtn.addActionListener(e  -> browseFolderInto(inputFolderField, true));
        browseOutputBtn.addActionListener(e -> browseFolderInto(outputFolderField, false));
        bakeAllButton.addActionListener(e   -> startBatchBake());
    }

    /**
     * Checks the form and, when {@code showErrors} is set, writes the reason
     * under each offending field.
     *
     * @return true when the form is ready to bake
     */
    private boolean validateForm(boolean showErrors) {
        String input  = inputFolderField.getText().trim();
        String output = outputFolderField.getText().trim();
        String name   = combinedNameField.getText().trim();

        String inputMsg = input.isEmpty()
                ? "Select the folder holding the .voa/.13/.t13 sources."
                : Files.isDirectory(Path.of(input)) ? null : "Not a folder: " + input;
        String outputMsg = output.isEmpty()
                ? "Select the repository root to write into."
                : null;
        String nameMsg = name.isEmpty() && inputFolderNameUnavailable()
                ? "Enter a name for the combined table."
                : null;

        if (showErrors) {
            FormFields.setError(inputError,  inputMsg);
            FormFields.setError(outputError, outputMsg);
            FormFields.setError(nameError,   nameMsg);
        }
        return inputMsg == null && outputMsg == null && nameMsg == null;
    }

    /** The combined name falls back to the input folder's name when left blank. */
    private boolean inputFolderNameUnavailable() {
        String input = inputFolderField.getText().trim();
        return input.isEmpty() || Path.of(input).getFileName() == null;
    }

    /** Keeps the Bake button in step with form validity. */
    private void updateBakeEnabled() {
        bakeAllButton.setEnabled(validateForm(false));
    }

    /** Shows the exact path the combined table will be written to. */
    private void updateDestinationHint() {
        String root = outputFolderField.getText().trim();
        if (root.isEmpty()) {
            destinationHint.setText(" ");
            return;
        }
        String group = groupField.getText().trim();
        if (group.isEmpty()) group = "user";
        String name = combinedNameField.getText().trim();
        if (name.isEmpty()) name = "<combined name>";
        destinationHint.setText("Writes to: "
                + Path.of(root).resolve(group).resolve(name + ".gtable"));
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

        // Field-level problems are already shown inline; this is the last guard
        // for the keyboard path that can fire the button while it is enabled.
        if (!validateForm(true)) return;
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
        progressBar.setString("0 of " + sourceFiles.size());

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

    /** Renders a cell normally but exposes the untruncated text on hover. */
    private static final class TooltipCellRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            String text = value == null ? null : value.toString();
            setToolTipText(text == null || text.isBlank() ? null : text);
            return c;
        }
    }
}
