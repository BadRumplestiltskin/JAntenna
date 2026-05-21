package com.jantenna.gui.bake;

import com.jantenna.GainTableCodec;
import com.jantenna.MetadataCodec;
import com.jantenna.baker.AntennaBaker;
import com.jantenna.baker.BakerDispatcher;
import com.jantenna.baker.ManifestWriter;
import com.jantenna.baker.RepositoryLock;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class BakeWorker extends SwingWorker<Path, String> {

    private final Path              source;
    private final Path              repoRoot;
    private final String            group;
    private final String            name;
    private final Consumer<String>  logConsumer;
    private final Consumer<Path>    onSuccess;
    private final Consumer<Throwable> onFailure;

    public BakeWorker(Path source, Path repoRoot, String group, String name,
                      Consumer<String> logConsumer,
                      Consumer<Path> onSuccess,
                      Consumer<Throwable> onFailure) {
        this.source      = source;
        this.repoRoot    = repoRoot;
        this.group       = group;
        this.name        = name;
        this.logConsumer = logConsumer;
        this.onSuccess   = onSuccess;
        this.onFailure   = onFailure;
    }

    @Override
    protected Path doInBackground() throws Exception {
        publish("Acquiring repository lock…");
        try (RepositoryLock lock = RepositoryLock.acquire(repoRoot)) {
            publish("Baking " + source.getFileName() + "…");
            AntennaBaker baker = BakerDispatcher.forFile(source);
            AntennaBaker.BakeResult result = baker.bake(source, group, name);

            Path groupDir = repoRoot.resolve(group);
            Files.createDirectories(groupDir);
            Path gtable = groupDir.resolve(name + ".gtable");
            Path meta   = groupDir.resolve(name + ".json");

            GainTableCodec.writeAtomic(result.table(), gtable);
            MetadataCodec.writeAtomic(result.metadata(), meta);
            ManifestWriter.regenerate(repoRoot);

            publish("Done: " + gtable);
            return gtable;
        }
    }

    @Override
    protected void process(List<String> chunks) {
        for (String msg : chunks) logConsumer.accept(msg);
    }

    @Override
    protected void done() {
        try {
            onSuccess.accept(get());
        } catch (ExecutionException ee) {
            onFailure.accept(ee.getCause() != null ? ee.getCause() : ee);
        } catch (Exception ex) {
            onFailure.accept(ex);
        }
    }
}
