package com.jantenna;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for {@link GainTableCodec} - the {@code .gtable} binary I/O codec. */
@DisplayName("GainTableCodec binary I/O")
class GainTableCodecTest {

    private static GainTable isotrope(double gainDbi, double freqMHz) {
        return new GainTable(
                new double[]{ freqMHz },
                new double[]{ 0.0 },
                new double[]{ 0.0 },
                new short[]{ GainTable.toCentiDb(gainDbi) });
    }

    private static GainTable azElGrid(double[][] grid, double freqMHz) {
        double[] azs = new double[grid.length];
        double[] els = new double[grid[0].length];
        for (int a = 0; a < azs.length; a++) azs[a] = a;
        for (int e = 0; e < els.length; e++) els[e] = e;
        short[] flat = new short[grid.length * grid[0].length];
        for (int a = 0; a < grid.length; a++) {
            for (int e = 0; e < grid[0].length; e++) {
                flat[a * grid[0].length + e] = GainTable.toCentiDb(grid[a][e]);
            }
        }
        return new GainTable(new double[]{ freqMHz }, azs, els, flat);
    }

    @Test
    @DisplayName("Isotrope round-trip preserves all fields")
    void isotrope_roundTrip() throws IOException {
        GainTable original = isotrope(5.0, 14.15);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GainTableCodec.write(original, out);
        GainTable read = GainTableCodec.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(original, read);
        assertEquals(5.0, read.gainDbi(14.15, 0.0, 0.0), 0.005);
    }

    @Test
    @DisplayName("Az-El 360x91 round-trip preserves spot values")
    void azEl_roundTrip() throws IOException {
        double[][] grid = new double[360][91];
        for (int a = 0; a < 360; a++)
            for (int e = 0; e < 91; e++)
                grid[a][e] = (a * 0.01) + (e * 0.1) - 5.0;
        GainTable original = azElGrid(grid, 10.0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GainTableCodec.write(original, out);
        GainTable read = GainTableCodec.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(original, read);
        assertEquals(grid[180][30], read.gainDbi(10.0, 180.0, 30.0), 0.01);
        assertEquals(grid[359][90], read.gainDbi(10.0, 359.0, 90.0), 0.01);
    }

    @Test
    @DisplayName("Multi-frequency round-trip preserves freq axis")
    void multiFreq_roundTrip() throws IOException {
        double[] freqs = { 10.0, 15.0, 20.0 };
        double[] azs = new double[360];
        double[] els = new double[91];
        for (int a = 0; a < 360; a++) azs[a] = a;
        for (int e = 0; e < 91; e++) els[e] = e;
        short[] flat = new short[3 * 360 * 91];
        for (int f = 0; f < 3; f++) {
            double base = freqs[f] * 0.5;
            for (int a = 0; a < 360; a++) {
                for (int e = 0; e < 91; e++) {
                    flat[f * 360 * 91 + a * 91 + e] = GainTable.toCentiDb(base + (a + e) * 0.001);
                }
            }
        }
        GainTable original = new GainTable(freqs, azs, els, flat);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GainTableCodec.write(original, out);
        GainTable read = GainTableCodec.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(original, read);
        assertEquals(3, read.frequencyCount());
    }

    @Test
    @DisplayName("Sentinel survives round-trip")
    void sentinel_roundTrip() throws IOException {
        double[] freqs = { 14.0 };
        double[] azs   = { 0.0 };
        double[] els   = new double[91];
        short[] flat = new short[91];
        for (int e = 0; e < 91; e++) els[e] = e;
        flat[0] = GainTable.SENTINEL_CENTI_DB;
        for (int e = 1; e < 91; e++) flat[e] = GainTable.toCentiDb(e);
        GainTable original = new GainTable(freqs, azs, els, flat);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GainTableCodec.write(original, out);
        GainTable read = GainTableCodec.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(GainTable.SENTINEL_CENTI_DB, read.gainsCentiDb()[0]);
    }

    @Test
    @DisplayName("Magic bytes are JVOAGT01 at offset 0")
    void magic_bytes_at_offset_zero() throws IOException {
        GainTable t = isotrope(0.0, 14.0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GainTableCodec.write(t, out);
        byte[] bytes = out.toByteArray();
        assertArrayEquals(
            new byte[]{ 'J', 'V', 'O', 'A', 'G', 'T', '0', '1' },
            java.util.Arrays.copyOfRange(bytes, 0, 8));
    }

    @Test
    @DisplayName("Read rejects unknown magic")
    void rejectsBadMagic() {
        byte[] bogus = new byte[1024];
        bogus[0] = 'X';  bogus[1] = 'Y';  bogus[2] = 'Z';
        IOException e = assertThrows(IOException.class,
            () -> GainTableCodec.read(new ByteArrayInputStream(bogus)));
        assertTrue(e.getMessage().contains("bad magic"));
    }

    @Test
    @DisplayName("Read rejects unsupported version")
    void rejectsBadVersion() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(GainTableCodec.MAGIC);
        out.write(99); out.write(0); out.write(0); out.write(0);
        out.write(0); out.write(0); out.write(0); out.write(0);
        out.write(1); out.write(0); out.write(0); out.write(0);
        out.write(1); out.write(0); out.write(0); out.write(0);
        out.write(1); out.write(0); out.write(0); out.write(0);
        IOException e = assertThrows(IOException.class,
            () -> GainTableCodec.read(new ByteArrayInputStream(out.toByteArray())));
        assertTrue(e.getMessage().contains("Unsupported"));
    }

    @Test
    @DisplayName("File-path round-trip via writeAtomic + read(Path)")
    void filePath_roundTrip(@TempDir Path tmp) throws IOException {
        GainTable original = isotrope(7.5, 14.15);
        Path target = tmp.resolve("test.gtable");
        GainTableCodec.writeAtomic(original, target);
        assertTrue(Files.exists(target));
        assertFalse(Files.exists(target.resolveSibling("test.gtable.tmp")));
        GainTable read = GainTableCodec.read(target);
        assertEquals(original, read);
    }

    @Test
    @DisplayName("File size matches the formula header + 8*(F+A+E) + 2*F*A*E")
    void fileSize_matchesSpec(@TempDir Path tmp) throws IOException {
        GainTable t = isotrope(0.0, 10.0);
        Path target = tmp.resolve("iso.gtable");
        GainTableCodec.writeAtomic(t, target);
        assertEquals(54L, Files.size(target));
    }
}
