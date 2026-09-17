package protocol;

public interface Command {

    static final String SEPERATOR = "~" ;
    static final String LIST_SEPERATOR = ",";
    static final String VALUE_SEPERATOR = ".";

    String transformToProtocolString();
}
