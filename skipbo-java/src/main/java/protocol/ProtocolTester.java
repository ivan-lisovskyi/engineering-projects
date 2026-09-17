package protocol;

import protocol.client.Play;
import protocol.common.Card;
import protocol.common.position.HandPosition;
import protocol.common.position.NumberedPilePosition;

public class ProtocolTester {
    public static void main (String[] args) throws ProtocolException {

        Command play = new Play(
            new HandPosition(new Card(2)),
            new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, 0)
        );
        System.out.println("Line: " + play.transformToProtocolString());
    }
}
