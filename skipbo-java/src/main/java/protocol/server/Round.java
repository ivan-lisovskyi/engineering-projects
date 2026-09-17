package protocol.server;

import protocol.Command;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Server Command
 * Informs the players in a game a round has finished, and a new game should be started
 */
public class Round implements Command {

    public static final String COMMAND = "ROUND";
    public Score[] scores;

    public Round(Score[] scores){
        this.scores = scores;
    }

    public static class Score {
        private String player;
        private Integer score;

        public Score(String player, Integer score){
            this.player = player;
            this.score = score;
        }
        public String toString(){
            return player + VALUE_SEPERATOR + score;
        }
    }
    @Override
    public String transformToProtocolString() {
        return COMMAND + SEPERATOR + Stream.of(scores).map(Score::toString).collect(Collectors.joining(LIST_SEPERATOR));
    }
}
