package clearnet.error

class UnknownExternalException (message: String?) : ClearNetworkException(message) {

    init {
        kind = ClearNetworkException.KIND.UNKNOWN_EXTERNAL_ERROR
    }
}