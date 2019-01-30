package clearnet.error;

public abstract class ClearNetworkException extends Exception {
    protected KIND kind;

    public ClearNetworkException(){

    }

    public ClearNetworkException(String message){
        super(message);
    }

    public ClearNetworkException(Throwable cause){
        super(cause);
    }

    public ClearNetworkException(String message, Throwable cause){
        super(message, cause);
    }

    public KIND getKind(){
        return kind;
    }

    public enum KIND {
        NETWORK, VALIDATION, CONVERSION, RESPONSE_ERROR, HTTP_CODE, INTERRUPT_FLOW_REQUESTED, UNKNOWN_EXTERNAL_ERROR
    }
}
