## Problem 26 — Parking Lot

> Design a parking lot with multiple floors. Two types of spots: 2-wheeler and 4-wheeler. Support park, unpark, and availability check.

```
parkingLot.park("KA01", VehicleType.CAR)    → Ticket(floor=1, spot=3)
parkingLot.park("KA02", VehicleType.BIKE)   → Ticket(floor=1, spot=1)
parkingLot.unpark(ticket)                   → spot freed
parkingLot.available(VehicleType.CAR)       → 23
```

---

Before coding — design the domain model first:

```
ParkingLot
 └── List<Floor>
       └── List<ParkingSpot>
             └── SpotType (TWO_WHEELER, FOUR_WHEELER)
             └── isOccupied
             └── Vehicle (if occupied)

Ticket
 └── vehicleNumber
 └── floor
 └── spotId
 └── entryTime
```

---

Two questions:

1. When a car comes in — how do you find the first available spot efficiently? What data structure per floor?
2. Where does the "find spot + mark occupied" logic live — in `Floor` or `ParkingLot`?

Have a go.

Perfect approach — clean and efficient. Let me refine slightly:

---

## Your idea refined

```java
// available spots per type — quick lookup
Map<VehicleType, Queue<ParkingSpot>> available = new ConcurrentHashMap<>();

// occupied spots — for unpark lookup
Map<String, Ticket> activeTickets = new ConcurrentHashMap<>(); 
// key = ticketId
```

**Why Queue instead of Map for available?**

You don't care WHICH spot — just give me the next available one. Queue gives O(1) poll() — perfect.

---

Now code it — here's the skeleton:

```java
enum VehicleType { BIKE, CAR }

class ParkingSpot {
    int spotId;
    int floor;
    VehicleType type;

    public ParkingSpot(int spotId, int floor, VehicleType type) {
        this.spotId = spotId;
        this.floor  = floor;
        this.type   = type;
    }
}

class Ticket {
    String ticketId;
    String vehicleNumber;
    ParkingSpot spot;
    long entryTime;

    public Ticket(String vehicleNumber, ParkingSpot spot) {
        this.ticketId      = UUID.randomUUID().toString();
        this.vehicleNumber = vehicleNumber;
        this.spot          = spot;
        this.entryTime     = System.currentTimeMillis();
    }
}

class ParkingLot {
    // available spots per vehicle type
    private final Map<VehicleType, Queue<ParkingSpot>> available
                                        = new ConcurrentHashMap<>();

    // active tickets — ticketId → ticket
    private final Map<String, Ticket> activeTickets
                                        = new ConcurrentHashMap<>();

    public ParkingLot(int floors, int bikePerFloor, int carPerFloor) {
        available.put(VehicleType.BIKE, new ConcurrentLinkedQueue<>());
        available.put(VehicleType.CAR,  new ConcurrentLinkedQueue<>());

        // initialize spots
        for (int f = 1; f <= floors; f++) {
            for (int s = 1; s <= bikePerFloor; s++)
                available.get(VehicleType.BIKE)
                         .offer(new ParkingSpot(s, f, VehicleType.BIKE));
            for (int s = 1; s <= carPerFloor; s++)
                available.get(VehicleType.CAR)
                         .offer(new ParkingSpot(s, f, VehicleType.CAR));
        }
    }

    public Ticket park(String vehicleNumber, VehicleType type) {
        // 1. poll from available queue
        // 2. if null → lot full
        // 3. create ticket
        // 4. store in activeTickets
        // 5. return ticket
    }

    public void unpark(String ticketId) {
        // 1. get ticket from activeTickets
        // 2. remove from activeTickets
        // 3. return spot to available queue
    }

    public int available(VehicleType type) {
        // return size of available queue for type
    }
}
```

Fill in the three methods — all short. Go ahead.