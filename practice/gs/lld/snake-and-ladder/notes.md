## Snake and Ladder Game

> Design a Snake and Ladder board game for multiple players.

```
Board: 10×10 grid (100 cells, 1-100)
Players take turns rolling a dice (1-6)
Snakes: landing on snake head → slide to tail (go back)
Ladders: landing on ladder bottom → climb to top (go forward)
First player to reach exactly 100 wins

Example:
  Player1 at 5, rolls 3 → moves to 8
  Cell 8 has ladder to 34 → Player1 jumps to 34
  
  Player2 at 14, rolls 2 → moves to 16
  Cell 16 has snake head → slides to 4
```

**API:**
```java
void addPlayer(String playerId)
void addSnake(int head, int tail)
void addLadder(int bottom, int top)
int rollDice()
String playTurn(String playerId)  // returns result message
String getWinner()
```

---

## Before coding — four questions:

1. What are the core entities?
2. How do you store snakes and ladders efficiently for O(1) lookup?
3. What happens if player rolls and would go beyond 100?
4. What design pattern for board setup?

Take a shot.

Human's: go ahead


Really good — clean structure and Builder pattern is perfect. Three bugs to fix:

---

## Bug 1 — addLadder calls addSnake

```java
public Builder addLadder(int bottom, int top) {
    board.addSnake(bottom, top);  // ← wrong method!
    return this;
}

// fix:
public Builder addLadder(int bottom, int top) {
    board.addLadder(bottom, top);  // ← correct
    return this;
}
```

---

## Bug 2 — Dice range is wrong

```java
new Random().nextInt(this.min, this.max);
// nextInt(1, 6) → returns 1,2,3,4,5 only — never 6!
// nextInt(bound) is exclusive of upper bound

// fix:
new Random().nextInt(this.min, this.max + 1);
// nextInt(1, 7) → returns 1,2,3,4,5,6 ✓
```

---

## Bug 3 — playTurn doesn't stop after winner found

```java
// if winner already set, still processes turns
// fix — add guard at top:
public String playTurn() {
    if (winner != null) return "Game over! Winner: " + winner;
    // ... rest of method
}
```

---

## Minor improvement — message should mention snake/ladder

```java
} else {
    finalPosition = board.getFinalPosition(newPosition);
    
    // tell user if snake or ladder hit
    if (finalPosition > newPosition) {
        message = currentPlayer.playerName + " rolled " + newRoll 
                + " → landed on " + newPosition 
                + " → LADDER to " + finalPosition;
    } else if (finalPosition < newPosition) {
        message = currentPlayer.playerName + " rolled " + newRoll 
                + " → landed on " + newPosition 
                + " → SNAKE to " + finalPosition;
    } else {
        message = currentPlayer.playerName + " rolled " + newRoll 
                + " → moved to " + finalPosition;
    }
}
```

---

## Clean corrected version

```java
import java.util.*;

public class SnakeLadder {
    public static void main(String[] args) {
        Game game = new Game.Builder()
            .addPlayer("Alice")
            .addPlayer("Bob")
            .addSnake(16, 4)
            .addSnake(54, 34)
            .addSnake(62, 19)
            .addLadder(8, 34)
            .addLadder(20, 42)
            .addLadder(28, 76)
            .build();

        while (game.getWinner() == null) {
            System.out.println(game.playTurn());
        }
        System.out.println("Winner: " + game.getWinner());
    }
}

class Game {
    private final Board board;
    private final Dice dice;
    private final List<Player> players;
    private int currentTurn = 0;
    private String winner;

    private Game(Board board, List<Player> players) {
        this.board   = board;
        this.players = players;
        this.dice    = new Dice();
    }

    public String playTurn() {
        // guard — game already over
        if (winner != null) return "Game over! Winner: " + winner;

        Player player = players.get(currentTurn);
        int roll      = dice.roll();
        int newPos    = player.currentPosition + roll;
        String message;

        if (newPos == 100) {
            player.currentPosition = 100;
            winner = player.playerName;
            message = player.playerName + " rolled " + roll 
                    + " → reached 100 → WON! 🎉";

        } else if (newPos > 100) {
            message = player.playerName + " rolled " + roll 
                    + " → would go to " + newPos 
                    + " → stays at " + player.currentPosition;

        } else {
            int finalPos = board.getFinalPosition(newPos);
            player.currentPosition = finalPos;

            if (finalPos > newPos) {
                message = player.playerName + " rolled " + roll
                        + " → " + newPos + " → LADDER → " + finalPos;
            } else if (finalPos < newPos) {
                message = player.playerName + " rolled " + roll
                        + " → " + newPos + " → SNAKE  → " + finalPos;
            } else {
                message = player.playerName + " rolled " + roll
                        + " → moved to " + finalPos;
            }
        }

        // advance turn only if no winner
        if (winner == null) {
            currentTurn = (currentTurn + 1) % players.size();
        }

        return message;
    }

    public String getWinner() { return winner; }

    // ── Builder ──────────────────────────────────────────────
    public static class Builder {
        private final Board board   = new Board();
        private final List<Player> players = new ArrayList<>();

        public Builder addPlayer(String name)           { players.add(new Player(name)); return this; }
        public Builder addSnake(int head, int tail)     { board.addSnake(head, tail);    return this; }
        public Builder addLadder(int bottom, int top)   { board.addLadder(bottom, top);  return this; }  // ← fixed

        public Game build() {
            if (players.isEmpty()) throw new IllegalStateException("Need at least one player");
            return new Game(board, players);
        }
    }
}

class Board {
    private final Map<Integer, Integer> teleport = new HashMap<>();

    void addSnake(int head, int tail)   { teleport.put(head, tail); }
    void addLadder(int bottom, int top) { teleport.put(bottom, top); }
    int getFinalPosition(int pos)       { return teleport.getOrDefault(pos, pos); }
}

class Player {
    String playerName;
    int currentPosition = 0;

    Player(String playerName) { this.playerName = playerName; }
}

class Dice {
    private final Random random = new Random();
    int roll() { return random.nextInt(1, 7); }  // ← fixed: 1-6 inclusive
}
```

---

## What to say in interview

```
Builder pattern   → clean board setup, method chaining
Single teleport map → snakes and ladders both O(1) lookup
Guard at playTurn → prevents turns after game over
Message describes snake/ladder/stay clearly
```

---

Snake & Ladder ✅ done. **Traffic Signal, Payment, or HLD next?**