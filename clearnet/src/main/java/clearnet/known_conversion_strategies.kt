package clearnet.conversion

import clearnet.interfaces.ConversionStrategy
import clearnet.interfaces.ConversionStrategy.ConversionStrategyError
import clearnet.model.RpcErrorResponse
import clearnet.model.RpcInnerError
import clearnet.model.RpcInnerErrorResponse
import org.json.JSONObject

open class DefaultConversionStrategy : ConversionStrategy {
    override fun checkErrorOrResult(response: JSONObject): String? {
        checkOuterError(response)
        return response.optString("result")
    }
}

fun ConversionStrategy.checkOuterError(response: JSONObject) {
    if (response.has("error")) {
        throw ConversionStrategyError(response.optString("error"), RpcErrorResponse::class.java)
    }
}