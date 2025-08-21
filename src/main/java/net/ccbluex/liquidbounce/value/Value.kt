/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.value

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import net.ccbluex.liquidbounce.file.FileManager.saveConfig
import net.ccbluex.liquidbounce.file.FileManager.valuesConfig
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.ui.font.GameFontRenderer
import net.ccbluex.liquidbounce.utils.ClientUtils.logger
import net.ccbluex.liquidbounce.utils.misc.RandomUtils.nextFloat
import net.ccbluex.liquidbounce.utils.misc.RandomUtils.nextInt
import net.minecraft.client.gui.FontRenderer
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class Value<T>(
    val name: String,
    protected open var value: T,
    val subjective: Boolean = false,
    var supportCondition: (() -> Boolean)? = null,
) : ReadWriteProperty<Any?, T> {

    var excluded: Boolean = false
        private set

    var hidden = false
        private set

    open fun set(newValue: T): Boolean {
        if (newValue == value || hidden || excluded)
            return false

        val oldValue = value

        try {
            val handledValue = onChange(oldValue, newValue)
            if (handledValue == oldValue) return false

            changeValue(handledValue)
            onChanged(oldValue, handledValue)
            onUpdate(handledValue)

            saveConfig(valuesConfig)
            return true
        } catch (e: Exception) {
            logger.error("[ValueSystem ($name)]: ${e.javaClass.name} (${e.message}) [$oldValue >> $newValue]")
            return false
        }
    }

    /**
     * Use only when you want an option to be hidden while keeping its state.
     *
     * [state] the value it will be set to before it is hidden.
     */
    fun hideWithState(state: T = value) {
        set(state)

        hidden = true
    }

    /**
     * Excludes the chosen option [value] from the config system.
     *
     * [state] the value it will be set to before it is excluded.
     */
    fun excludeWithState(state: T = value) {
        set(state)

        excluded = true
    }

    fun get() = value

    open fun changeValue(newValue: T) {
        value = newValue
    }

    open fun toJson() = toJsonF()

    open fun fromJson(element: JsonElement) {
        val result = fromJsonF(element)
        if (result != null) changeValue(result)

        onInit(value)
        onUpdate(value)
    }

    abstract fun toJsonF(): JsonElement?
    abstract fun fromJsonF(element: JsonElement): T?

    protected open fun onInit(value: T) {}
    protected open fun onUpdate(value: T) {}
    protected open fun onChange(oldValue: T, newValue: T) = newValue
    protected open fun onChanged(oldValue: T, newValue: T) {}

    open fun isSupported() = supportCondition?.invoke() != false

    open fun setSupport(value: (Boolean) -> () -> Boolean) {
        supportCondition = value(isSupported())
    }

    // Support for delegating values using the `by` keyword.
    override operator fun getValue(thisRef: Any?, property: KProperty<*>) = value
    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        set(value)
    }

    fun shouldRender() = isSupported() && !hidden
}

/**
 * Bool value represents a value with a boolean
 */
open class BoolValue(
    name: String,
    value: Boolean,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null,
) : Value<Boolean>(name, value, subjective, isSupported) {

    override fun toJsonF() = JsonPrimitive(value)

    override fun fromJsonF(element: JsonElement) =
        if (element.isJsonPrimitive) element.asBoolean || element.asString.equals("true", ignoreCase = true)
        else null

    fun toggle() = set(!value)

    fun isActive() = value && (isSupported() || hidden)

    override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
        return super.getValue(thisRef, property) && isActive()
    }

}

/**
 * Integer value represents a value with a integer
 */
open class IntegerValue(
    name: String,
    value: Int,
    val range: IntRange = 0..Int.MAX_VALUE,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null,
) : Value<Int>(name, value, subjective, isSupported) {

    fun set(newValue: Number) = set(newValue.toInt())

    override fun toJsonF() = JsonPrimitive(value)

    override fun fromJsonF(element: JsonElement) = if (element.isJsonPrimitive) element.asInt else null

    fun isMinimal() = value <= minimum
    fun isMaximal() = value >= maximum

    val minimum = range.first
    val maximum = range.last
}

// TODO: Replace Min/Max options with this instead
open class IntegerRangeValue(
    name: String,
    value: IntRange,
    val range: IntRange = 0..Int.MAX_VALUE,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null,
) : Value<IntRange>(name, value, subjective, isSupported) {

    fun setFirst(newValue: Int) = set(newValue..value.last)
    fun setLast(newValue: Int) = set(value.first..newValue)

    override fun toJsonF(): JsonElement {
        return JsonPrimitive("${value.first}-${value.last}")
    }

    override fun fromJsonF(element: JsonElement): IntRange? {
        return element.asJsonPrimitive?.asString?.split("-")?.takeIf { it.size == 2 }?.let {
            val (start, end) = it

            start.toIntOrNull()?.let { s ->
                end.toIntOrNull()?.let { e ->
                    s..e
                }
            }
        }
    }

    fun isMinimal() = value.first <= minimum
    fun isMaximal() = value.last >= maximum

    val minimum = range.first
    val maximum = range.last

    val random
        get() = nextInt(value.first, value.last)
}

