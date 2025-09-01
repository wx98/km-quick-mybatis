package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 测试MyBatis缓存配置类的功能
 */
public class MyBatisCacheConfigTest {

    /**
     * 测试默认配置值
     */
    @Test
    public void testDefaultConfigValues() {
        MyBatisCacheConfig config = new MyBatisCacheConfig();

        // 验证默认配置值
        assertEquals(1000, config.getMaxCacheSize());
        assertEquals(3600, config.getCacheExpiryTimeSeconds());
        assertEquals(60, config.getScanIntervalSeconds());
        assertTrue(config.isEnableMemoryOptimization());
        assertEquals(100, config.getCleanupBatchSize());
    }

    /**
     * 测试设置和获取最大缓存大小
     */
    @Test
    public void testSetAndGetMaxCacheSize() {
        MyBatisCacheConfig config = new MyBatisCacheConfig();

        // 测试设置有效的值
        config.setMaxCacheSize(500);
        assertEquals(500, config.getMaxCacheSize());

        // 测试设置更大的值
        config.setMaxCacheSize(2000);
        assertEquals(2000, config.getMaxCacheSize());

        // 测试设置无效的值（应该保持原值）
        config.setMaxCacheSize(-100);
        assertEquals(2000, config.getMaxCacheSize());

        config.setMaxCacheSize(0);
        assertEquals(2000, config.getMaxCacheSize());
    }

    /**
     * 测试设置和获取缓存过期时间
     */
    @Test
    public void testSetAndGetCacheExpiryTimeSeconds() {
        MyBatisCacheConfig config = new MyBatisCacheConfig();

        // 测试设置有效的值
        config.setCacheExpiryTimeSeconds(1800); // 30分钟
        assertEquals(1800, config.getCacheExpiryTimeSeconds());

        // 测试设置更大的值
        config.setCacheExpiryTimeSeconds(7200); // 2小时
        assertEquals(7200, config.getCacheExpiryTimeSeconds());

        // 测试设置更小的值
        config.setCacheExpiryTimeSeconds(60); // 1分钟
        assertEquals(60, config.getCacheExpiryTimeSeconds());

        // 测试设置无效的值（应该保持原值）
        config.setCacheExpiryTimeSeconds(-60);
        assertEquals(60, config.getCacheExpiryTimeSeconds());

        config.setCacheExpiryTimeSeconds(0);
        assertEquals(60, config.getCacheExpiryTimeSeconds());
    }

    /**
     * 测试设置和获取扫描间隔
     */
    @Test
    public void testSetAndGetScanIntervalSeconds() {
        MyBatisCacheConfig config = new MyBatisCacheConfig();

        // 测试设置有效的值
        config.setScanIntervalSeconds(30);
        assertEquals(30, config.getScanIntervalSeconds());

        // 测试设置更大的值
        config.setScanIntervalSeconds(120);
        assertEquals(120, config.getScanIntervalSeconds());

        // 测试设置无效的值（应该保持原值）
        config.setScanIntervalSeconds(-30);
        assertEquals(120, config.getScanIntervalSeconds());

        config.setScanIntervalSeconds(0);
        assertEquals(120, config.getScanIntervalSeconds());
    }

    /**
     * 测试启用和禁用内存优化
     */
    @Test
    public void testSetAndIsEnableMemoryOptimization() {
        MyBatisCacheConfig config = new MyBatisCacheConfig();

        // 默认应该是启用的
        assertTrue(config.isEnableMemoryOptimization());

        // 测试禁用内存优化
        config.setEnableMemoryOptimization(false);
        assertFalse(config.isEnableMemoryOptimization());

        // 测试重新启用内存优化
        config.setEnableMemoryOptimization(true);
        assertTrue(config.isEnableMemoryOptimization());
    }

    /**
     * 测试设置和获取清理批次大小
     */
    @Test
    public void testSetAndGetCleanupBatchSize() {
        MyBatisCacheConfig config = new MyBatisCacheConfig();

        // 测试设置有效的值
        config.setCleanupBatchSize(50);
        assertEquals(50, config.getCleanupBatchSize());

        // 测试设置更大的值
        config.setCleanupBatchSize(200);
        assertEquals(200, config.getCleanupBatchSize());

        // 测试设置无效的值（应该保持原值）
        config.setCleanupBatchSize(-50);
        assertEquals(200, config.getCleanupBatchSize());

        config.setCleanupBatchSize(0);
        assertEquals(200, config.getCleanupBatchSize());
    }

    /**
     * 测试链式调用设置配置
     */
    @Test
    public void testChainedMethodCalls() {
        MyBatisCacheConfig config = new MyBatisCacheConfig()
                .setMaxCacheSize(1500)
                .setCacheExpiryTimeSeconds(1200)
                .setScanIntervalSeconds(45)
                .setEnableMemoryOptimization(false)
                .setCleanupBatchSize(75);

        // 验证所有配置都被正确设置
        assertEquals(1500, config.getMaxCacheSize());
        assertEquals(1200, config.getCacheExpiryTimeSeconds());
        assertEquals(45, config.getScanIntervalSeconds());
        assertFalse(config.isEnableMemoryOptimization());
        assertEquals(75, config.getCleanupBatchSize());
    }

    /**
     * 测试创建默认配置
     */
    @Test
    public void testCreateDefault() {
        MyBatisCacheConfig defaultConfig = MyBatisCacheConfig.createDefault();
        MyBatisCacheConfig newConfig = new MyBatisCacheConfig();

        // 验证createDefault返回的配置与直接new的配置具有相同的默认值
        assertEquals(newConfig.getMaxCacheSize(), defaultConfig.getMaxCacheSize());
        assertEquals(newConfig.getCacheExpiryTimeSeconds(), defaultConfig.getCacheExpiryTimeSeconds());
        assertEquals(newConfig.getScanIntervalSeconds(), defaultConfig.getScanIntervalSeconds());
        assertEquals(newConfig.isEnableMemoryOptimization(), defaultConfig.isEnableMemoryOptimization());
        assertEquals(newConfig.getCleanupBatchSize(), defaultConfig.getCleanupBatchSize());

        // 验证它们不是同一个实例
        assertNotSame(newConfig, defaultConfig);
    }
}