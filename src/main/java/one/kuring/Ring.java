package one.kuring;

import com.tdunning.math.stats.TDigest;
import one.kuring.collections.IntObjectMap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

abstract class Ring {
    final Uring ring;
    final CompletionQueue completionQueue;
    final SubmissionQueue submissionQueue;
    private final IntObjectMap<Command<?>> commands;
    private final Map<Integer, Long> commandExecutionBegin;
    private final CompletionCallback callback = this::handle;

    private final TDigest commandExecutionDelay = TDigest.createDigest(100.0);

    private final ReentrantLock lock = new ReentrantLock();

    private final IoUringBufRing bufRing;

    Ring(int entries, int flags, int sqThreadIdle, int sqThreadCpu, int cqSize, int attachWqRingFd, boolean withBufRing, int bufRingBufSize, int numOfBuffers, IntObjectMap<Command<?>> commands, Map<Integer, Long> commandExecutionBegin) {
        this.commands = commands;
        this.commandExecutionBegin = commandExecutionBegin;
        ring = Native.setupIoUring(entries, flags, sqThreadIdle, sqThreadCpu, cqSize, attachWqRingFd);
        submissionQueue = ring.getSubmissionQueue();
        completionQueue = ring.getCompletionQueue();

        if (withBufRing) {
            bufRing = new IoUringBufRing(ring.getRingFd(), bufRingBufSize, numOfBuffers);
        } else {
            bufRing = null;
        }
    }

    private boolean isIoringCqeFBufferSet(int flags) {
        return (flags & Native.IORING_CQE_F_BUFFER) == Native.IORING_CQE_F_BUFFER;
    }

    private void handle(int res, int flags, long data) {
        Command<?> command = commands.remove((int) data);
        long executionStart = commandExecutionBegin.remove((int) data);
        if (command != null) {
            if (res >= 0) {
                if (isIoringCqeFBufferSet(flags)) {
                    int bufferId = flags >> 16;
                    ByteBuffer buffer = bufRing.getBuffer(bufferId);
                    buffer.position(res);
                    command.complete(new BufRingResult(buffer, res, bufferId, this));
                } else {
                    command.complete(res);
                }
            } else {
                command.error(new IOException(String.format("Error code: %d; message: %s", -res, Native.decodeErrno(res))));
            }
        }
        try {
            lock.lock();
            commandExecutionDelay.add(Native.getCpuClock() - executionStart);
        } finally {
            lock.unlock();
        }

    }

    void close() {
        ring.close();
        bufRing.close();
    }

    abstract void park();

    abstract void unpark();

    boolean hasCompletions() {
        return completionQueue.hasCompletions();
    }

    int processCompletedTasks() {
        return completionQueue.processEvents(callback);
    }

    public double[] publishCommandExecutionDelays(double[] percentiles) {
        double[] res = new double[percentiles.length];
        try {
            lock.lock();
            for (int i = 0; i < percentiles.length; i++) {
                res[i] = commandExecutionDelay.quantile(percentiles[i]);
            }
        } finally {
            lock.unlock();
        }
        return res;
    }

    void recycleBuffer(int bufferId) {
        bufRing.recycleBuffer(bufferId);
    }

    <T> void addOperation(Command<T> op, long opId) {
        submissionQueue.enqueueSqe(
                op.getOp(),
                op.getFlags(),
                op.getRwFlags(),
                op.getFd(),
                op.getBufferAddress(),
                op.getLength(),
                op.getOffset(),
                opId,
                op.getBufIndex(),
                op.getFileIndex()
        );
    }

    void submitIo() {
        submissionQueue.submit(0);
    }

    boolean hasInKernel() {
        return submissionQueue.getTail() != completionQueue.getHead();
    }

    int getBufRingId() {
        return bufRing.getId();
    }

    int getBufferLength() {
        return bufRing.getBufferSize();
    }

    boolean isBufRingInitialized() {
        return bufRing != null;
    }

    boolean hasPending() {
        return submissionQueue.hasPending();
    }
}
