package adsbynimbus.solutions.dynamicprice.util

import com.google.api.ads.admanager.axis.utils.v202505.*
import com.google.api.ads.admanager.axis.v202505.*

fun findBy(id: Long): Statement = statement {
    where("id = :id")
    withBindVariableValue("id", id)
}.toStatement()

fun findBy(name: String): Statement = statement {
    where("name = :name")
    withBindVariableValue("name", "$name")
}.toStatement()

fun findAllBy(name: String): Statement = statement {
    where("name LIKE :name")
    withBindVariableValue("name", "%$name%")
}.toStatement()

inline fun statement(query: StatementBuilder.() -> Unit): StatementBuilder =
    StatementBuilder().apply {
        orderBy("id ASC")
        limit(pageSize)
        also(query)
    }

inline val pageSize get() = StatementBuilder.SUGGESTED_PAGE_LIMIT
