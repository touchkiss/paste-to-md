package com.touchkiss.pastetomd.converter

import java.util.Comparator
import java.util.stream.IntStream

internal object ParallelRenderSupport {

    private const val PARALLEL_BLOCK_THRESHOLD = 8

    fun <T, R> mapOrdered(items: List<T>, transform: (T) -> R): List<R> {
        if (items.size < PARALLEL_BLOCK_THRESHOLD || Runtime.getRuntime().availableProcessors() < 2) {
            return items.map(transform)
        }

        return IntStream.range(0, items.size)
            .parallel()
            .mapToObj { index -> IndexedValue(index, transform(items[index])) }
            .sorted(Comparator.comparingInt(IndexedValue<R>::index))
            .map(IndexedValue<R>::value)
            .toList()
    }
}
