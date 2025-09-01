package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 测试缓存统计信息类的功能
 */
public class CacheStatisticsTest {

    /**
     * 测试记录缓存命中和获取命中次数
     */
    @Test
    public void testRecordHitAndGetHits() {
        CacheStatistics statistics = new CacheStatistics();

        // 初始值应该为0
        assertEquals(0, statistics.getHits());

        // 记录一次命中
        statistics.recordHit();
        assertEquals(1, statistics.getHits());

        // 记录多次命中
        statistics.recordHit();
        statistics.recordHit();
        assertEquals(3, statistics.getHits());

        // 验证请求总数也相应增加
        assertEquals(3, statistics.getRequests());
    }

    /**
     * 测试记录缓存未命中和获取未命中次数
     */
    @Test
    public void testRecordMissAndGetMisses() {
        CacheStatistics statistics = new CacheStatistics();

        // 初始值应该为0
        assertEquals(0, statistics.getMisses());

        // 记录一次未命中
        statistics.recordMiss();
        assertEquals(1, statistics.getMisses());

        // 记录多次未命中
        statistics.recordMiss();
        statistics.recordMiss();
        assertEquals(3, statistics.getMisses());

        // 验证请求总数也相应增加
        assertEquals(3, statistics.getRequests());
    }

    /**
     * 测试记录缓存驱逐和获取驱逐次数
     */
    @Test
    public void testRecordEvictionAndGetEvictions() {
        CacheStatistics statistics = new CacheStatistics();

        // 初始值应该为0
        assertEquals(0, statistics.getEvictions());

        // 记录一次驱逐
        statistics.recordEviction();
        assertEquals(1, statistics.getEvictions());

        // 记录多次驱逐
        statistics.recordEviction();
        statistics.recordEviction();
        statistics.recordEviction();
        assertEquals(4, statistics.getEvictions());
    }

    /**
     * 测试记录缓存失效和获取失效次数
     */
    @Test
    public void testRecordInvalidationAndGetInvalidations() {
        CacheStatistics statistics = new CacheStatistics();

        // 初始值应该为0
        assertEquals(0, statistics.getInvalidations());

        // 记录一次失效
        statistics.recordInvalidation();
        assertEquals(1, statistics.getInvalidations());

        // 记录多次失效
        statistics.recordInvalidation();
        statistics.recordInvalidation();
        assertEquals(3, statistics.getInvalidations());
    }

    /**
     * 测试更新缓存大小和获取当前大小、最大大小
     */
    @Test
    public void testUpdateCurrentSizeAndGetSizeInfo() {
        CacheStatistics statistics = new CacheStatistics();

        // 初始值应该为0
        assertEquals(0, statistics.getCurrentSize());
        assertEquals(0, statistics.getMaxSize());

        // 更新缓存大小
        statistics.updateCurrentSize(10);
        assertEquals(10, statistics.getCurrentSize());
        assertEquals(10, statistics.getMaxSize());

        // 更新为更大的大小
        statistics.updateCurrentSize(20);
        assertEquals(20, statistics.getCurrentSize());
        assertEquals(20, statistics.getMaxSize());

        // 更新为更小的大小
        statistics.updateCurrentSize(5);
        assertEquals(5, statistics.getCurrentSize());
        // 最大大小应该保持不变
        assertEquals(20, statistics.getMaxSize());
    }

    /**
     * 测试更新内存使用量和获取内存使用量
     */
    @Test
    public void testUpdateMemoryUsageAndGetMemoryUsage() {
        CacheStatistics statistics = new CacheStatistics();

        // 初始值应该为0
        assertEquals(0, statistics.getMemoryUsageBytes());

        // 更新内存使用量
        statistics.updateMemoryUsage(1024); // 1KB
        assertEquals(1024, statistics.getMemoryUsageBytes());

        // 更新为更大的内存使用量
        statistics.updateMemoryUsage(1048576); // 1MB
        assertEquals(1048576, statistics.getMemoryUsageBytes());

        // 更新为更小的内存使用量
        statistics.updateMemoryUsage(512);
        assertEquals(512, statistics.getMemoryUsageBytes());
    }

    /**
     * 测试计算缓存命中率
     */
    @Test
    public void testGetHitRate() {
        CacheStatistics statistics = new CacheStatistics();

        // 初始命中率应该为0
        assertEquals(0.0, statistics.getHitRate(), 0.001);

        // 记录一些命中和未命中
        statistics.recordHit();
        statistics.recordHit();
        statistics.recordMiss();

        // 命中率应该是 2/3 ≈ 0.6667
        assertEquals(2.0 / 3.0, statistics.getHitRate(), 0.001);

        // 再记录一次命中
        statistics.recordHit();

        // 命中率应该是 3/4 = 0.75
        assertEquals(0.75, statistics.getHitRate(), 0.001);

        // 只有命中的情况
        CacheStatistics hitOnlyStatistics = new CacheStatistics();
        hitOnlyStatistics.recordHit();
        hitOnlyStatistics.recordHit();
        assertEquals(1.0, hitOnlyStatistics.getHitRate(), 0.001);

        // 只有未命中的情况
        CacheStatistics missOnlyStatistics = new CacheStatistics();
        missOnlyStatistics.recordMiss();
        missOnlyStatistics.recordMiss();
        assertEquals(0.0, missOnlyStatistics.getHitRate(), 0.001);
    }

    /**
     * 测试重置统计数据
     */
    @Test
    public void testReset() {
        CacheStatistics statistics = new CacheStatistics();

        // 记录一些统计数据
        statistics.recordHit();
        statistics.recordHit();
        statistics.recordMiss();
        statistics.recordEviction();
        statistics.recordInvalidation();
        statistics.updateCurrentSize(10);
        statistics.updateCurrentSize(20); // 最大大小应该是20
        statistics.updateMemoryUsage(1024);

        // 验证重置前的数据
        assertEquals(2, statistics.getHits());
        assertEquals(1, statistics.getMisses());
        assertEquals(3, statistics.getRequests());
        assertEquals(1, statistics.getEvictions());
        assertEquals(1, statistics.getInvalidations());
        assertEquals(20, statistics.getCurrentSize());
        assertEquals(20, statistics.getMaxSize());
        assertEquals(1024, statistics.getMemoryUsageBytes());

        // 重置统计数据
        statistics.reset();

        // 验证重置后的数据
        assertEquals(0, statistics.getHits());
        assertEquals(0, statistics.getMisses());
        assertEquals(0, statistics.getRequests());
        assertEquals(0, statistics.getEvictions());
        assertEquals(0, statistics.getInvalidations());
        assertEquals(0, statistics.getCurrentSize());
        // 最大大小应该保持不变
        assertEquals(20, statistics.getMaxSize());
        assertEquals(0, statistics.getMemoryUsageBytes());
    }

    /**
     * 测试toString方法的格式
     */
    @Test
    public void testToString() {
        CacheStatistics statistics = new CacheStatistics();
        statistics.recordHit();
        statistics.recordMiss();
        statistics.updateCurrentSize(5);

        String toStringResult = statistics.toString();

        // 验证toString结果包含必要的信息
        assertTrue(toStringResult.contains("CacheStatistics"));
        assertTrue(toStringResult.contains("hits=1"));
        assertTrue(toStringResult.contains("misses=1"));
        assertTrue(toStringResult.contains("requests=2"));
        assertTrue(toStringResult.contains("hitRate=50.00%"));
        assertTrue(toStringResult.contains("currentSize=5"));
        assertTrue(toStringResult.contains("maxSize=5"));
    }
}