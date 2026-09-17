package protocol.common.position;


import protocol.Command;

public class NumberedPilePosition implements Position {
    private Pile pile;
    private int number;

    public NumberedPilePosition(Pile pile, int number){
        this.pile = pile;
        this.number = number;
    }

    public String toString(){
        return pile + Command.VALUE_SEPERATOR + number;
    }

    public enum Pile {
        BUILDING_PILE("B"),
        DISCARD_PILE("D");

        private String rep;

        Pile(String rep){
            this.rep = rep;
        }

        public String toString(){
            return rep;
        }
    }

}
