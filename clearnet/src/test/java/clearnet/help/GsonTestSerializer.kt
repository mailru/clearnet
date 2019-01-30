package clearnet.help

import clearnet.interfaces.ISerializer
import com.google.gson.Gson
import java.lang.reflect.Type

class GsonTestSerializer : ISerializer {
    private val gson = Gson()

    override fun serialize(obj: Any?): String = gson.toJson(obj)

    override fun deserialize(body: String?, objectType: Type): Any? {
        try {
            return chooseBodyDestiny(body, objectType)
        } catch (e: Exception) {
            throw clearnet.error.ConversionException(e)
        }
    }

    private fun chooseBodyDestiny(body: String?, objectType: Type): Any? {
        if (objectType in listOf(CharSequence::class.java, String::class.java)) {
            return body
        } else {
            return gson.fromJson(body, objectType)
        }
    }
}