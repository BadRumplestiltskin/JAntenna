package com.jantenna.reader;

import com.jantenna.GainTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link VoaAntennaReader#readType13} - the 360 x 91
 * azimuth x elevation gain-pattern parser.
 */
@DisplayName("VoaAntennaReader Type-13 (360x91 az x el) parsing")
class VoaAntennaReaderType13Test {

    private static final Path FIXTURE = Paths.get(
            "src/test/resources/voa-fixtures/sbrr216a.13");

    private static final Path ITU_FIXTURE = Paths.get(
            "src/test/resources/voa-fixtures/141-10_0.t13");

    @Test
    @DisplayName("Header parses: max-gain, type, frequency from the 4-parameter block")
    void header_parses() throws IOException {
        AntennaModel2D model = VoaAntennaReader.readType13(FIXTURE);
        assertNotNull(model);
        assertEquals(0.00, model.maxGainDbi(), 1e-9);
        assertEquals(1.85, model.designFrequency(), 1e-9);
    }

    @Test
    @DisplayName("Grid shape: 360 azimuth rows x 91 elevation samples")
    void shape_is360x91() throws IOException {
        AntennaModel2D model = VoaAntennaReader.readType13(FIXTURE);
        assertEquals(360, model.gainsAbsoluteDbi().length);
        for (int az = 0; az < 360; az++) {
            assertEquals(91, model.gainsAbsoluteDbi()[az].length);
        }
    }

    @Test
    @DisplayName("Spot values match the on-disk fixture at known (az, el) coords")
    void spotValues_matchFixture() throws IOException {
        AntennaModel2D model = VoaAntennaReader.readType13(FIXTURE);
        assertEquals(-99.99, model.gainAt(0,  0), 1e-9);
        assertEquals(-7.20,  model.gainAt(0,  1), 1e-9);
        assertEquals(-1.99,  model.gainAt(0,  2), 1e-9);
        assertEquals( 6.50,  model.gainAt(0, 10), 1e-9);
        assertEquals( 6.73,  model.gainAt(0, 11), 1e-9);
        assertEquals(-9.58,  model.gainAt(0, 90), 1e-9);
        assertEquals(-99.99, model.gainAt(1,  0), 1e-9);
        assertEquals(-7.20,  model.gainAt(1,  1), 1e-9);
        assertEquals(model.gainAt(0, 45), model.gainAt(360, 45), 1e-9);
        assertEquals(model.gainAt(0, 45), model.gainAt(-360, 45), 1e-9);
        assertEquals(-9.58, model.gainAt(359, 90), 1e-9);
    }

    @Test
    @DisplayName("Bruce array broadside-symmetric: az=0 ~ az=180 within 0.5 dB")
    void brucefourSidedSymmetry_holds() throws IOException {
        AntennaModel2D model = VoaAntennaReader.readType13(FIXTURE);
        for (int el : new int[] { 5, 15, 30, 45, 60 }) {
            double front = model.gainAt(0, el);
            double back  = model.gainAt(180, el);
            assertEquals(front, back, 0.5, "el=" + el);
        }
    }

    @Test
    @DisplayName("Below-horizon sentinel (-99.99) preserved at every azimuth row's elevation 0")
    void belowHorizonSentinel_preserved() throws IOException {
        AntennaModel2D model = VoaAntennaReader.readType13(FIXTURE);
        for (int az = 0; az < 360; az++) {
            assertEquals(-99.99, model.gainAt(az, 0), 1e-9, "az=" + az);
        }
    }

    @Test
    @DisplayName("Non-Type-13 file rejected by readType13")
    void readType13_rejectsType11File() {
        String type11 = """
                SWWhip.VOA used by VOA for receive antenna
                 3     3 parameters
                  0.00  [ 1] Max Gain dBi..:
                  11    [ 2] Antenna Type..: 91 values gain in elevation angle follows
                 -4.8   [ 3] Efficiency (for IONCAP)
                """;
        IOException e = assertThrows(IOException.class,
                () -> VoaAntennaReader.readType13(new StringReader(type11), "type11.voa"));
        assertTrue(e.getMessage().contains("4 parameters")
                || e.getMessage().contains("expected Antenna Type 13"),
                "Expected a clear 'wrong type' error, got: " + e.getMessage());
    }

    @Test
    @DisplayName("ITU-R Rec.705 AHRS-2-1 @ 10 MHz parses (header + frequency)")
    void itu_aHRS_10MHz_headerParses() throws IOException {
        AntennaModel2D model = VoaAntennaReader.readType13(ITU_FIXTURE);
        assertEquals(12.740, model.maxGainDbi(), 1e-9);
        assertEquals(10.000, model.designFrequency(), 1e-9);
        assertEquals(360, model.gainsAbsoluteDbi().length);
        assertEquals(91,  model.gainsAbsoluteDbi()[0].length);
    }

    @Test
    @DisplayName("ITU-R: jammed-negative format decomposes into individual values")
    void itu_jammedNegatives_parseCorrectly() throws IOException {
        AntennaModel2D model = VoaAntennaReader.readType13(ITU_FIXTURE);
        assertEquals(-99.999, model.gainAt(0, 0), 1e-9);
        assertEquals(-20.520, model.gainAt(0, 1), 1e-9);
        assertEquals(-14.560, model.gainAt(0, 2), 1e-9);
        assertEquals(-11.100, model.gainAt(0, 3), 1e-9);
        assertEquals( -8.680, model.gainAt(0, 4), 1e-9);
    }

    @Test
    @DisplayName("ITU-R: peak absolute gain over the full grid is at most Max Gain dBi")
    void itu_peakGainNotAboveMax() throws IOException {
        AntennaModel2D model = VoaAntennaReader.readType13(ITU_FIXTURE);
        double peak = Double.NEGATIVE_INFINITY;
        for (int az = 0; az < 360; az++) {
            for (int el = 0; el < 91; el++) {
                double g = model.gainAt(az, el);
                if (g > -99.0) peak = Math.max(peak, g);
            }
        }
        assertEquals(model.maxGainDbi(), peak, 0.01);
    }

    @Test
    @DisplayName("readGainTable wraps Type-13 fixture into a unified 360x91 tensor")
    void readGainTable_wrapsType13() throws IOException {
        GainTable t = VoaAntennaReader.readGainTable(FIXTURE);
        assertEquals(360, t.azimuthCount());
        assertEquals(91,  t.elevationCount());
        assertEquals(-9.58, t.gainDbi(1.85, 0.0, 90.0), 0.01);
    }

    @Test
    @DisplayName("Truncated body throws a clear EOF error")
    void truncatedBody_throws() throws IOException {
        String truncated;
        try (var reader = Files.newBufferedReader(FIXTURE)) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                String line = reader.readLine();
                if (line == null) break;
                sb.append(line).append('\n');
            }
            truncated = sb.toString();
        }
        IOException e = assertThrows(IOException.class,
                () -> VoaAntennaReader.readType13(new StringReader(truncated), "truncated.13"));
        assertTrue(e.getMessage().contains("EOF") || e.getMessage().contains("mid-pattern"));
    }
}
