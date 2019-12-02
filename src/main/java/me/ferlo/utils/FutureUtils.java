package me.ferlo.utils;

import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class FutureUtils {

    /**
     * Limit scope
     */
    private FutureUtils() {}

    /**
     * Waits if necessary for this future to complete, and then
     * returns its result.
     *
     * The {@link InterruptedException} is not swallowed
     * but it's thrown as an unchecked one.
     *
     * @param promise   Future
     * @param <T>       promise return type
     * @return          the result value
     *
     * @throws ExecutionException       if this future completed exceptionally
     * @throws CancellationException    if this future was cancelled
     *                                  while waiting
     */
    public static <T> T getUninterruptibly(Future<T> promise) throws ExecutionException {
        try {
            return promise.get();
        } catch (InterruptedException e) {
            throw SneakyThrow.throwUnchecked(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<T> nettyToJava(io.netty.util.concurrent.Future<T> promise) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        promise.addListener(nettyFuture -> {
            if(nettyFuture.isSuccess())
                future.complete((T) nettyFuture.getNow());
            else
                future.completeExceptionally(nettyFuture.cause());
        });
        return future;
    }

    @SuppressWarnings("unchecked")
    public static <T> Promise<T> javaToNetty(CompletableFuture<T> future) {
        final Promise promise = GlobalEventExecutor.INSTANCE.newPromise();
        future.thenAccept(promise::setSuccess)
                .exceptionally(ex -> {
                    promise.setFailure(ex);
                    return null;
                });
        return promise;
    }
}
