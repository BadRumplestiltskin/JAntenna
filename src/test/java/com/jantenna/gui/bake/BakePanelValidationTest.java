package com.jantenna.gui.bake;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the bake panels gate their action button on form validity,
 * rather than accepting a click and then rejecting it with a dialog.
 *
 * <p>Skipped in a headless environment, where Swing components cannot be
 * realised.</p>
 */
@DisabledIf("java.awt.GraphicsEnvironment#isHeadless")
@DisplayName("Bake panel inline validation")
class BakePanelValidationTest {

    @BeforeAll
    static void requireDisplay() {
        // Touching a component early surfaces a missing display as a skip,
        // not as a cascade of failures inside each test.
        new JPanel();
    }

    /** Depth-first search for a button carrying the given label. */
    private static JButton findButton(Container root, String text) {
        List<Component> queue = new ArrayList<>(List.of(root.getComponents()));
        while (!queue.isEmpty()) {
            Component c = queue.remove(0);
            if (c instanceof JButton b && text.equals(b.getText())) return b;
            if (c instanceof Container nested) queue.addAll(List.of(nested.getComponents()));
        }
        throw new AssertionError("No button labelled " + text);
    }

    /** Depth-first search for the nth text field, in layout order. */
    private static JTextField field(Container root, int index) {
        List<JTextField> found = new ArrayList<>();
        collectFields(root, found);
        return found.get(index);
    }

    private static void collectFields(Container root, List<JTextField> out) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTextField f) out.add(f);
            if (c instanceof Container nested) collectFields(nested, out);
        }
    }

    @Test
    @DisplayName("batch panel: Bake All is disabled until input and output are real")
    void batchPanelGatesBakeButton(@TempDir Path tmp) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BatchBakePanel panel = new BatchBakePanel();
            JButton bake = findButton(panel, "Bake All");

            assertFalse(bake.isEnabled(), "empty form must not be bakeable");

            field(panel, 0).setText(tmp.toString());          // input folder
            assertFalse(bake.isEnabled(), "output still missing");

            field(panel, 1).setText(tmp.toString());          // output repo root
            assertTrue(bake.isEnabled(), "input + output present, so bakeable");

            field(panel, 0).setText(tmp.resolve("nope").toString());
            assertFalse(bake.isEnabled(), "non-existent input folder must block");
        });
    }

    @Test
    @DisplayName("single panel: Bake is disabled until the source file exists")
    void singlePanelGatesBakeButton(@TempDir Path tmp) throws Exception {
        Path source = Files.createFile(tmp.resolve("sample.13"));
        SwingUtilities.invokeAndWait(() -> {
            SingleFileBakePanel panel = new SingleFileBakePanel(
                    new com.jantenna.gui.viewer.PatternViewerPanel());
            JButton bake = findButton(panel, "Bake");

            assertFalse(bake.isEnabled(), "empty form must not be bakeable");

            field(panel, 0).setText(source.toString());       // input file
            field(panel, 1).setText(tmp.toString());          // output repo root
            assertFalse(bake.isEnabled(), "name still missing");

            field(panel, 3).setText("sample");                // name
            assertTrue(bake.isEnabled(), "all three present, so bakeable");
        });
    }
}
