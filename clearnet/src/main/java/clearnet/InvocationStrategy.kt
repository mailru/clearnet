package clearnet

import clearnet.InvocationBlockType.*
import clearnet.interfaces.IInvocationStrategy
import clearnet.interfaces.IInvocationStrategy.Decision


enum class InvocationStrategy(
        override val algorithm: Map<InvocationBlockType, Decision>,
        override val metaData: Map<String, String> = emptyMap()
): IInvocationStrategy {
    NO_CACHE(mapOf(
            INITIAL to Decision(GET_FROM_NET),
            CHECK_AUTH_TOKEN to Decision(GET_FROM_NET, DELIVER_ERROR),
            GET_FROM_NET to Decision(DELIVER_RESULT, RESOLVE_ERROR)
    )),

    REQUEST_EXCLUDE_CACHE(mapOf(
            INITIAL to Decision(GET_FROM_NET),
            CHECK_AUTH_TOKEN to Decision(GET_FROM_NET, DELIVER_ERROR),
            GET_FROM_NET to Decision(arrayOf(DELIVER_RESULT, SAVE_TO_CACHE), RESOLVE_ERROR)
    )),

    PRIORITY_REQUEST(mapOf(
            INITIAL to Decision(GET_FROM_NET),
            CHECK_AUTH_TOKEN to Decision(GET_FROM_NET, DELIVER_ERROR),
            GET_FROM_NET to Decision(arrayOf(DELIVER_RESULT, SAVE_TO_CACHE), GET_FROM_CACHE),
            GET_FROM_CACHE to Decision(DELIVER_RESULT, RESOLVE_ERROR)
    )),

    PRIORITY_CACHE(mapOf(
            INITIAL to Decision(GET_FROM_CACHE),
            CHECK_AUTH_TOKEN to Decision(GET_FROM_CACHE, DELIVER_ERROR),
            GET_FROM_CACHE to Decision(DELIVER_RESULT, GET_FROM_NET),
            GET_FROM_NET to Decision(arrayOf(DELIVER_RESULT, SAVE_TO_CACHE), RESOLVE_ERROR)
    )),

    RETRY_IF_NO_NETWORK(emptyMap(), mapOf("retry_network_error" to "true")),

    AUTHORIZED_REQUEST(mapOf(
            INITIAL to Decision(CHECK_AUTH_TOKEN)
    ));
}
