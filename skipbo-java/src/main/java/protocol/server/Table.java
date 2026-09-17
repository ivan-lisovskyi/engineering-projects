package protocol.server;

import protocol.Command;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Server Command
 * Informs the requested client about the whole table layout
 */
public class Table implements Command {

    public static final String COMMAND = "TABLE";
    public String buildingPile1;
    public String buildingPile2;
    public String buildingPile3;
    public String buildingPile4;
    public PlayerTable[] playerDetails;

    public Table(PlayerTable[] playerDetails, String buildingPile1, String buildingPile2, String buildingPile3, String buildingPile4){
        this.playerDetails = playerDetails;
        this.buildingPile1 = buildingPile1;
        this.buildingPile2 = buildingPile2;
        this.buildingPile3 = buildingPile3;
        this.buildingPile4 = buildingPile4;
    }

    public static class PlayerTable {
        private String playerName;
        private String stockTop;
        private String discardPile1;
        private String discardPile2;
        private String discardPile3;
        private String discardPile4;

        public PlayerTable(String playerName, String stockTop,
                           String discardPile1, String discardPile2, String discardPile3, String discardPile4) {
            this.playerName = playerName;
            this.stockTop = stockTop;
            this.discardPile1 = discardPile1;
            this.discardPile2 = discardPile2;
            this.discardPile3 = discardPile3;
            this.discardPile4 = discardPile4;
        }

        public String toString(){
            return playerName
                    + VALUE_SEPERATOR
                    + (stockTop == null ? "X" : stockTop)
                    + VALUE_SEPERATOR
                    + (discardPile1 == null ? "X" : discardPile1)
                    + VALUE_SEPERATOR
                    + (discardPile2 == null ? "X" : discardPile2)
                    + VALUE_SEPERATOR
                    + (discardPile3 == null ? "X" : discardPile3)
                    + VALUE_SEPERATOR
                    + (discardPile4 == null ? "X" : discardPile4);
        }
    }
    @Override
    public String transformToProtocolString() {
        return COMMAND
                + SEPERATOR
                + (buildingPile1 == null ? "X" : buildingPile1)
                + VALUE_SEPERATOR
                + (buildingPile2 == null ? "X" : buildingPile2)
                + VALUE_SEPERATOR
                + (buildingPile3 == null ? "X" : buildingPile3)
                + VALUE_SEPERATOR
                + (buildingPile4 == null ? "X" : buildingPile4)
                + SEPERATOR
                + Stream.of(playerDetails).map(PlayerTable::toString).collect(Collectors.joining(LIST_SEPERATOR));
    }
}
