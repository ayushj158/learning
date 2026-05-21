import java.util.concurrent.locks.ReentrantLock;

public class TokenBucketRateLimiter {

    private final ReentrantLock lock = new ReentrantLock();
    private final int capacity;
    private final double refillRateInMs;
    private double availableTokens;
    private long lastRefillTime;

    public TokenBucketRateLimiter(int capacity, long refillRateInMs){
        this.capacity = capacity;
        this.availableTokens = capacity;
        this.refillRateInMs = refillRateInMs;
        this.lastRefillTime = System.currentTimeMillis();
    }

    public boolean isAllowed(){

        lock.lock();
        try{
            long now = System.currentTimeMillis();
            long elapsedTime = now - lastRefillTime;
            double newAquiredTokens = (double) (elapsedTime * refillRateInMs);
            availableTokens = Math.min(capacity, availableTokens + newAquiredTokens) ;
            lastRefillTime = now;
            
            if (availableTokens >= 1){
                availableTokens--;
                return true;
            }
        } finally {
            lock.unlock();
        }
        return false;
    }

}
