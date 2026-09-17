package protocol.client;

import protocol.Command;

/**
 * Client Command
 * Command used to request a new game to play, with a specified amount of players
 */
public class Game implements Command {

    public static final String COMMAND = "GAME";
    public int numberOfPlayers;

    public Game(int numberOfPlayers){
        this.numberOfPlayers = numberOfPlayers;
    }

    @Override
    public String transformToProtocolString() {
        return COMMAND + Command.SEPERATOR + this.numberOfPlayers;
    }
}
