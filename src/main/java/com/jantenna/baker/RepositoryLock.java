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
 * semantics on the antenna repository.
 *
 * <p>Usage (try-with-resources):</p>
 * <pre>
 *   try (RepositoryLock lock = RepositoryLock.acquire(repoRoot)) {
 *       // mutating operations on the repository
 *   }
 * </pre>
 *
 * <p>If another process already holds the lock, {@link #acquire} throws
 * {@link RepositoryLocked} immediately - no wait.  Bake CLI invocations
 * are expected to be short, so a fast failure is preferable to silent
 * blocking.</p>
 */
public final class RepositoryLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock    lock;

    private RepositoryLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Take an exclusive advisory lock on {@code <root>/.lock}.  Creates
     * the lock file (and the repository directory if missing).
     *
     * @throws RepositoryLocked if another process or thread holds the lock
     * @throws IOException      on filesystem errors opening the lock file
     */
    public static RepositoryLock acquire(Path root) throws IOException {
        Files.createDirectories(root);
        Path lockFile = root.resolve(".lock");
        FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        boolean ownershipTransferred = false;
        try {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException olfe) {
                throw new RepositoryLocked(root, "another thread in this JVM holds the lock");
            }
            if (lock == null) {
                throw new RepositoryLocked(root, "another process holds the lock");
            }
            RepositoryLock acquired = new RepositoryLock(channel, lock);
            ownershipTransferred = true;
            return acquired;
        } finally {
            if (!ownershipTransferred) {
                channel.close();
            }
        }
    }

    @Override
    public void close() throws IOException {
        // Leave the .lock file behind - its presence is harmless and
        // saves a create-delete round-trip on every bake.  Release the
        // lock first; close() always runs to drop the file handle even
        // if release() throws.  If both throw, channel.close()'s exception
        // suppresses lock.release()'s — standard Java finally semantics.
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }

    /** Thrown when {@link #acquire} cannot obtain an exclusive lock. */
    public static final class RepositoryLocked extends IOException {
        private static final long serialVersionUID = 1L;

        public RepositoryLocked(Path root, String reason) {
            super("Repository at " + root + " is locked: " + reason
                + "  (Another `jantenna` process is probably running.)");
        }
    }
}
