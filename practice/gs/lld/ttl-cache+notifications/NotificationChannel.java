import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


class NotificationService {
    // subscriptions
    private final Map<EventType, Map<String, Set<Channel>>> subscriptions
                                            = new ConcurrentHashMap<>();

    // channel implementations
    private final Map<Channel, NotificationChannel> channels = Map.of(
        Channel.EMAIL, new EmailChannel(),
        Channel.SMS,   new SMSChannel(),
        Channel.PUSH,  new PushChannel()
    );

    // async delivery
    private final ExecutorService executor = 
        Executors.newFixedThreadPool(10);

     // async delivery
    private final ExecutorService executor1 =  new ThreadPoolExecutor(10, 
        20,
         60,
          TimeUnit.MICROSECONDS,
        new LinkedBlockingQueue<>(1000)) ;

    public void subscribe(String userId, EventType eventType, Channel channel) {
       subscriptions.computeIfAbsent(eventType, k-> new ConcurrentHashMap<String, Set<Channel>>())
                    .computeIfAbsent(userId, k-> ConcurrentHashMap.newKeySet())
                    .add(channel);
    }

    public void unsubscribe(String userId, EventType eventType, Channel channel) {
        // get and remove
    }

    public void notify(EventType eventType, String message) {
    Map<String,Set<Channel>> map =  subscriptions.get(eventType);
    map.forEach((userId, userChannels) -> 
        userChannels.forEach(channel -> {
            executor1.submit(() -> 
              channels.get(channel).send(userId, message));
        }));
    }
}

enum Channel   { EMAIL, SMS, PUSH }
enum EventType { TRADE_EXECUTED, RISK_BREACH, PRICE_ALERT }

interface NotificationChannel {
    void send(String userId, String message);
}

class EmailChannel implements NotificationChannel {
    public void send(String userId, String message) {
        System.out.println("EMAIL to " + userId + ": " + message);
    }
}

class SMSChannel implements NotificationChannel {
    public void send(String userId, String message) {
        System.out.println("SMS to " + userId + ": " + message);
    }
}

class PushChannel implements NotificationChannel {
    public void send(String userId, String message) {
        System.out.println("PUSH to " + userId + ": " + message);
    }
}