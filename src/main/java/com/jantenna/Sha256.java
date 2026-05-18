package com.jantenna;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 hashing of source files for drift detection. */
public final class Sha256 {

    private Sha256() { }

    /** Return the lowercase hex SHA-256 of the file at {@code path}.
     * @param path
     * @return 
     * @throws java.io.IOException */
    public static String ofFile(Path path) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
