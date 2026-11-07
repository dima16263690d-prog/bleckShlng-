package com.dima16263690d.bleckshlng.client.render;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Render-thread handoff adapted from the modern Minecraft RenderSystem contract.
 *
 * Minecraft 1.12.2 uses the client thread as its OpenGL/render thread. This class
 * does not create another rendering thread; it only serializes GL work onto the
 * thread that owns the current OpenGL context.
 */
public final class RenderSystem112 {
    private static final Queue<Runnable> RENDER_CALLS = new ConcurrentLinkedQueue<Runnable>();
    private static volatile Thread renderThread;

    private RenderSystem112() {
    }

    /**
     * Binds the current client render thread. Forge calls this from RenderTickEvent.
     * Repeated calls from the same thread are harmless.
     */
    public static synchronized void initRenderThread() {
        Thread current = Thread.currentThread();

        if (renderThread == null) {
            renderThread = current;
        } else if (renderThread != current) {
            throw new IllegalStateException(
                    "Render thread is already owned by " + renderThread.getName()
                            + ", not " + current.getName());
        }
    }

    public static boolean isInitialized() {
        return renderThread != null;
    }

    public static boolean isOnRenderThread() {
        return Thread.currentThread() == renderThread;
    }

    public static void assertOnRenderThread() {
        if (!isOnRenderThread()) {
            Thread expected = renderThread;
            throw new IllegalStateException(
                    "OpenGL operation called off the render thread. Expected "
                            + (expected == null ? "<not initialized>" : expected.getName())
                            + ", actual " + Thread.currentThread().getName());
        }
    }

    /**
     * Queues a GL operation for the next client render tick.
     * The call is queued even when invoked from the render thread.
     */
    public static void recordRenderCall(Runnable call) {
        if (call == null) {
            throw new IllegalArgumentException("Render call must not be null");
        }

        RENDER_CALLS.add(call);
    }

    /**
     * Runs immediately on the render thread, otherwise queues the operation.
     */
    public static void execute(Runnable call) {
        if (call == null) {
            throw new IllegalArgumentException("Render call must not be null");
        }

        if (isOnRenderThread()) {
            call.run();
        } else {
            recordRenderCall(call);
        }
    }

    /**
     * Drains queued operations. Must be called only from the OpenGL owner thread.
     *
     * @return number of operations executed
     */
    public static int replayQueue() {
        assertOnRenderThread();

        int executed = 0;
        Runnable call;

        while ((call = RENDER_CALLS.poll()) != null) {
            call.run();
            executed++;
        }

        return executed;
    }

    public static int getPendingCallCount() {
        return RENDER_CALLS.size();
    }
}
