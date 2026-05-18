package com.jantenna.baker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for {@link BakerDispatcher} — file-header jant detection + routing. */
@DisplayName("BakerDispatcher — jant detection routing")
class BakerDispatcherTest {

    @Test
    @DisplayName("Type-11 source → Type11Baker")
    void detectType11(@TempDir Path tmp) throws IOException {
        Path source = writeVoa(tmp, "type11.voa", """
                Constant 5 dBi reference antenna
                 3     3 parameters
                  5.00  [ 1] Max Gain dBi..:
                  11    [ 2] Antenna Type..: 91 values gain in elevation angle follows
                  0.0   [ 3] Efficiency (for IONCAP)
                """);
        AntennaBaker baker = BakerDispatcher.forFile(source);
        assertInstanceOf(Type11Baker.class, baker);
        assertEquals("VOA_TYPE_11", baker.sourceKind());
    }

    @Test
    @DisplayName("Type-13 source → Type13Baker")
    void detectType13(@TempDir Path tmp) throws IOException {
        Path source = writeVoa(tmp, "type13.voa", """
                AHRS test antenna
                 4     4 parameters
                 12.74  [ 1] Max Gain dBi..:
                  13    [ 2] Antenna Type..: 360 x 91 gain values follow
                  0.0   [ 3] Efficiency (for IONCAP)
                 10.00  [ 4] Frequency
                """);
        AntennaBaker baker = BakerDispatcher.forFile(source);
        assertInstanceOf(Type13Baker.class, baker);
    }

    @Test
    @DisplayName("Unsupported jant rejected with clear message")
    void rejectsUnsupported(@TempDir Path tmp) throws IOException {
        Path source = writeVoa(tmp, "type5.voa", """
                CCIR horizontal log-periodic
                 3     3 parameters
                  0.00  [ 1] Max Gain dBi..:
                   5    [ 2] Antenna Type..: CCIR Horizontal Log-Periodic
                  0.0   [ 3] Efficiency
                """);
        IOException e = assertThrows(IOException.class, () -> BakerDispatcher.forFile(source));
        assertTrue(e.getMessage().contains("jant=5"));
        assertTrue(e.getMessage().contains("Phase B"));
    }

    @Test
    @DisplayName("Truncated header rejected")
    void rejectsTruncated(@TempDir Path tmp) throws IOException {
        Path source = writeVoa(tmp, "short.voa", "");
        assertThrows(IOException.class, () -> BakerDispatcher.forFile(source));
    }

    private static Path writeVoa(Path dir, String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p;
    }
}
