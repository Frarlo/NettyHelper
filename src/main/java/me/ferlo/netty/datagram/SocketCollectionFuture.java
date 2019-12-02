package me.ferlo.netty.datagram;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.group.ChannelGroupException;
import io.netty.util.concurrent.*;

import java.net.InetSocketAddress;
import java.util.*;

public class SocketCollectionFuture extends DefaultPromise<Void> {

    private final Map<InetSocketAddress, ChannelFuture> futures;
    private int successCount;
    private int failureCount;

    private final ChannelFutureListener childListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            boolean success = future.isSuccess();
            boolean callSetDone;
            synchronized (SocketCollectionFuture.this) {
                if (success) {
                    successCount ++;
                } else {
                    failureCount ++;
                }

                callSetDone = successCount + failureCount == futures.size();
                assert successCount + failureCount <= futures.size();
            }

            if (callSetDone) {
                if (failureCount > 0) {
                    List<Map.Entry<Channel, Throwable>> failed = new ArrayList<>(failureCount);

                    for (ChannelFuture f : futures.values()) {
                        if (!f.isSuccess()) {
                            failed.add(new AbstractMap.SimpleImmutableEntry<>(f.channel(), f.cause()));
                        }
                    }
                    setFailure0(new ChannelGroupException(failed));
                } else {
                    setSuccess0();
                }
            }
        }
    };

    public SocketCollectionFuture(Map<InetSocketAddress, ChannelFuture> futures,
                                  EventExecutor executor) {

        super(executor);

        this.futures = Collections.unmodifiableMap(futures);
        for (ChannelFuture f: this.futures.values()) {
            f.addListener(childListener);
        }

        // Done on arrival?
        if (this.futures.isEmpty()) {
            setSuccess0();
        }
    }

    @Override
    public SocketCollectionFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        super.addListener(listener);
        return this;
    }

    @Override
    public SocketCollectionFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        super.addListeners(listeners);
        return this;
    }

    @Override
    public SocketCollectionFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        super.removeListener(listener);
        return this;
    }

    @Override
    public SocketCollectionFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        super.removeListeners(listeners);
        return this;
    }

    @Override
    public SocketCollectionFuture await() throws InterruptedException {
        super.await();
        return this;
    }

    @Override
    public SocketCollectionFuture awaitUninterruptibly() {
        super.awaitUninterruptibly();
        return this;
    }

    @Override
    public SocketCollectionFuture syncUninterruptibly() {
        super.syncUninterruptibly();
        return this;
    }

    @Override
    public SocketCollectionFuture sync() throws InterruptedException {
        super.sync();
        return this;
    }

    @Override
    public ChannelGroupException cause() {
        return (ChannelGroupException) super.cause();
    }

    private void setSuccess0() {
        super.setSuccess(null);
    }

    private void setFailure0(ChannelGroupException cause) {
        super.setFailure(cause);
    }

    @Override
    public SocketCollectionFuture setSuccess(Void result) {
        throw new IllegalStateException();
    }

    @Override
    public boolean trySuccess(Void result) {
        throw new IllegalStateException();
    }

    @Override
    public SocketCollectionFuture setFailure(Throwable cause) {
        throw new IllegalStateException();
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        throw new IllegalStateException();
    }

    @Override
    protected void checkDeadLock() {
        EventExecutor e = executor();
        if (e != null && e != ImmediateEventExecutor.INSTANCE && e.inEventLoop()) {
            throw new BlockingOperationException();
        }
    }
}
