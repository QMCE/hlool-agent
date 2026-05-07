package rj.cocacode.utils

fun <T> intersperse(list: List<T>, separator: (Int) -> T): List<T> =
    list.flatMapIndexed { index, item ->
        if (index > 0) listOf(separator(index), item) else listOf(item)
    }

fun <T> count(items: List<T>, predicate: (T) -> Boolean): Int =
    items.count { predicate(it) }

fun <T> uniq(items: Iterable<T>): List<T> = items.toSet().toList()