package com.jantenna;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** JSON I/O for {@link AntennaMetadata} sidecars. */
public final class MetadataCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private MetadataCodec() { }

    public static String toJson(AntennaMetadata meta) throws IOException {
        return MAPPER.writeValueAsString(meta);
    }

    public static AntennaMetadata fromJson(String json) throws IOException {
        return MAPPER.readValue(json, AntennaMetadata.class);
    }

    public static AntennaMetadata read(Path file) throws IOException {
        return MAPPER.readValue(file.toFile(), AntennaMetadata.class);
    }

    /**
     * Atomic write to a file path: writes to {@code <path>.tmp} then
     * renames over the target.  Mirrors {@link GainTableCodec#writeAtomic}.
     * @param meta
     * @param target
     * @throws java.io.IOException
     */
    public static void writeAtomic(AntennaMetadata meta, Path target) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, toJson(meta),
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
