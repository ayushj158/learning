## Building intuition first

---

## Core entities

```
TrafficLight  → one light, has state (RED/YELLOW/GREEN)
               knows its direction (NORTH/SOUTH/EAST/WEST)

SignalGroup   → two opposite lights move together
               Group1: NORTH + SOUTH
               Group2: EAST + WEST

Intersection  → has two signal groups
               only one group GREEN at a time

Timer         → controls how long each state lasts
```

---

## How opposite roads stay in sync

```
NORTH and SOUTH always same state
EAST and WEST always same state

When Group1 (N/S) is GREEN → Group2 (E/W) is RED
When Group1 (N/S) is RED   → Group2 (E/W) is GREEN

So you only need to track ONE group's state
Other group is always opposite
```

---

## State Pattern — perfect fit here

Each signal has states: RED → GREEN → YELLOW → RED

```
State Pattern:
  State interface → handle(), next()
  ConcreteStates  → RedState, GreenState, YellowState
  Context         → TrafficLight holds current state

Each state knows:
  how long it lasts
  what state comes next
  what to do on entry
```

Why State Pattern?
```
Without it:
  if(state==RED) { ... }
  else if(state==GREEN) { ... }
  else if(state==YELLOW) { ... }
  → messy, hard to extend

With State Pattern:
  each state encapsulated in own class
  adding new state → new class, nothing else changes
  clean transitions
```

---

## State transition diagram

```
GREEN (30s) → YELLOW (5s) → RED (until other group done) → GREEN
```

---

## Now code it

```java
enum Direction  { NORTH, SOUTH, EAST, WEST }
enum SignalColor { RED, GREEN, YELLOW }

// State interface
interface SignalState {
    SignalColor getColor();
    int getDurationSeconds();
    SignalState next();  // what comes after this state
}

// Concrete states
class GreenState implements SignalState {
    public SignalColor getColor()      { return SignalColor.GREEN; }
    public int getDurationSeconds()    { return 30; }
    public SignalState next()          { return new YellowState(); }
}

class YellowState implements SignalState {
    public SignalColor getColor()      { return SignalColor.YELLOW; }
    public int getDurationSeconds()    { return 5; }
    public SignalState next()          { return new RedState(); }
}

class RedState implements SignalState {
    public SignalColor getColor()      { return SignalColor.RED; }
    public int getDurationSeconds()    { return 35; }  // 30+5 = other group's green+yellow
    public SignalState next()          { return new GreenState(); }
}

// TrafficLight — one per direction
class TrafficLight {
    Direction direction;
    SignalState currentState;

    public TrafficLight(Direction direction, SignalState initialState) {
        this.direction    = direction;
        this.currentState = initialState;
    }

    public void transition()           { currentState = currentState.next(); }
    public SignalColor getColor()      { return currentState.getColor(); }
    public int getDuration()           { return currentState.getDurationSeconds(); }
    public void setState(SignalState s){ currentState = s; }
}

// SignalGroup — two opposite lights
class SignalGroup {
    TrafficLight light1;
    TrafficLight light2;

    public SignalGroup(TrafficLight l1, TrafficLight l2) {
        this.light1 = l1;
        this.light2 = l2;
    }

    public void transition() {
        light1.transition();
        light2.transition();
    }

    public SignalColor getColor() { return light1.getColor(); }
}

// Intersection — orchestrates everything
class Intersection {
    private SignalGroup group1;  // NORTH + SOUTH
    private SignalGroup group2;  // EAST + WEST
    private ScheduledExecutorService scheduler;
    private boolean emergencyMode = false;
    private SignalState savedGroup1State;
    private SignalState savedGroup2State;

    public Intersection() {
        // group1 starts GREEN, group2 starts RED
        TrafficLight north = new TrafficLight(Direction.NORTH, new GreenState());
        TrafficLight south = new TrafficLight(Direction.SOUTH, new GreenState());
        TrafficLight east  = new TrafficLight(Direction.EAST,  new RedState());
        TrafficLight west  = new TrafficLight(Direction.WEST,  new RedState());

        group1 = new SignalGroup(north, south);
        group2 = new SignalGroup(east,  west);

        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void startSignals() {
        scheduleNextTransition();
    }

    private void scheduleNextTransition() {
        if (emergencyMode) return;  // don't schedule during emergency

        int duration = group1.getColor() == SignalColor.GREEN
            ? group1.light1.getDuration()   // group1 active
            : group2.light1.getDuration();  // group2 active

        scheduler.schedule(() -> {
            group1.transition();
            group2.transition();
            System.out.println("NORTH=" + group1.light1.getColor()
                             + " EAST="  + group2.light1.getColor());
            scheduleNextTransition();  // schedule next
        }, duration, TimeUnit.SECONDS);
    }

    public void emergencyOverride(Direction direction) {
        emergencyMode = true;

        // save current states for restore
        savedGroup1State = group1.light1.currentState;
        savedGroup2State = group2.light1.currentState;

        // set emergency direction to GREEN, all others RED
        if (direction == Direction.NORTH || direction == Direction.SOUTH) {
            group1.light1.setState(new GreenState());
            group1.light2.setState(new GreenState());
            group2.light1.setState(new RedState());
            group2.light2.setState(new RedState());
        } else {
            group2.light1.setState(new GreenState());
            group2.light2.setState(new GreenState());
            group1.light1.setState(new RedState());
            group1.light2.setState(new RedState());
        }

        System.out.println("EMERGENCY: " + direction + " is GREEN");
    }

    public void resumeNormalCycle() {
        emergencyMode = false;

        // restore saved states
        group1.light1.setState(savedGroup1State);
        group1.light2.setState(savedGroup1State);
        group2.light1.setState(savedGroup2State);
        group2.light2.setState(savedGroup2State);

        scheduleNextTransition();
        System.out.println("Normal cycle resumed");
    }

    public SignalColor getSignalState(Direction direction) {
        return switch (direction) {
            case NORTH, SOUTH -> group1.getColor();
            case EAST,  WEST  -> group2.getColor();
        };
    }

    public void shutdown() { scheduler.shutdown(); }
}
```

---

## Main — demo

```java
public class TrafficSignal {
    public static void main(String[] args) throws InterruptedException {
        Intersection intersection = new Intersection();
        intersection.startSignals();

        System.out.println("NORTH: " + intersection.getSignalState(Direction.NORTH));
        System.out.println("EAST:  " + intersection.getSignalState(Direction.EAST));

        // simulate emergency
        Thread.sleep(5000);
        intersection.emergencyOverride(Direction.EAST);

        Thread.sleep(3000);
        intersection.resumeNormalCycle();

        Thread.sleep(60000);  // watch normal cycle
        intersection.shutdown();
    }
}
```

---

## Key design decisions — say in interview

```
State Pattern:
  Each signal state (RED/GREEN/YELLOW) encapsulated
  Knows duration + next state
  Adding new state → new class only

SignalGroup:
  Opposite roads always in sync
  Single transition() updates both ← consistency

ScheduledExecutorService:
  Non-blocking — doesn't sleep main thread
  Auto-schedules next transition after duration

Emergency override:
  Save current state → override → restore on resume
  emergencyMode flag prevents scheduler interference
```

---

Traffic Signal ✅ done. **Payment System or HLD next?**