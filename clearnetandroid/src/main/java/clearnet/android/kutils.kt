package clearnet.android

import android.content.ContentValues
import java.lang.reflect.AccessibleObject
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.isAccessible

inline fun <T : Any, R> T.synchronized(block: T.() -> R): R = kotlin.synchronized(this) { block() }

fun <T> KProperty.Getter<T>.callWithAccessibility(vararg args: Any?): T {
    val isAccessible = property.isAccessible
    if (!isAccessible) property.isAccessible = true
    val isGetterAccessible = this.isAccessible
    if (!isGetterAccessible) this.isAccessible = true
    val value = this.call(*args)
    this.isAccessible = isGetterAccessible
    property.isAccessible = isAccessible
    return value
}

inline fun <T : AccessibleObject, R> T.doWithAccessibility(action: T.() -> R): R {
    val old = isAccessible
    isAccessible = true
    val result = action()
    isAccessible = old
    return result
}

operator fun ContentValues.set(key: String, value: String?) = this.put(key, value)
operator fun ContentValues.set(key: String, value: Long?) = this.put(key, value)
operator fun ContentValues.set(key: String, value: Int) = this.put(key, value)