package clearnet.help

import clearnet.interfaces.IRequestExecutor
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject

open class BatchTestRequestExecutor : IRequestExecutor {
    override fun executeGet(headers: Map<String, String>, queryParams: Map<String, String>): Pair<String, Map<String, String>> {
        throw UnsupportedOperationException("No implementation")
    }

    override fun executePost(body: String, headers: Map<String, String>, queryParams: Map<String,String>): Pair<String, Map<String, String>> {
        val result: Any
        if(body.startsWith("{")){
            val requestObject = JSONObject(body)
            result = mapOf(
                    "result" to "test0",
                    "id" to requestObject.getInt("id")
            )
        } else {
            val array = JSONArray(body)

            result = mutableListOf<Map<String, Any>>()
            for (i in 0 until array.length()) {
                result += mapOf(
                        "result" to "test$i",
                        "id" to array.getJSONObject(i).getInt("id")
                )
            }
        }

        return Pair(
                Gson().toJson(result),
                mapOf("testHeader" to "test", "testHeader2" to "test2")
        )
    }
}