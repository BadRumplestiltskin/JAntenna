package com.jantenna.baker;

import com.jantenna.BakerVersion;
import com.jantenna.AntennaMetadata;
import com.jantenna.MetadataCodec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Regenerates {@code <repo>/manifest.json} — a flat summary of every
 * pattern in the repository.  Per {@code docs/antennas.md} §10 this is
 * an optimisation (avoids walking 100+ JSON sidecars for catalogue
 * queries), not a correctness requirement.
 *
 * <p>The manifest is fully rebuilt from the on-disk metadata sidecars
 * after every bake; it's never the source of truth, just a cached
 * view.  Hand-deleting it is harmless — the next bake regenerates.</p>
 */
public final class ManifestWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ManifestWriter() { }

    /** Top-level manifest schema. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Manifest(
            @JsonProperty("patterns")        List<Entry> patterns,
            @JsonProperty("regenerated_at")  Instant     regeneratedAt,
            @JsonProperty("jvoacap_version") String      jvoacapVersion
    ) { }

    /** One row per pattern in the repo. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Entry(
            @JsonProperty("name")          String  name,        // group/name
            @JsonProperty("kind")          String  kind,
            @JsonProperty("description")   String  description,
            @JsonProperty("peak_gain_dbi") Double  peakGainDbi,
            @JsonProperty("min_freq_mhz")  Double  minFreqMhz,
            @JsonProperty("max_freq_mhz")  Double  maxFreqMhz,
            @JsonProperty("az_dependent")  Boolean azDependent,
            @JsonProperty("freq_dependent") Boolean freqDependent
    ) { }

    /**
     * Walk {@code root}, read every {@code .json} sidecar, write a
     * regenerated {@code manifest.json} at the repo root.
     * @param root
     * @throws java.io.IOException
     */
    public static void regenerate(Path root) throws IOException {
        if (!Files.exists(root)) return;
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().endsWith(".json"))
                  .filter(p -> !p.getFileName().toString().equals("manifest.json"))
                  .forEach(metaFile -> {
                      try {
                          AntennaMetadata m = MetadataCodec.read(metaFile);
                          entries.add(new Entry(
                                  m.group() + "/" + m.name(),
                                  m.source() != null ? m.source().kind() : null,
                                  m.description(),
                                  m.peakGainDbi(),
                                  m.minFreqMhz(),
                                  m.maxFreqMhz(),
                                  m.grid() != null ? m.grid().azDependent() : null,
                                  m.grid() != null ? m.grid().freqDependent() : null));
                      } catch (IOException e) {
                          // Skip unreadable metadata; the bake CLI's verify
                          // sub-command surfaces these issues separately.
                      }
                  });
        }
        entries.sort(Comparator.comparing(Entry::name));
        Manifest manifest = new Manifest(entries, Instant.now(), BakerVersion.JANTENNA_VERSION);
        Path target = root.resolve("manifest.json");
        Path tmp = target.resolveSibling("manifest.json.tmp");
        Files.writeString(tmp, MAPPER.writeValueAsString(manifest),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try {
            Files.move(tmp, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailed) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
