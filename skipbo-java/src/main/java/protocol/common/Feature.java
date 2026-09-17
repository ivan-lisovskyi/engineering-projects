package protocol.common;

public enum Feature {
    CHAT('C'),
    LOBBY('L'),
    MASTER('M');

    private char letter;
    Feature(char letter){
        this.letter = letter;
    }

    public char getLetter(){
        return this.letter;
    }
}
