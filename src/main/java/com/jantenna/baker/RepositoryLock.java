package com.jantenna.baker;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Advisory file lock at {@code <repo>/.lock} to enforce single-writer
 * semantics on the antenna repository per {@code docs/antennas.md}
 * §5.3 (locked E20).
 *
 * <p>Usage (try-with-resources):</p>
 * <pre>
 *   try (RepositoryLock lock = RepositoryLock.acquire(repoRoot)) {
 *       // mutating operations on the repository
 *   }
 * </pre>
 *
 * <p>If another process already holds the lock, {@link #acquire} throws
 * {@link RepositoryLocked} immediately — no wait.  Bake CLI invocations
 * are expected to be short, so deadlock-by-coincidence is preferable to
 * the silent waits a long-blocking lock would produce.</p>
 */
public final class RepositoryLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock    lock;
    private final Path        lockFile;

    private RepositoryLock(FileChannel channel, FileLock lock, Path lockFile) {
        this.channel = channel;
        this.lock = lock;
        this.lockFile = lockFile;
    }

    /**
     * Take an exclusive advisory lock on {@code <root>/.lock}.  Creates
     * the lock file (and the repository directory if missing).
     *
     * @throws RepositoryLocked if another process holds the lock
     */
    public static RepositoryLock acquire(Path root) throws IOException {
        Files.createDirectories(root);
        Path lockFile = root.resolve(".lock");
        FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException olfe) {
            channel.close();
            throw new RepositoryLocked(root, "another thread in this JVM holds the lock");
        }
        if (lock == null) {
            channel.close();
            throw new RepositoryLocked(root, "another process holds the lock");
        }
        return new RepositoryLock(channel, lock, lockFile);
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
            // Leave .lock file behind — its presence is harmless and
            // saves a create-delete round-trip on every bake.
        }
    }

    /** Thrown when {@link #acquire} cannot obtain an exclusive lock. */
    public static final class RepositoryLocked extends IOException {
        private static final long serialVersionUID = 1L;

        public RepositoryLocked(Path root, String reason) {
            super("Repository at " + root + " is locked: " + reason
                + "  (Another `jvoacap-antennas` process is probably running.)");
        }
    }
}