// TODO: Replace Min/Max options with this instead
open class FloatRangeValue(
    name: String,
    value: ClosedFloatingPointRange<Float>,
    val range: ClosedFloatingPointRange<Float> = 0f..Float.MAX_VALUE,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null,
) : Value<ClosedFloatingPointRange<Float>>(name, value, subjective, isSupported) {

    fun setFirst(newValue: Float) = set(newValue..value.endInclusive)
    fun setLast(newValue: Float) = set(value.start..newValue)

    override fun toJsonF(): JsonElement {
        return JsonPrimitive("${value.start}-${value.endInclusive}")
    }

    override fun fromJsonF(element: JsonElement): ClosedFloatingPointRange<Float>? {
        return element.asJsonPrimitive?.asString?.split("-")?.takeIf { it.size == 2 }?.let {
            val (start, end) = it

            start.toFloatOrNull()?.let { s ->
                end.toFloatOrNull()?.let { e ->
                    s..e
                }
            }
        }
    }

    fun isMinimal() = value.start <= minimum
    fun isMaximal() = value.endInclusive >= maximum

    val minimum = range.start
    val maximum = range.endInclusive

    val random
        get() = nextFloat(value.start, value.endInclusive)
}

/**
 * Float value represents a value with a float
 */
open class FloatValue(
    name: String,
    value: Float,
    val range: ClosedFloatingPointRange<Float> = 0f..Float.MAX_VALUE,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null,
) : Value<Float>(name, value, subjective, isSupported) {

    fun set(newValue: Number) = set(newValue.toFloat())

    override fun toJsonF() = JsonPrimitive(value)

    override fun fromJsonF(element: JsonElement) = if (element.isJsonPrimitive) element.asFloat else null

    fun isMinimal() = value <= minimum
    fun isMaximal() = value >= maximum

    val minimum = range.start
    val maximum = range.endInclusive
}

/**
 * Text value represents a value with a string
 */
open class TextValue(
    name: String,
    value: String,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null,
) : Value<String>(name, value, subjective, isSupported) {

    override fun toJsonF() = JsonPrimitive(value)

    override fun fromJsonF(element: JsonElement) = if (element.isJsonPrimitive) element.asString else null
}

/**
 * Font value represents a value with a font
 */
open class FontValue(
    name: String,
    value: FontRenderer,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null,
) : Value<FontRenderer>(name, value, subjective, isSupported) {

    override fun toJsonF(): JsonElement? {
        val fontDetails = Fonts.getFontDetails(value) ?: return null
        val valueObject = JsonObject()
        valueObject.run {
            addProperty("fontName", fontDetails.name)
            addProperty("fontSize", fontDetails.fontSize)
        }
        return valueObject
    }

    override fun fromJsonF(element: JsonElement) =
        if (element.isJsonObject) {
            val valueObject = element.asJsonObject
            Fonts.getFontRenderer(valueObject["fontName"].asString, valueObject["fontSize"].asInt)
        } else null

    val displayName
        get() = when (value) {
            is GameFontRenderer -> "Font: ${(value as GameFontRenderer).defaultFont.font.name} - ${(value as GameFontRenderer).defaultFont.font.size}"
            Fonts.minecraftFont -> "Font: Minecraft"
            else -> {
                val fontInfo = Fonts.getFontDetails(value)
                fontInfo?.let {
                    "${it.name}${if (it.fontSize != -1) " - ${it.fontSize}" else ""}"
                } ?: "Font: Unknown"
            }
        }

    fun next() {
        val fonts = Fonts.fonts
        value = fonts[(fonts.indexOf(value) + 1) % fonts.size]
    }

    fun previous() {
        val fonts = Fonts.fonts
        value = fonts[(fonts.indexOf(value) - 1) % fonts.size]
    }
}

/**
 * Block value represents a value with a block
 */
open class BlockValue(
    name: String, value: Int, subjective: Boolean = false, isSupported: (() -> Boolean)? = null,
) : IntegerValue(name, value, 1..197, subjective, isSupported)

/**
 * List value represents a selectable list of values
 */
open class ListValue(
    name: String,
    var values: Array<String>,
    override var value: String,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null,
) : Value<String>(name, value, subjective, isSupported) {

    var openList = false

    operator fun contains(string: String?) = values.any { it.equals(string, true) }

    override fun changeValue(newValue: String) {
        values.find { it.equals(newValue, true) }?.let { value = it }
    }
    fun cycle() {
        val index = values.indexOf(value)
        value = values[(index + 1) % values.size]
    }
    override fun toJsonF() = JsonPrimitive(value)

    override fun fromJsonF(element: JsonElement) = if (element.isJsonPrimitive) element.asString else null

    fun updateValues(newValues: Array<String>) {
        values = newValues
    }
}

fun intValue(
    name: String,
    value: Int,
    range: IntRange = 0..Int.MAX_VALUE,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null
) = IntegerValue(name, value, range, subjective, isSupported)

fun floatValue(
    name: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = 0f..Float.MAX_VALUE,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null
) = FloatValue(name, value, range, subjective, isSupported)

fun choices(
    name: String,
    values: Array<String>,
    value: String,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null
) = ListValue(name, values, value, subjective, isSupported)

fun block(
    name: String,
    value: Int,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null
) = BlockValue(name, value, subjective, isSupported)

fun font(
    name: String,
    value: FontRenderer,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null
) = FontValue(name, value, subjective, isSupported)

fun text(
    name: String,
    value: String,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null
) = TextValue(name, value, subjective, isSupported)

fun _boolean(
    name: String,
    value: Boolean,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null
) = BoolValue(name, value, subjective, isSupported)

fun intRange(
    name: String,
    value: IntRange,
    range: IntRange = 0..Int.MAX_VALUE,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null
) = IntegerRangeValue(name, value, range, subjective, isSupported)

fun floatRange(
    name: String,
    value: ClosedFloatingPointRange<Float>,
    range: ClosedFloatingPointRange<Float> = 0f..Float.MAX_VALUE,
    subjective: Boolean = false,
    isSupported: (() -> Boolean)? = null
) = FloatRangeValue(name, value, range, subjective, isSupported)