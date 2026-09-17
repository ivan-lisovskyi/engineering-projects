package protocol.common;

public enum ErrorCode {

    INVALID_PLAYER_NAME("001"),
    NAME_IN_USE("002"),
    PLAYER_DISCONNECTED("103"),
    INVALID_COMMAND("204"),
    COMMAND_NOT_ALLOWED("205"),
    INVALID_MOVE("206");

    private String code;

    ErrorCode(String code){
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
