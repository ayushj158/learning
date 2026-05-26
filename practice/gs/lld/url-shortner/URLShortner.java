import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class URLShortner {
    private final Map<String, String> cache = new ConcurrentHashMap<>();  // Redis simulation
    private final Map<String, URLEntry> db  = new ConcurrentHashMap<>();  // DB simulation
    private final SnowflakeIDGenerator idGenerator;
    private static final String BASE_URL = "https://short.gs/";
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    

    public URLShortner(long serverId) {
        this.idGenerator = new SnowflakeIDGenerator(serverId);
    }

    public static void main(String[] args) {
        ExecutorService executors = Executors.newFixedThreadPool(1000);
        List<Future<String>> futures = new ArrayList<>();
        Random rand = new Random();
        for(int j=0;j<100; j++){
           int count = j;
           Future<String> query = executors.submit(()->{
                System.out.println("Generating count="+ count);
                StringBuilder sb = new StringBuilder();
                for (int i=0; i<6; i++){
                    sb.append(CHARS.charAt(rand.nextInt(62)));
                }

                String s = sb.toString();
                System.out.println(s);
                return s;
            });
            futures.add(query);
        }
    }

    private String encode(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(CHARS.charAt((int)(num % 62)));
            num /= 62;
        }
        return sb.reverse().toString();
    }

    public String create(String longUrl) {

        long nextId = idGenerator.generate();
        String id = encode(nextId);
        cache.put(id, longUrl);
        db.put(id, new URLEntry(id, longUrl));
        return BASE_URL + id;
     }
    
    public String resolve(String shortCode) throws Exception        { 
        
        if(cache.containsKey(shortCode)){
            return cache.get(shortCode);
        } else if(db.containsKey(shortCode)) {
            return db.get(shortCode).longUrl;
        } else {
            throw new Exception("Invalid short code");
        }
    }
    
    public void deactivate(String shortCode)       {
         if(cache.containsKey(shortCode)){
            cache.remove(shortCode);
         }

         if(db.containsKey(shortCode)){
            URLEntry entry = db.get(shortCode);
            entry.isActive = false;
            db.put(shortCode, entry);
         }
     }
    public URLEntry getDetails(String shortCode) throws Exception   { 

        if(db.containsKey(shortCode)) {
            return db.get(shortCode);
        } else {
            throw new Exception("Invalid short code");
        }
    }
}

class URLEntry {
    String shortCode;
    String longUrl;
    boolean isActive;
    long createdAt;

    public URLEntry(String shortCode, String longUrl) {
        this.shortCode = shortCode;
        this.longUrl   = longUrl;
        this.isActive  = true;
        this.createdAt = System.currentTimeMillis();
    }
}

class SnowflakeIDGenerator {
    long serverId;
   
    private static final long EPOCH = LocalDateTime.of(2024, Month.NOVEMBER, 14,01,00).toInstant(ZoneOffset.UTC).toEpochMilli();
    private static final long TIMESTAMP_SHIFT = 22L;
    private static final long SERVER_ID_SHIFT = 12L;
    private static final long MAX_SEQUENCE = 4095L ; // ~(-1L << 12)
    private long sequence;

    private long lastTimestamp;


    public SnowflakeIDGenerator(long serverId){
        this.serverId = serverId;
        this.sequence = 0L;   // current sequence within this ms
        this.lastTimestamp = System.currentTimeMillis() - EPOCH;
    }

    public synchronized long generate(){
        //(ms since 1970)
        // EPOCH = 1699920000000  (ms since 1970 for start date Nov 14 2023)
        // now = 580000000      (ms since your start date)
        long now = System.currentTimeMillis() - EPOCH; 

        if (now == lastTimestamp){
            // same millisecond — increment sequence
            sequence = (sequence + 1) & MAX_SEQUENCE;

            if (sequence == 0) {
                // all 4096 slots used this ms — wait for next ms so id does not have collison as now timestamp changes
                while (lastTimestamp == (now = System.currentTimeMillis()- EPOCH)) {}
            }
        } else { 
            // new millisecond — reset sequence to 0
            sequence = 0L;
        }

        // remember this ms for next call
        lastTimestamp = now;


         // Part 3: stitch all parts together
        return (now << TIMESTAMP_SHIFT)   // bits 63-22
             | (serverId << SERVER_ID_SHIFT)   // bits 21-12
             | sequence; 
    }
}