package adsbynimbus.solutions.dynamicprice.util.service

import com.google.api.ads.admanager.axis.v202505.*

inline fun newKeyValue(configuration: CustomTargetingKey.() -> Unit) =
    CustomTargetingKey().apply(configuration)

inline val Dynamic: CustomTargetingKeyType get() = CustomTargetingKeyType.FREEFORM
inline val Predefined: CustomTargetingKeyType get() = CustomTargetingKeyType.PREDEFINED

inline var CustomTargetingKey.valueType: CustomTargetingKeyType
    get() = type
    set(value) { type = value }

inline val DoNotIncludeValues: ReportableType get() = ReportableType.OFF
inline val IncludeValues: ReportableType get() = ReportableType.ON
inline val IncludeValuesAsCustomDimension: ReportableType get() = ReportableType.CUSTOM_DIMENSION

inline var CustomTargetingKey.reportOnValues: ReportableType
    get() = reportableType
    set(value) { reportableType = value }

fun CustomTargetingKey.createValue(
    name: String,
    displayName: String = name,
    matchType: CustomTargetingValueMatchType = CustomTargetingValueMatchType.UNKNOWN,
) = CustomTargetingValue().also {
    it.customTargetingKeyId = id
    it.name = name
    it.displayName = displayName
    it.matchType = matchType
}

fun CustomTargetingServiceInterface.createKeys(vararg keys: CustomTargetingKey):
    Array<CustomTargetingKey> = createCustomTargetingKeys(keys)

fun CustomTargetingServiceInterface.createValues(vararg values: CustomTargetingValue):
    Array<CustomTargetingValue> = createCustomTargetingValues(values)
