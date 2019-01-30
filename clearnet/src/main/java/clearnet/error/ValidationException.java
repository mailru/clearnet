package clearnet.error;

public class ValidationException extends ClearNetworkException {
    private final Object model;

    {
        kind = KIND.VALIDATION;
    }

    public ValidationException(String message, Object model) {
        super(message);
        this.model = model;
    }

    public Object getModel() {
        return model;
    }
}
