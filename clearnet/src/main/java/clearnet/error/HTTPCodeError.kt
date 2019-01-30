package clearnet.error

class HTTPCodeError(val code: Int, val response: String?) : ClearNetworkException("Http code error: $code") {
    init {
        kind = KIND.HTTP_CODE
    }
}