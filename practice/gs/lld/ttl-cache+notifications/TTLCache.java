import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class TTLCache {

    Map<String, CacheEntry> cache = new HashMap<>();
    PriorityQueue<CacheEntry> queue;

    public TTLCache(){  
        this.cache = new HashMap<>();
        this.queue = new PriorityQueue<>((a,b) -> Long.compare(a.getExpiryTime(), b.getExpiryTime()));
    }

    public void put(String key, Object value, long ttlMs) {

        long expiryTime = System.currentTimeMillis() + ttlMs;
        CacheEntry obj = new  CacheEntry(key, value, expiryTime);
        cache.put(key, obj);
        queue.offer(obj);

    }

    public Object get(String key) throws Exception {
       if (!cache.containsKey(key) ||  cache.get(key).getExpiryTime() < System.currentTimeMillis()) throw new Exception("No object found");
              
       
       return cache.get(key).getValue();
    }

    void evict() {
        while(!queue.isEmpty()){
            long expirtyTime = queue.peek().getExpiryTime();
            if(System.currentTimeMillis() < expirtyTime){
                // alive objects
                break;
            } else{
                CacheEntry entry =  queue.poll();
                cache.remove(entry.getKey());
            }
        }
    }

}


class CacheEntry {
    String key;
    Object value;
    long expirtyTime;

    public CacheEntry(String key, Object value, long expirtyTime ){
        this.key = key;
        this.value = value;
        this.expirtyTime = expirtyTime;
    }

    public long getExpiryTime(){
        return this.expirtyTime;
    }

    public String getKey(){
        return this.key;
    }
    public Object getValue(){
        return this.value;
    }
}
