/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.util.Recycler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.internal.StringUtil;

import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import static io.netty.channel.DefaultChannelPipeline.*;
import static io.netty.channel.EventLoopUtil.*;

/**
 * Abstract base class for {@link EventLoop}s that execute all its submitted tasks in a single thread.
 *
 */
public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop {

    protected SingleThreadEventLoop(EventLoopGroup parent, ThreadFactory threadFactory, boolean addTaskWakesUp) {
        super(parent, threadFactory, addTaskWakesUp);
    }

    protected SingleThreadEventLoop(EventLoopGroup parent, Executor executor, boolean addTaskWakesUp) {
        super(parent, executor, addTaskWakesUp);
    }

    @Override
    public EventLoopGroup parent() {
        return (EventLoopGroup) super.parent();
    }

    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    @Override
    public void invokeChannelRegistered(final ChannelHandlerContext ctx) {
        if (inEventLoop()) {
            invokeChannelRegisteredNow(ctx);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    invokeChannelRegisteredNow(ctx);
                }
            });
        }
    }

    @Override
    public void invokeChannelActive(final ChannelHandlerContext ctx) {
        if (inEventLoop()) {
            invokeChannelActiveNow(ctx);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    invokeChannelActiveNow(ctx);
                }
            });
        }
    }

    @Override
    public void invokeChannelInactive(final ChannelHandlerContext ctx) {
        if (inEventLoop()) {
            invokeChannelInactiveNow(ctx);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    invokeChannelInactiveNow(ctx);
                }
            });
        }
    }

    @Override
    public void invokeExceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }

        if (inEventLoop()) {
            invokeExceptionCaughtNow(ctx, cause);
        } else {
            try {
                execute(new Runnable() {
                    @Override
                    public void run() {
                        invokeExceptionCaughtNow(ctx, cause);
                    }
                });
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to submit an exceptionCaught() event.", t);
                    logger.warn("The exceptionCaught() event that was failed to submit was:", cause);
                }
            }
        }
    }

    @Override
    public void invokeUserEventTriggered(final ChannelHandlerContext ctx, final Object event) {
        if (event == null) {
            throw new NullPointerException("event");
        }

        if (inEventLoop()) {
            invokeUserEventTriggeredNow(ctx, event);
        } else {
            safeExecuteInbound(new Runnable() {
                @Override
                public void run() {
                    invokeUserEventTriggeredNow(ctx, event);
                }
            }, event);
        }
    }

    @Override
    public void invokeChannelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg == null) {
            throw new NullPointerException("msg");
        }

        if (inEventLoop()) {
            invokeChannelReadNow(ctx, msg);
        } else {
            safeExecuteInbound(new Runnable() {
                @Override
                public void run() {
                    invokeChannelReadNow(ctx, msg);
                }
            }, msg);
        }
    }

    @Override
    public void invokeChannelReadComplete(final ChannelHandlerContext ctx) {
        if (inEventLoop()) {
            invokeChannelReadCompleteNow(ctx);
        } else {
            DefaultChannelHandlerContext dctx = (DefaultChannelHandlerContext) ctx;
            Runnable task = dctx.invokeChannelReadCompleteTask;
            if (task == null) {
                dctx.invokeChannelReadCompleteTask = task = new Runnable() {
                    @Override
                    public void run() {
                        invokeChannelReadCompleteNow(ctx);
                    }
                };
            }
            execute(task);
        }
    }

    @Override
    public void invokeChannelWritabilityChanged(final ChannelHandlerContext ctx) {
        if (inEventLoop()) {
            invokeChannelWritabilityChangedNow(ctx);
        } else {
            DefaultChannelHandlerContext dctx = (DefaultChannelHandlerContext) ctx;
            Runnable task = dctx.invokeChannelWritableStateChangedTask;
            if (task == null) {
                dctx.invokeChannelWritableStateChangedTask = task = new Runnable() {
                    @Override
                    public void run() {
                        invokeChannelWritabilityChangedNow(ctx);
                    }
                };
            }
            execute(task);
        }
    }

    @Override
    public void invokeBind(
            final ChannelHandlerContext ctx, final SocketAddress localAddress, final ChannelPromise promise) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        validatePromise(ctx, promise, false);

        if (inEventLoop()) {
            invokeBindNow(ctx, localAddress, promise);
        } else {
            safeExecuteOutbound(new Runnable() {
                @Override
                public void run() {
                    invokeBindNow(ctx, localAddress, promise);
                }
            }, promise);
        }
    }

    @Override
    public void invokeConnect(
            final ChannelHandlerContext ctx,
            final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        validatePromise(ctx, promise, false);

        if (inEventLoop()) {
            invokeConnectNow(ctx, remoteAddress, localAddress, promise);
        } else {
            safeExecuteOutbound(new Runnable() {
                @Override
                public void run() {
                    invokeConnectNow(ctx, remoteAddress, localAddress, promise);
                }
            }, promise);
        }
    }

    @Override
    public void invokeDisconnect(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        validatePromise(ctx, promise, false);

        if (inEventLoop()) {
            invokeDisconnectNow(ctx, promise);
        } else {
            safeExecuteOutbound(new Runnable() {
                @Override
                public void run() {
                    invokeDisconnectNow(ctx, promise);
                }
            }, promise);
        }
    }

    @Override
    public void invokeClose(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        validatePromise(ctx, promise, false);

        if (inEventLoop()) {
            invokeCloseNow(ctx, promise);
        } else {
            safeExecuteOutbound(new Runnable() {
                @Override
                public void run() {
                    invokeCloseNow(ctx, promise);
                }
            }, promise);
        }
    }

    @Override
    public void invokeRead(final ChannelHandlerContext ctx) {
        if (inEventLoop()) {
            invokeReadNow(ctx);
        } else {
            DefaultChannelHandlerContext dctx = (DefaultChannelHandlerContext) ctx;
            Runnable task = dctx.invokeReadTask;
            if (task == null) {
                dctx.invokeReadTask = task = new Runnable() {
                    @Override
                    public void run() {
                        invokeReadNow(ctx);
                    }
                };
            }
            execute(task);
        }
    }

    @Override
    public void invokeWrite(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg == null) {
            throw new NullPointerException("msg");
        }

        validatePromise(ctx, promise, true);
        invokeWrite(ctx, msg, false, promise);
    }

    private void invokeWrite(ChannelHandlerContext ctx, Object msg, boolean flush, ChannelPromise promise) {

        if (inEventLoop()) {
            invokeWriteNow(ctx, msg, promise);
            if (flush) {
                invokeFlushNow(ctx);
            }
        } else {
            AbstractChannel channel = (AbstractChannel) ctx.channel();
            int size = channel.estimatorHandle().size(msg);
            if (size > 0) {
                ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
                // Check for null as it may be set to null if the channel is closed already
                if (buffer != null) {
                    buffer.incrementPendingOutboundBytes(size);
                }
            }
            safeExecuteOutbound(WriteTask.newInstance(ctx, msg, size, flush, promise), promise);
        }
    }

    @Override
    public void invokeFlush(final ChannelHandlerContext ctx) {
        if (inEventLoop()) {
            invokeFlushNow(ctx);
        } else {
            DefaultChannelHandlerContext dctx = (DefaultChannelHandlerContext) ctx;
            Runnable task = dctx.invokeFlushTask;
            if (task == null) {
                dctx.invokeFlushTask = task = new Runnable() {
                    @Override
                    public void run() {
                        invokeFlushNow(ctx);
                    }
                };
            }
            execute(task);
        }
    }

    @Override
    public void invokeWriteAndFlush(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg == null) {
            throw new NullPointerException("msg");
        }

        validatePromise(ctx, promise, true);

        invokeWrite(ctx, msg, true, promise);
    }

    private static void validatePromise(ChannelHandlerContext ctx, ChannelPromise promise, boolean allowVoidPromise) {
        if (ctx == null) {
            throw new NullPointerException("ctx");
        }

        if (promise == null) {
            throw new NullPointerException("promise");
        }

        if (promise.isDone()) {
            throw new IllegalArgumentException("promise already done: " + promise);
        }

        if (promise.channel() != ctx.channel()) {
            throw new IllegalArgumentException(String.format(
                    "promise.channel does not match: %s (expected: %s)", promise.channel(), ctx.channel()));
        }

        if (promise.getClass() == DefaultChannelPromise.class) {
            return;
        }

        if (!allowVoidPromise && promise instanceof VoidChannelPromise) {
            throw new IllegalArgumentException(
                    StringUtil.simpleClassName(VoidChannelPromise.class) + " not allowed for this operation");
        }

        if (promise instanceof AbstractChannel.CloseFuture) {
            throw new IllegalArgumentException(
                    StringUtil.simpleClassName(AbstractChannel.CloseFuture.class) + " not allowed in a pipeline");
        }
    }

    private void safeExecuteInbound(Runnable task, Object msg) {
        boolean success = false;
        try {
            execute(task);
            success = true;
        } finally {
            if (!success) {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    private void safeExecuteOutbound(Runnable task, ChannelPromise promise) {
        try {
            execute(task);
        } catch (Throwable cause) {
            promise.setFailure(cause);
        }
    }

    static final class WriteTask implements Runnable {
        private ChannelHandlerContext ctx;
        private Object msg;
        private ChannelPromise promise;
        private int size;
        private boolean flush;

        private static final Recycler<WriteTask> RECYCLER = new Recycler<WriteTask>() {
            @Override
            protected WriteTask newObject(Handle handle) {
                return new WriteTask(handle);
            }
        };

        private static WriteTask newInstance(
                ChannelHandlerContext ctx, Object msg, int size, boolean flush, ChannelPromise promise) {
            WriteTask task = RECYCLER.get();
            task.ctx = ctx;
            task.msg = msg;
            task.promise = promise;
            task.size = size;
            task.flush = flush;
            return task;
        }

        private final Recycler.Handle handle;

        private WriteTask(Recycler.Handle handle) {
            this.handle = handle;
        }

        @Override
        public void run() {
            try {
                if (size > 0) {
                    ChannelOutboundBuffer buffer = ctx.channel().unsafe().outboundBuffer();
                    // Check for null as it may be set to null if the channel is closed already
                    if (buffer != null) {
                        buffer.decrementPendingOutboundBytes(size);
                    }
                }
                invokeWriteNow(ctx, msg, promise);
                if (flush) {
                    invokeFlushNow(ctx);
                }
            } finally {
                // Set to null so the GC can collect them directly
                ctx = null;
                msg = null;
                promise = null;

                RECYCLER.recycle(this, handle);
            }
        }
    }
}
