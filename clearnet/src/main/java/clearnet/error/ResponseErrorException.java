package clearnet.error;

public class ResponseErrorException extends ClearNetworkException {
    public final Object error;

    {
        kind = KIND.RESPONSE_ERROR;
    }

    public ResponseErrorException(Object error){
        this.error = error;
    }
}
