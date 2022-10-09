package one.kuring;

import one.kuring.collections.IntObjectMap;

import java.util.Map;

class PollRing extends Ring {
    PollRing(int entries, int flags, int sqThreadIdle, int sqThreadCpu, int cqSize, int attachWqRingFd, boolean withBufRing, int bufRingBufSize, int numOfBuffers, IntObjectMap<Command<?>> commands, Map<Integer, Long> commandExecutionStart) {
        super(entries, flags, sqThreadIdle, sqThreadCpu, cqSize, attachWqRingFd, withBufRing, bufRingBufSize, numOfBuffers, commands, commandExecutionStart);
    }

    @Override
    void park() {
        throw new UnsupportedOperationException("Can't park poll ring");
    }

    @Override
    void unpark() {
        throw new UnsupportedOperationException("Can't unpark poll ring");
    }
}
