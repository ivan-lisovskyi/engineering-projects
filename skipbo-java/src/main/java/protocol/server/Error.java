package protocol.server;

import protocol.Command;
import protocol.common.ErrorCode;

/**
 * Server Command
 * Indicates something went wrong
 */
public class Error implements Command {

    public static final String COMMAND = "ERROR";
    public ErrorCode errorCode;

    public Error(ErrorCode errorCode){
        this.errorCode = errorCode;
    }

    @Override
    public String transformToProtocolString() {
        return COMMAND + SEPERATOR + errorCode.getCode();
    }
}
