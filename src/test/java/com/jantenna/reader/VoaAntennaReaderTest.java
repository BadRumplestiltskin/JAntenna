package com.jantenna.reader;

import com.jantenna.GainTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the VOACAPL {@code .voa} antenna-pattern reader.
 * Exercises the isotrope (type 0) and 91-elevation (type 11) variants
 * via both the package-private {@code read()} entry point (returning
 * the internal {@link AntennaModel}) and the public
 * {@link VoaAntennaReader#readGainTable} entry point (returning the
 * unified {@link GainTable}).
 */
class VoaAntennaReaderTest {

    @Test
    @DisplayName("Type 0 (isotrope) yields 91 equal samples at Max Gain dBi")
    void testIsotropic() throws IOException {
        String voa = """
                ISOTROPE  :Sample type 00 Constant gain isotrope
                 2     2 parameters
                  0.00  [ 1] Max Gain dBi..:
                   0    [ 2] Antenna Type..:
                """;
        AntennaModel model = VoaAntennaReader.read(new StringReader(voa), "inline-test");
        assertEquals(91, model.interpolatedGains().length);
        for (double g : model.interpolatedGains()) {
            assertEquals(0.0, g, 1e-9, "Every elevation should be 0.0 dBi for isotrope");
        }
    }

    @Test
    @DisplayName("Type 0 with non-zero Max Gain (e.g. 7 dBi) applies uniform offset")
    void testIsotropicWithGain() throws IOException {
        String voa = """
                Constant 7 dBi gain radiator
                 2     2 parameters
                  7.00  [ 1] Max Gain dBi..:
                   0    [ 2] Antenna Type..:
                """;
        AntennaModel model = VoaAntennaReader.read(new StringReader(voa), "7dbi");
        for (double g : model.interpolatedGains()) {
            assertEquals(7.0, g, 1e-9, "All angles should report 7 dBi");
        }
    }

    @Test
    @DisplayName("Type 11 (const17.voa format) reads 91 gain offsets and applies Max Gain")
    void testType11Const17() throws IOException {
        StringBuilder body = new StringBuilder();
        body.append("Const 17dB transmit antenna used by VOA\n");
        body.append(" 3     3 parameters\n");
        body.append(" 17.00  [ 1] Max Gain dBi..:\n");
        body.append("  11    [ 2] Antenna Type..: 91 values gain in elevation angle follows\n");
        body.append("  0.0   [ 3] Efficiency (for IONCAP)\n");
        body.append(" -43.0  -12.0   -7.0   -2.0 ");
        for (int i = 4; i < 91; i++) body.append("  0.0 ");
        body.append("\n");

        AntennaModel model = VoaAntennaReader.read(new StringReader(body.toString()), "const17");
        assertEquals(91, model.interpolatedGains().length);
        assertEquals(-26.0, model.interpolatedGains()[0], 1e-9, "0 deg sample: 17 + (-43) = -26 dBi");
        assertEquals(5.0,   model.interpolatedGains()[1], 1e-9, "1 deg sample: 17 + (-12) = 5 dBi");
        assertEquals(10.0,  model.interpolatedGains()[2], 1e-9, "2 deg sample: 17 + (-7) = 10 dBi");
        assertEquals(15.0,  model.interpolatedGains()[3], 1e-9, "3 deg sample: 17 + (-2) = 15 dBi");
        for (int i = 4; i < 91; i++) {
            assertEquals(17.0, model.interpolatedGains()[i], 1e-9,
                    "Elevation " + i + " deg expected 17 dBi");
        }
    }

    @Test
    @DisplayName("readGainTable returns unified tensor for Type 11 const17 sample")
    void readGainTable_const17() throws IOException {
        StringBuilder body = new StringBuilder();
        body.append("Const 17dB transmit antenna\n");
        body.append(" 3     3 parameters\n");
        body.append(" 17.00  [ 1] Max Gain dBi..:\n");
        body.append("  11    [ 2] Antenna Type..:\n");
        body.append("  0.0   [ 3] Efficiency\n");
        body.append(" -43.0  -12.0   -7.0   -2.0 ");
        for (int i = 4; i < 91; i++) body.append("  0.0 ");
        body.append("\n");
        GainTable t = VoaAntennaReader.readGainTable(new StringReader(body.toString()), "const17");
        assertEquals(91, t.elevationCount());
        assertEquals(1,  t.azimuthCount());
        assertEquals(-26.0, t.gainDbi(0.0, 0.0,  0.0), 0.01);
        assertEquals( 17.0, t.gainDbi(0.0, 0.0, 45.0), 0.01);
    }

    @Test
    @DisplayName("Too few gain values in pattern body raises IOException")
    void testTruncatedFile() {
        String truncated = """
                Truncated
                 3     3 parameters
                  0.00  [ 1] Max Gain dBi..:
                  11    [ 2] Antenna Type..:
                  0.0   [ 3] Efficiency
                  1.0  2.0  3.0
                """;
        IOException ex = assertThrows(IOException.class,
                () -> VoaAntennaReader.read(new StringReader(truncated), "truncated"));
        assertTrue(ex.getMessage().contains("91"),
                "Error message should reference expected count: " + ex.getMessage());
    }

    @Test
    @DisplayName("Unknown antenna type raises a descriptive IOException")
    void testUnknownType() {
        String unknown = """
                Unknown type
                 2     2 parameters
                  0.00  [ 1] Max Gain dBi..:
                  99    [ 2] Antenna Type..:
                """;
        IOException ex = assertThrows(IOException.class,
                () -> VoaAntennaReader.read(new StringReader(unknown), "weird"));
        assertTrue(ex.getMessage().contains("99"), ex.getMessage());
    }
}
