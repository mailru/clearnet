package clearnet

enum class InvocationBlockType {
    INITIAL, GET_FROM_CACHE, GET_FROM_NET, SAVE_TO_CACHE, DELIVER_RESULT, DELIVER_ERROR, RESOLVE_ERROR, CHECK_AUTH_TOKEN;

    object Comparator : java.util.Comparator<InvocationBlockType> {
        override fun compare(p0: InvocationBlockType?, p1: InvocationBlockType?): Int {
            return InvocationBlockType.values().indexOf(p0).compareTo(InvocationBlockType.values().indexOf(p1))
        }
    }
}