package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存统计信息类
 * 用于收集和提供缓存的统计数据，如缓存命中率、缓存项数量等
 */
public class CacheStatistics {
    // 统计计数器
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong requests = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    private final AtomicLong invalidations = new AtomicLong(0);
    private final AtomicLong currentSize = new AtomicLong(0);
    private final AtomicLong maxSize = new AtomicLong(0);
    private final AtomicLong memoryUsageBytes = new AtomicLong(0);

    /**
     * 记录缓存命中
     */
    public void recordHit() {
        hits.incrementAndGet();
        requests.incrementAndGet();
    }

    /**
     * 记录缓存未命中
     */
    public void recordMiss() {
        misses.incrementAndGet();
        requests.incrementAndGet();
    }

    /**
     * 记录缓存驱逐
     */
    public void recordEviction() {
        evictions.incrementAndGet();
    }

    /**
     * 记录缓存失效
     */
    public void recordInvalidation() {
        invalidations.incrementAndGet();
    }

    /**
     * 更新当前缓存大小
     *
     * @param size 当前缓存项数量
     */
    public void updateCurrentSize(long size) {
        currentSize.set(size);
        if (size > maxSize.get()) {
            maxSize.set(size);
        }
    }

    /**
     * 更新内存使用量
     *
     * @param bytes 内存使用量（字节）
     */
    public void updateMemoryUsage(long bytes) {
        memoryUsageBytes.set(bytes);
    }

    /**
     * 获取缓存命中次数
     *
     * @return 命中次数
     */
    public long getHits() {
        return hits.get();
    }

    /**
     * 获取缓存未命中次数
     *
     * @return 未命中次数
     */
    public long getMisses() {
        return misses.get();
    }

    /**
     * 获取缓存请求总数
     *
     * @return 请求总数
     */
    public long getRequests() {
        return requests.get();
    }

    /**
     * 获取缓存驱逐次数
     *
     * @return 驱逐次数
     */
    public long getEvictions() {
        return evictions.get();
    }

    /**
     * 获取缓存失效次数
     *
     * @return 失效次数
     */
    public long getInvalidations() {
        return invalidations.get();
    }

    /**
     * 获取当前缓存大小
     *
     * @return 当前缓存项数量
     */
    public long getCurrentSize() {
        return currentSize.get();
    }

    /**
     * 获取最大缓存大小
     *
     * @return 历史最大缓存项数量
     */
    public long getMaxSize() {
        return maxSize.get();
    }

    /**
     * 获取内存使用量
     *
     * @return 内存使用量（字节）
     */
    public long getMemoryUsageBytes() {
        return memoryUsageBytes.get();
    }

    /**
     * 获取缓存命中率
     *
     * @return 命中率（0-1之间的浮点数）
     */
    public double getHitRate() {
        long totalRequests = requests.get();
        return totalRequests > 0 ? (double) hits.get() / totalRequests : 0.0;
    }

    /**
     * 重置所有统计数据
     */
    public void reset() {
        hits.set(0);
        misses.set(0);
        requests.set(0);
        evictions.set(0);
        invalidations.set(0);
        currentSize.set(0);
        // maxSize 保留历史最大值，不重置
        memoryUsageBytes.set(0);
    }

    @Override
    public String toString() {
        return "CacheStatistics{" +
                "hits=" + hits.get() +
                ", misses=" + misses.get() +
                ", requests=" + requests.get() +
                ", hitRate=" + String.format("%.2f%%", getHitRate() * 100) +
                ", evictions=" + evictions.get() +
                ", invalidations=" + invalidations.get() +
                ", currentSize=" + currentSize.get() +
                ", maxSize=" + maxSize.get() +
                ", memoryUsage=" + formatMemoryUsage(memoryUsageBytes.get()) +
                '}';
    }

    /**
     * 格式化内存使用量显示
     *
     * @param bytes 字节数
     * @return 格式化后的内存使用量字符串
     */
    private String formatMemoryUsage(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}