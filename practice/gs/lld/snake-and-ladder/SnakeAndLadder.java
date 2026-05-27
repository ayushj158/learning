import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import java.util.List;

public class SnakeAndLadder {

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
    private Board board;
    private Dice dice;
    private List<Player> players;
    private int currentTurn    = 0;
    private String winner;

    private Game(Board board, List<Player> players) {
        this.board = board;
        this.players = players;
        this.dice = new Dice();
    }

    public static class Builder  {
        List<Player> players ;
        Board board;
        
        public Builder(){
            this.board = new Board();
            this.players = new ArrayList<>();
        }
        public Builder  addPlayer(String playerName){
            players.add(new Player(playerName));
            return this;
        }

        public Builder addSnake(int head, int tail){
            board.addSnake(head,tail);
            return this;
        }
        
        public Builder  addLadder(int bottom, int top){
            board.addLadder(bottom,top);
            return this;
        }

        Game build(){
            return new Game(board, players);
        }
    }

    public String playTurn() {

        if(this.winner!=null){
            return "GAME already finished and winner was" + winner;
        }

        String message = "";
        Player currentPlayer = players.get(currentTurn);
        int currentPosition = currentPlayer.currentPosition;
        int finalPosition = 0;
        int newRoll = dice.roll();

        int newPosition = currentPosition + newRoll;
        if(newPosition == 100) {
            this.winner = currentPlayer.playerName;
            finalPosition = newPosition;
            message = currentPlayer.playerName + " rolled " + newRoll + " → moved to " + finalPosition +" and has won the game";
        } else if(newPosition>100){
            finalPosition = currentPosition;
            message = currentPlayer.playerName + " rolled " + newRoll + " and stays at samePosition=" +currentPosition;
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

        currentPlayer.currentPosition = finalPosition;
        currentTurn++;
        this.currentTurn = currentTurn % players.size();
        
        return message;
    }
    public String getWinner() { return winner; }
   
}

class Board {

    private static final int SIZE = 100;
    // snakes and ladders both stored here
    private Map<Integer, Integer> teleport = new HashMap<>();

    void addSnake(int head, int tail)     { teleport.put(head, tail); }
    void addLadder(int bottom, int top)   { teleport.put(bottom, top); }
    int getFinalPosition(int position)    { return teleport.getOrDefault(position, position); }
}

class Player {

    String playerName;
    int currentPosition;

    public Player(String playerName) {
        this.playerName = playerName;
        this.currentPosition = 0;
    }
}

class Dice{

    int min =1;
    int max = 6;

    public Dice() {
    }

    public int roll(){
        return new Random().nextInt(this.min, this.max + 1);
    }
}
