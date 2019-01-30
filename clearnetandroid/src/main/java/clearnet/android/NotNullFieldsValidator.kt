package clearnet.android

import clearnet.error.ValidationException
import clearnet.interfaces.IBodyValidator
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.javaField


/**
 * @param throwFirst If true the converter throws an exception when the first error was found
 * (recommended for production builds). Otherwise the converter will collect
 * error messages and will throw an exceptions with a message that contains
 * full list of errors (recommended for debug)
 * @param checkingPackages List of packages of objects that the converter must check.
 * If it isn't set the converter will check all the objects
 * Recommended for optimisation.
 */
class NotNullFieldsValidator(private val throwFirst: Boolean, vararg checkingPackages: String) : IBodyValidator {
    private val checkingPackages = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    init {
        this.checkingPackages.addAll(Arrays.asList(*checkingPackages))
    }

    @Throws(ValidationException::class)
    override fun validate(body: Any?) {
        if (body == null) return

        val errors = LinkedList<String>()

        check(body, HashSet(), "|", errors)

        if (!errors.isEmpty()) {
            throw ValidationException(errors.joinToString("\n"), body)
        }
    }

    @Throws(ValidationException::class)
    private fun check(obj: Any?, checked: MutableSet<Any>, path: String, errors: MutableList<String>) {
        if (obj == null) return
        checked.add(obj)

        when (obj) {
            is Iterable<*> -> {
                obj.filter { it != null && !containsPointer(checked, it) }
                        .forEachIndexed { key, item -> check(item, checked, "$path[$key]", errors) }
            }
            is Array<*> -> {
                obj.filter { it != null && !containsPointer(checked, it) }
                        .forEachIndexed { key, item -> check(item, checked, "$path[$key]", errors) }
            }
            is Map<*, *> -> for ((key, value) in obj) {
                if (value != null && !containsPointer(checked, value))
                    check(value, checked, "$path[$key]", errors)
            }
            else -> {
                val kClass = obj::class

                try {
                    if (!kClass.isKotlinClass() || checkIsEnum(kClass) || !checkPackage(kClass.java.`package`?.name)) return
                    checkFields(kClass.declaredMemberProperties, obj, checked, path, errors)

                    kClass.superclasses
                            .filter { kClass.isKotlinClass() && !checkIsEnum(kClass) && checkPackage(kClass.java.`package`?.name) }
                            .forEach { checkFields(it.declaredMemberProperties, obj, checked, path, errors) }
                } catch (e: ValidationException) {
                    throw e
                } catch (e: Throwable) {
                    throw ValidationException(e.toString(), obj)
                }
            }
        }
    }

    @Throws(ValidationException::class)
    private fun checkFields(fields: Iterable<KProperty<*>>, obj: Any, checked: MutableSet<Any>, path: String, errors: MutableList<String>) {
        for (field in fields) {
            var value: Any? = null
            val javaField = field.javaField

            value = if (javaField == null) {
                field.getter.callWithAccessibility(obj)
            } else { // for example, field not exists if the getter is overridden
                javaField.doWithAccessibility { get(obj) }
            }

            if (!field.returnType.isMarkedNullable) {
                if (value == null) {
                    val message = "Field " + path + PATH_SEPARATOR + field.name + " in " + obj.javaClass.simpleName + " is required"
                    if (throwFirst) throw ValidationException(message, obj)
                    else errors.add(message)
                }
            }
            if (value != null && !containsPointer(checked, value!!)) {
                check(value, checked, path + PATH_SEPARATOR + field.name, errors)
            }
        }
    }

    private fun checkIsEnum(kClass: KClass<*>) = kClass.isSubclassOf(kotlin.Enum::class)

    private fun checkPackage(p: String?): Boolean {
        if (p == null) return false
        return !p.startsWith("java") && !p.startsWith("android") && !p.startsWith("kotlin") && (checkingPackages.isEmpty() || checkingPackages.contains(p))
    }

    private fun KClass<*>.isKotlinClass(): Boolean {
        return this.java.declaredAnnotations.any {
            it.annotationClass.qualifiedName == "kotlin.Metadata"
        }
    }

    companion object {
        private const val PATH_SEPARATOR = "."

        private fun containsPointer(objects: Set<Any>, target: Any) = objects.any { it === target }
    }
}