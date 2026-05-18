package com.jantenna.repository;

import com.jantenna.GainTable;
import com.jantenna.GainTableCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for {@link FilesystemGainTableRepository}. */
@DisplayName("FilesystemGainTableRepository")
class FilesystemGainTableRepositoryTest {

    private static GainTable isotrope(double gainDbi, double freqMHz) {
        return new GainTable(
                new double[]{ freqMHz },
                new double[]{ 0.0 },
                new double[]{ 0.0 },
                new short[]{ GainTable.toCentiDb(gainDbi) });
    }

    @Test
    @DisplayName("Empty repository: contains=false, load=throws, list=empty")
    void empty(@TempDir Path tmp) throws IOException {
        FilesystemGainTableRepository repo = new FilesystemGainTableRepository(tmp);
        assertFalse(repo.contains("default/swwhip"));
        assertEquals(List.of(), repo.list());
        assertThrows(GainTableRepository.AntennaNotInRepository.class,
            () -> repo.load("default/swwhip"));
    }

    @Test
    @DisplayName("Round-trip: write via codec, load via repo")
    void roundTrip(@TempDir Path tmp) throws IOException {
        FilesystemGainTableRepository repo = new FilesystemGainTableRepository(tmp);
        GainTable expected = isotrope(5.0, 14.15);

        Files.createDirectories(tmp.resolve("default"));
        GainTableCodec.writeAtomic(expected, tmp.resolve("default/swwhip.gtable"));

        assertTrue(repo.contains("default/swwhip"));
        assertEquals(expected, repo.load("default/swwhip"));
    }

    @Test
    @DisplayName("Canonicalise: extension strip + lowercase")
    void canonicalised(@TempDir Path tmp) throws IOException {
        FilesystemGainTableRepository repo = new FilesystemGainTableRepository(tmp);
        GainTable expected = isotrope(5.0, 14.15);
        Files.createDirectories(tmp.resolve("default"));
        GainTableCodec.writeAtomic(expected, tmp.resolve("default/swwhip.gtable"));

        assertTrue(repo.contains("default/SWWHIP"));
        assertTrue(repo.contains("default/SWWHIP.VOA"));
        assertTrue(repo.contains("default/swwhip.voa"));
        assertEquals(expected, repo.load("default/SWWHIP.VOA"));
    }

    @Test
    @DisplayName("List returns sorted names across groups")
    void listSorted(@TempDir Path tmp) throws IOException {
        FilesystemGainTableRepository repo = new FilesystemGainTableRepository(tmp);
        GainTable t = isotrope(0.0, 10.0);
        Files.createDirectories(tmp.resolve("itur705"));
        Files.createDirectories(tmp.resolve("default"));
        GainTableCodec.writeAtomic(t, tmp.resolve("default/swwhip.gtable"));
        GainTableCodec.writeAtomic(t, tmp.resolve("default/const5.gtable"));
        GainTableCodec.writeAtomic(t, tmp.resolve("itur705/141.gtable"));

        List<String> names = repo.list();
        assertEquals(List.of("default/const5", "default/swwhip", "itur705/141"), names);
    }

    @Test
    @DisplayName("Cache: load() returns same instance on second call")
    void caching(@TempDir Path tmp) throws IOException {
        FilesystemGainTableRepository repo = new FilesystemGainTableRepository(tmp);
        Files.createDirectories(tmp.resolve("default"));
        GainTableCodec.writeAtomic(isotrope(5.0, 14.15), tmp.resolve("default/test.gtable"));

        GainTable first = repo.load("default/test");
        GainTable second = repo.load("default/test");
        assertSame(first, second);

        repo.invalidateCache();
        GainTable third = repo.load("default/test");
        assertEquals(first, third);
        assertNotSame(first, third);
    }

    @Test
    @DisplayName("Missing repository directory: contains=false, list=empty")
    void missingRootDir(@TempDir Path tmp) throws IOException {
        Path nonexistent = tmp.resolve("does-not-exist");
        FilesystemGainTableRepository repo = new FilesystemGainTableRepository(nonexistent);
        assertFalse(repo.contains("default/swwhip"));
        assertEquals(List.of(), repo.list());
    }

    @Test
    @DisplayName("AntennaNotInRepository message points at the bake CLI")
    void notInRepoMessage(@TempDir Path tmp) {
        FilesystemGainTableRepository repo = new FilesystemGainTableRepository(tmp);
        GainTableRepository.AntennaNotInRepository ex = assertThrows(
            GainTableRepository.AntennaNotInRepository.class,
            () -> repo.load("nonexistent/antenna"));
        assertTrue(ex.getMessage().contains("jantenna bake"),
            "Error should direct user to the bake CLI; got: " + ex.getMessage());
    }
}
