package com.jantenna.gui.bake;

import com.jantenna.gui.AppPreferences;
import com.jantenna.gui.viewer.PatternViewerPanel;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;

@SuppressWarnings("serial")
public class SingleFileBakePanel extends JPanel {

    private final JTextField        inputFileField;
    private final JTextField        outputFolderField;
    private final JTextField        groupField;
    private final JTextField        nameField;
    private final JButton           bakeButton;
    private final JTextArea         logArea;
    private final JPanel            bottomPanel;
    private final PatternViewerPanel viewerPanel;

    private final JLabel            inputError;
    private final JLabel            outputError;
    private final JLabel            nameError;

    private Path lastBakedGtable;

    private static final String REPO_ROOT_TOOLTIP =
            "<html>Repository root. Baked files are written to "
            + "<b>&lt;root&gt;/&lt;group&gt;/&lt;name&gt;.gtable</b>,<br>"
            + "not directly into this folder.</html>";

    public SingleFileBakePanel(PatternViewerPanel viewerPanel) {
        super(new BorderLayout());
        this.viewerPanel = viewerPanel;

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        // Row 0: Input file
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        formPanel.add(new JLabel("Input file:"), gbc);
        inputFileField = new JTextField(30);
        inputError = FormFields.errorLabel();
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(FormFields.withError(inputFileField, inputError), gbc);
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

        // Row 3: Name
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Name:"), gbc);
        nameField = new JTextField(30);
        nameError = FormFields.errorLabel();
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(FormFields.withError(nameField, nameError), gbc);

        add(formPanel, BorderLayout.NORTH);

        // Center: log area + bake button
        logArea = new JTextArea(8, 40);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        bottomPanel = new JPanel(new BorderLayout());
        bakeButton = new JButton("Bake");
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonBar.add(bakeButton);
        bottomPanel.add(buttonBar, BorderLayout.NORTH);
        bottomPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.CENTER);

        // Button state is derived from the form, never toggled ad hoc at submit time.
        Runnable revalidate = this::updateBakeEnabled;
        FormFields.onTextChange(inputFileField,    revalidate);
        FormFields.onTextChange(outputFolderField, revalidate);
        FormFields.onTextChange(nameField,         revalidate);

        // Errors appear on blur rather than while the user is still typing.
        FormFields.onBlur(inputFileField,    () -> validateForm(true));
        FormFields.onBlur(outputFolderField, () -> validateForm(true));
        FormFields.onBlur(nameField,         () -> validateForm(true));

        updateBakeEnabled();

        // Actions
        browseInputBtn.addActionListener(e -> browseInputFile());
        browseOutputBtn.addActionListener(e -> browseOutputFolder());
        bakeButton.addActionListener(e -> startBake());
    }

    private void browseInputFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Antenna source files (*.voa, *.13, *.t13)", "voa", "13", "t13"));
        String lastDir = AppPreferences.getLastInputDir();
        if (lastDir != null) chooser.setCurrentDirectory(new File(lastDir));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File chosen = chooser.getSelectedFile();
            inputFileField.setText(chosen.getAbsolutePath());
            AppPreferences.setLastInputDir(chosen.getParent());

            String filename = chosen.getName();
            int dot = filename.lastIndexOf('.');
            String derivedName = dot > 0 ? filename.substring(0, dot) : filename;
            nameField.setText(derivedName);
        }
    }

    private void browseOutputFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String lastDir = AppPreferences.getLastOutputDir();
        if (lastDir != null) chooser.setCurrentDirectory(new File(lastDir));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File chosen = chooser.getSelectedFile();
            outputFolderField.setText(chosen.getAbsolutePath());
            AppPreferences.setLastOutputDir(chosen.getAbsolutePath());
        }
    }

    private void startBake() {
        String inputText  = inputFileField.getText().trim();
        String outputText = outputFolderField.getText().trim();
        String group      = groupField.getText().trim();
        String name       = nameField.getText().trim();

        // Field-level problems are already shown inline; this is the last guard
        // for the keyboard path that can fire the button while it is enabled.
        if (!validateForm(true)) return;
        if (group.isEmpty()) group = "user";

        Path source   = Path.of(inputText);
        Path repoRoot = Path.of(outputText);

        logArea.setText("");
        bakeButton.setEnabled(false);
        removeViewPatternButton();

        BakeWorker worker = new BakeWorker(
            source, repoRoot, group, name,
            this::appendLog,
            gtable -> {
                updateBakeEnabled();
                lastBakedGtable = gtable;
                appendLog("✓ Bake successful: " + gtable);
                addViewPatternButton(gtable);
            },
            ex -> {
                updateBakeEnabled();
                appendLog("✗ Bake failed: " + ex.getMessage());
            }
        );
        worker.execute();
    }

    /**
     * Checks the form and, when {@code showErrors} is set, writes the reason
     * under each offending field.
     *
     * @return true when the form is ready to bake
     */
    private boolean validateForm(boolean showErrors) {
        String input  = inputFileField.getText().trim();
        String output = outputFolderField.getText().trim();
        String name   = nameField.getText().trim();

        String inputMsg = input.isEmpty()
                ? "Select a .voa/.13/.t13 source file."
                : java.nio.file.Files.isRegularFile(Path.of(input)) ? null : "Not a file: " + input;
        String outputMsg = output.isEmpty()
                ? "Select the repository root to write into."
                : null;
        String nameMsg = name.isEmpty() ? "Enter a name for the baked table." : null;

        if (showErrors) {
            FormFields.setError(inputError,  inputMsg);
            FormFields.setError(outputError, outputMsg);
            FormFields.setError(nameError,   nameMsg);
        }
        return inputMsg == null && outputMsg == null && nameMsg == null;
    }

    /** Keeps the Bake button in step with form validity. */
    private void updateBakeEnabled() {
        bakeButton.setEnabled(validateForm(false));
    }

    private void appendLog(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void removeViewPatternButton() {
        Component[] comps = ((JPanel) bottomPanel.getComponent(0)).getComponents();
        for (Component c : comps) {
            if (c instanceof JButton btn && "View Pattern".equals(btn.getText())) {
                ((JPanel) bottomPanel.getComponent(0)).remove(btn);
                ((JPanel) bottomPanel.getComponent(0)).revalidate();
                ((JPanel) bottomPanel.getComponent(0)).repaint();
                break;
            }
        }
    }

    private void addViewPatternButton(Path gtable) {
        JButton viewBtn = new JButton("View Pattern");
        viewBtn.addActionListener(e -> viewerPanel.openFile(gtable));
        JPanel buttonBar = (JPanel) bottomPanel.getComponent(0);
        buttonBar.add(viewBtn);
        buttonBar.revalidate();
        buttonBar.repaint();
    }

    public void setLogPanelVisible(boolean visible) {
        bottomPanel.setVisible(visible);
    }

    public void triggerBrowseInputFile() {
        browseInputFile();
    }
}
