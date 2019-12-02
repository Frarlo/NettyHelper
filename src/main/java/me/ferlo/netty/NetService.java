package me.ferlo.netty;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

public interface NetService extends Closeable {
    CompletableFuture<Void> startAsync() throws NetworkException;

    CompletableFuture<Void> closeAsync() throws NetworkException;
}
