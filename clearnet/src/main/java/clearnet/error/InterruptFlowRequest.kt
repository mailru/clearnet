package clearnet.error

class InterruptFlowRequest(message: String) : ClearNetworkException(message) {

    init {
        kind = KIND.INTERRUPT_FLOW_REQUESTED
    }
}