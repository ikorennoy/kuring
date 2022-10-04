package one.kuring;

interface CompletionCallback {
    void handle(int res, int flags, long userData);
}
