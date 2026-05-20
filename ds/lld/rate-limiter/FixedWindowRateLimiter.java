import java.util.concurrent.locks.ReentrantLock;

public class FixedWindowRateLimiter {

    private final ReentrantLock lock = new ReentrantLock();

    int counter;
    int limit;
    long windowSizeInMs;
    long windowStart;

    public FixedWindowRateLimiter(int limit, long windowSizeInMs){
        this.limit = limit;
        this.windowSizeInMs = windowSizeInMs;
        this.windowStart = System.currentTimeMillis();
        this.counter = 0;
    }

    public boolean isAllowed(){
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            if(now - windowStart > windowSizeInMs){
                counter =0;
                windowStart = now;
            }
            
            if (counter<limit) {
                counter++;
                return true;
            }
        } finally {
            lock.unlock();
        }

        return false;

    }
}