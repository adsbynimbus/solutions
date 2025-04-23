package adsbynimbus.solutions.dynamicprice.util.data

import com.google.api.ads.admanager.axis.utils.v202505.*
import com.google.api.ads.admanager.axis.v202505.*

fun findBy(id: Long): Statement = statement {
    where("id = :id")
    withBindVariableValue("id", id)
}

fun findBy(name: String): Statement = statement {
    where("name LIKE :name")
    withBindVariableValue("name", "%$name%")
}

inline fun statement(query: StatementBuilder.() -> Unit): Statement = StatementBuilder().run {
    orderBy("id ASC")
    limit(StatementBuilder.SUGGESTED_PAGE_LIMIT)
    apply(query)
}.toStatement()
