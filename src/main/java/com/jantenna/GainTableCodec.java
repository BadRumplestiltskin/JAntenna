package com.jantenna;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * Binary I/O for the {@code .gtable} file format.
 *
 * <p><b>Byte layout</b> (little-endian throughout):</p>
 * <pre>
 *   off  len  field
 *     0    8  magic "JVOAGT01"
 *     8    4  version (int32 LE)        - current: {@value #FORMAT_VERSION}
 *    12    4  flags   (int32 LE)        - reserved, currently 0
 *    16    4  F       (int32 LE)        - frequency axis length
 *    20    4  A       (int32 LE)        - azimuth axis length
 *    24    4  E       (int32 LE)        - elevation axis length
 *    28  8*F  freqs   (double[F] LE)
 *  ...  8*A  azs     (double[A] LE)
 *  ...  8*E  els     (double[E] LE)
 *  ...  2*F*A*E  gains (int16[F*A*E] LE) - scaled x 100; sentinel = {@code Short.MIN_VALUE}
 * </pre>
 *
 * <p>This codec is stateless; {@link #write(GainTable, OutputStream)} and
 * {@link #read(InputStream)} are the only public entry points.  Atomic
 * file writes are handled by {@link #writeAtomic(GainTable, Path)} which
 * writes to a {@code .tmp} sibling then renames via
 * {@link StandardCopyOption#ATOMIC_MOVE}.</p>
 */
public final class GainTableCodec {

    /** ASCII magic bytes identifying a {@code .gtable} file. */
    public static final byte[] MAGIC = { 'J', 'V', 'O', 'A', 'G', 'T', '0', '1' };

    /** Current binary format version. */
    public static final int FORMAT_VERSION = 1;

    private GainTableCodec() {
        // utility class
    }

    /**
     * Serialize a {@link GainTable} to a stream.  The stream is not closed
     * by this method; the caller is responsible.
     */
    public static void write(GainTable table, OutputStream out) throws IOException {
        DataOutput d = new DataOutputStream(new BufferedOutputStream(out));
        writeHeader(d, table);
        writeAxis(d, table.frequenciesMHz());
        writeAxis(d, table.azimuthsDeg());
        writeAxis(d, table.elevationsDeg());
        writeGains(d, table.gainsCentiDb());
        ((DataOutputStream) d).flush();
    }

    /**
     * Atomic write to a file path.  Writes to {@code <path>.tmp} first,
     * then atomically renames over the target.  A failure mid-write
     * leaves only the {@code .tmp} file behind (or nothing, if the open
     * itself failed); the target is never partially overwritten.
     */
    public static void writeAtomic(GainTable table, Path target) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream os = Files.newOutputStream(tmp,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            write(table, os);
        }
        try {
            Files.move(tmp, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailed) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeHeader(DataOutput d, GainTable table) throws IOException {
        d.write(MAGIC);
        writeIntLE(d, FORMAT_VERSION);
        writeIntLE(d, 0);
        writeIntLE(d, table.frequencyCount());
        writeIntLE(d, table.azimuthCount());
        writeIntLE(d, table.elevationCount());
    }

    private static void writeAxis(DataOutput d, double[] axis) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(axis.length * Double.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (double v : axis) buf.putDouble(v);
        d.write(buf.array());
    }

    private static void writeGains(DataOutput d, short[] gains) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(gains.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (short v : gains) buf.putShort(v);
        d.write(buf.array());
    }

    /**
     * Deserialize a {@link GainTable} from a stream.  Validates magic
     * bytes and version; throws {@link IOException} on malformed input.
     * The stream is not closed by this method.
     */
    public static GainTable read(InputStream in) throws IOException {
        DataInput d = new DataInputStream(new BufferedInputStream(in));
        readAndCheckMagic(d);
        int version = readIntLE(d);
        if (version != FORMAT_VERSION) {
            throw new IOException("Unsupported .gtable format version " + version
                    + " (this build reads version " + FORMAT_VERSION + ")");
        }
        readIntLE(d);
        int F = readIntLE(d);
        int A = readIntLE(d);
        int E = readIntLE(d);
        if (F < 1 || A < 1 || E < 1) {
            throw new IOException("Invalid axis counts: F=" + F + " A=" + A + " E=" + E);
        }
        double[] freqs = readAxis(d, F);
        double[] azs   = readAxis(d, A);
        double[] els   = readAxis(d, E);
        short[]  gains = readGains(d, F * A * E);
        return new GainTable(freqs, azs, els, gains);
    }

    public static GainTable read(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
            return read(in);
        }
    }

    private static void readAndCheckMagic(DataInput d) throws IOException {
        byte[] magic = new byte[MAGIC.length];
        d.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException("Not a .gtable file: bad magic bytes "
                    + Arrays.toString(magic) + " (expected " + Arrays.toString(MAGIC) + ")");
        }
    }

    private static double[] readAxis(DataInput d, int n) throws IOException {
        byte[] bytes = new byte[n * Double.BYTES];
        d.readFully(bytes);
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = buf.getDouble();
        return out;
    }

    private static short[] readGains(DataInput d, int n) throws IOException {
        byte[] bytes = new byte[n * Short.BYTES];
        try {
            d.readFully(bytes);
        } catch (EOFException eof) {
            throw new IOException(
                    "Truncated .gtable: expected " + bytes.length
                    + " bytes of gain data, hit EOF", eof);
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) out[i] = buf.getShort();
        return out;
    }

    private static void writeIntLE(DataOutput d, int v) throws IOException {
        d.writeByte( v        & 0xFF);
        d.writeByte((v >>>  8) & 0xFF);
        d.writeByte((v >>> 16) & 0xFF);
        d.writeByte((v >>> 24) & 0xFF);
    }

    private static int readIntLE(DataInput d) throws IOException {
        int b0 = d.readByte() & 0xFF;
        int b1 = d.readByte() & 0xFF;
        int b2 = d.readByte() & 0xFF;
        int b3 = d.readByte() & 0xFF;
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }
}
