package rj.cocacode.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object Metrics {
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val gauges = ConcurrentHashMap<String, () -> Long>()
    private val histograms = ConcurrentHashMap<String, MutableList<Long>>()
    private val timers = ConcurrentHashMap<String, Long>()
    
    fun incrementCounter(name: String, value: Long = 1) {
        counters.getOrPut(name) { AtomicLong() }.addAndGet(value)
    }
    
    fun decrementCounter(name: String, value: Long = 1) {
        counters.getOrPut(name) { AtomicLong() }.addAndGet(-value)
    }
    
    fun setGauge(name: String, gauge: () -> Long) {
        gauges[name] = gauge
    }
    
    fun recordValue(name: String, value: Long) {
        histograms.getOrPut(name) { mutableListOf() }.add(value)
    }
    
    fun recordTime(name: String, durationMs: Long) {
        recordValue(name, durationMs)
        timers[name] = System.currentTimeMillis()
    }
    
    fun getCounter(name: String): Long = counters[name]?.get() ?: 0
    
    fun getGauge(name: String): Long = gauges[name]?.invoke() ?: 0
    
    fun getHistogramStats(name: String): HistogramStats? {
        val values = histograms[name] ?: return null
        if (values.isEmpty()) return null
        
        val sorted = values.sorted()
        return HistogramStats(
            count = values.size,
            sum = values.sum(),
            min = sorted.first(),
            max = sorted.last(),
            mean = values.sum().toDouble() / values.size,
            p50 = sorted[sorted.size / 2],
            p95 = sorted[(sorted.size * 0.95).toInt()],
            p99 = sorted[(sorted.size * 0.99).toInt()]
        )
    }
    
    fun getAllMetrics(): Map<String, MetricValue> {
        val result = mutableMapOf<String, MetricValue>()
        
        counters.forEach { (name, counter) ->
            result["counter.$name"] = MetricValue.Counter(counter.get())
        }
        
        gauges.forEach { (name, gauge) ->
            result["gauge.$name"] = MetricValue.Gauge(gauge())
        }
        
        histograms.forEach { (name, values) ->
            if (values.isNotEmpty()) {
                result["histogram.$name"] = MetricValue.Histogram(getHistogramStats(name)!!)
            }
        }
        
        return result
    }
    
    fun reset() {
        counters.clear()
        gauges.clear()
        histograms.clear()
        timers.clear()
    }
}

data class HistogramStats(
    val count: Int,
    val sum: Long,
    val min: Long,
    val max: Long,
    val mean: Double,
    val p50: Long,
    val p95: Long,
    val p99: Long
)

sealed class MetricValue {
    data class Counter(val value: Long) : MetricValue()
    data class Gauge(val value: Long) : MetricValue()
    data class Histogram(val stats: HistogramStats) : MetricValue()
}

class Timer(private val name: String) {
    private var startTime: Long = 0
    
    fun start() {
        startTime = System.currentTimeMillis()
    }
    
    fun stop(): Long {
        val elapsed = System.currentTimeMillis() - startTime
        Metrics.recordTime(name, elapsed)
        return elapsed
    }
}

object DefaultMetrics {
    fun init() {
        Metrics.setGauge("jvm.memory.used") {
            Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        }
        
        Metrics.setGauge("jvm.memory.free") {
            Runtime.getRuntime().freeMemory()
        }
        
        Metrics.setGauge("jvmthreads.active") {
            Thread.activeCount().toLong()
        }
    }
}