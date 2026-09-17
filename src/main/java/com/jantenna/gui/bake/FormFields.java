package com.jantenna.gui.bake;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * Small helpers for inline form validation in the bake panels.
 *
 * <p>Errors belong next to the field that caused them, not in a modal dialog
 * raised after submit - the user should never have to hunt for which input was
 * rejected.</p>
 */
final class FormFields {

    private FormFields() {}

    /** Error text colour, readable against the default panel background. */
    private static final Color ERROR_FG = new Color(0xB3, 0x26, 0x1E);

    /**
     * Pairs {@code field} with {@code error} in a single component that can drop
     * into one grid cell: the field on top, its error message underneath.
     */
    static JComponent withError(JComponent field, JLabel error) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(field, BorderLayout.CENTER);
        wrapper.add(error, BorderLayout.SOUTH);
        return wrapper;
    }

    /** Creates the (initially blank) label that carries a field's error text. */
    static JLabel errorLabel() {
        JLabel label = new JLabel(" ");
        label.setForeground(ERROR_FG);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, label.getFont().getSize2D() - 1f));
        // Reserve the row from the start so showing an error never reflows the form.
        label.setPreferredSize(label.getPreferredSize());
        return label;
    }

    /**
     * Shows {@code message} under a field, or clears the error when
     * {@code message} is null. Returns true when the field is valid, so callers
     * can chain checks with {@code &}.
     */
    static boolean setError(JLabel error, String message) {
        error.setText(message == null ? " " : message);
        return message == null;
    }

    /** Runs {@code onChange} whenever the text of {@code field} changes. */
    static void onTextChange(JTextField field, Runnable onChange) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { onChange.run(); }
            @Override public void removeUpdate(DocumentEvent e)  { onChange.run(); }
            @Override public void changedUpdate(DocumentEvent e) { onChange.run(); }
        });
    }

    /** Runs {@code onBlur} when {@code field} loses focus - the usual moment to validate. */
    static void onBlur(JTextField field, Runnable onBlur) {
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { onBlur.run(); }
        });
    }
}
