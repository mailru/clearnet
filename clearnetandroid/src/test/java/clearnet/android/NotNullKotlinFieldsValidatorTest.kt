package clearnet.android

import clearnet.android.help.JavaModel
import clearnet.error.ValidationException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NotNullKotlinFieldsValidatorTest {
    private lateinit var validator: NotNullFieldsValidator
    private val gson = Gson()

    @Before
    fun setUp() {
        validator = NotNullFieldsValidator(true)
    }

    @Test
    fun testDeserialize() {
        var item = convert("right", Item::class.java)

        assertNotNull(item.getPrivateRequired())
        assertNotNull(item.optional)

        item = convert("required", Item::class.java)
        assertNotNull(item.getPrivateRequired())

        val list: ArrayList<Item> = convert("arrayRight", ItemsArrayList::class.java)

        assertEquals(list.size.toLong(), 1)


        val array: Array<Item> = convert("arrayRight", object : TypeToken<Array<Item>>() {}.rawType as Class<Array<Item>>)
        assertEquals(array.size.toLong(), 1)

        val map: Map<String, Item> = convert("mapRight", StringToItemMap::class.java)

        assertNull(map["one"])
        item = map["two"]
        assertNotNull(item!!.getPrivateRequired())
        assertNotNull(item.optional)
        item = map["three"]
        assertNotNull(item!!.getPrivateRequired())


        var extended = convert("wrong", Included::class.java)
        assertNull(extended.item)

        extended = convert("includedRight", Included::class.java)
        assertNotNull(extended.item)
        assertNotNull(extended.item!!.getPrivateRequired())


        try {
            convert("wrong", Item::class.java)
            fail()
        } catch (e: ValidationException) {

        }

        try {
            convert("arrayWrong", ItemsArrayList::class.java)
            fail()
        } catch (e: ValidationException) {

        }

        try {
            convert("arrayWrong", object : TypeToken<Array<Item>>() {}.rawType as Class<Array<Item>>)
            fail()
        } catch (e: ValidationException) {

        }

        try {
            convert("mapWrong", StringToItemMap::class.java)
            fail()
        } catch (e: ValidationException) {

        }

        try {
            convert("includedWrong", Included::class.java)
            fail()
        } catch (e: ValidationException) {

        }

        try {
            convert("wrong", ItemExtended::class.java)
            fail()
        } catch (e: ValidationException) {

        }
    }


    @Test
    fun testPackages() {
        validator = NotNullFieldsValidator(true, "another.package")
        try {
            convert("wrong", Item::class.java, validator)
        } catch (e: ValidationException) {
            fail()
        }

        validator = NotNullFieldsValidator(true, Item::class.java.`package`.name)
        try {
            convert("wrong", Item::class.java, validator)
            fail()
        } catch (e: ValidationException) {
        }
    }

    @Test
    fun ignoreJavaClasses() {
        try {
            convert("wrong", JavaModel::class.java)
        } catch (e: ValidationException){
            fail()
        }
    }


    @Throws(ValidationException::class)
    private fun <T> convert(request: String, type: Class<T>, validator: NotNullFieldsValidator = this.validator) = gson.fromJson(getStringForResponse(request), type).apply {
        validator.validate(this)
    }


    private fun getStringForResponse(request: String) = when (request) {
        "right" -> "{\"required\":\"yes\", \"optional\":\"no\"}"
        "required" -> "{\"required\":\"yes\"}"
        "arrayRight" -> "[{\"required\":\"yes\"}]"
        "arrayWrong" -> "[{}]"
        "includedRight" -> "{\"item\":{\"required\":\"yes\"}}"
        "includedWrong" -> "{\"item\":{}}"
        "mapRight" -> "{\"one\":null,\"two\":{\"required\":\"yes\", \"optional\":\"no\"},\"three\":{\"required\":\"yes\"}}"
        "mapWrong" -> "{\"one\":null,\"two\":{\"optional\":\"no\"},\"three\":{\"required\":\"yes\"}}"
        else -> "{}"
    }


    open class Item(
            private val required: String,
            var optional: String?,
            var requiredPrimitive: Int = 0   // must ignore annotation
    ) {
        fun getPrivateRequired() = required
    }

    class ItemExtended(
            one: String, two: String?, three: Int,
            private val rr: String?
    ) : Item(one, two, three)

    class Included {
        var item: Item? = null
    }


    class ItemsArrayList : ArrayList<Item>()
    class StringToItemMap : HashMap<String, Item>()
}