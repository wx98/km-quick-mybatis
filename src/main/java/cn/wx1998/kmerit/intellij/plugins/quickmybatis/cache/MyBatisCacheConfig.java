package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

/**
 * MyBatis 缓存配置类
 * 用于配置缓存的行为，如缓存大小限制、过期时间等
 */
public class MyBatisCacheConfig {
    // 默认配置值
    private static final int DEFAULT_MAX_CACHE_SIZE = 1000;
    private static final int DEFAULT_CACHE_EXPIRY_TIME_SECONDS = 3600;
    private static final int DEFAULT_SCAN_INTERVAL_SECONDS = 60;
    private static final boolean DEFAULT_ENABLE_MEMORY_OPTIMIZATION = true;
    private static final int DEFAULT_CLEANUP_BATCH_SIZE = 100;

    // 配置属性
    private int maxCacheSize = DEFAULT_MAX_CACHE_SIZE;
    private int cacheExpiryTimeSeconds = DEFAULT_CACHE_EXPIRY_TIME_SECONDS;
    private int scanIntervalSeconds = DEFAULT_SCAN_INTERVAL_SECONDS;
    private boolean enableMemoryOptimization = DEFAULT_ENABLE_MEMORY_OPTIMIZATION;
    private int cleanupBatchSize = DEFAULT_CLEANUP_BATCH_SIZE;

    /**
     * 创建默认配置
     *
     * @return 默认配置实例
     */
    public static MyBatisCacheConfig createDefault() {
        return new MyBatisCacheConfig();
    }

    /**
     * 创建适用于大型项目的配置
     *
     * @return 适用于大型项目的配置实例
     */
    public static MyBatisCacheConfig createForLargeProjects() {
        return new MyBatisCacheConfig().setMaxCacheSize(5000).setCacheExpiryTimeSeconds(1800).setScanIntervalSeconds(60).setEnableMemoryOptimization(true).setCleanupBatchSize(500);
    }

    /**
     * 创建适用于小型项目的配置
     *
     * @return 适用于小型项目的配置实例
     */
    public static MyBatisCacheConfig createForSmallProjects() {
        return new MyBatisCacheConfig().setMaxCacheSize(500).setCacheExpiryTimeSeconds(3600).setScanIntervalSeconds(120).setEnableMemoryOptimization(false).setCleanupBatchSize(50);
    }

    /**
     * 获取最大缓存大小
     *
     * @return 最大缓存项数
     */
    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    /**
     * 设置最大缓存大小
     *
     * @param maxCacheSize 最大缓存项数
     * @return 当前配置实例，用于链式调用
     */
    public MyBatisCacheConfig setMaxCacheSize(int maxCacheSize) {
        if (maxCacheSize > 0) {
            this.maxCacheSize = maxCacheSize;
        }
        return this;
    }

    /**
     * 获取缓存过期时间（秒）
     *
     * @return 缓存过期时间
     */
    public int getCacheExpiryTimeSeconds() {
        return cacheExpiryTimeSeconds;
    }

    /**
     * 设置缓存过期时间（秒）
     *
     * @param cacheExpiryTimeSeconds 缓存过期时间
     * @return 当前配置实例，用于链式调用
     */
    public MyBatisCacheConfig setCacheExpiryTimeSeconds(int cacheExpiryTimeSeconds) {
        if (cacheExpiryTimeSeconds > 0) {
            this.cacheExpiryTimeSeconds = cacheExpiryTimeSeconds;
        }
        return this;
    }

    /**
     * 获取文件扫描间隔（秒）
     *
     * @return 文件扫描间隔
     */
    public int getScanIntervalSeconds() {
        return scanIntervalSeconds;
    }

    /**
     * 设置文件扫描间隔（秒）
     *
     * @param scanIntervalSeconds 文件扫描间隔
     * @return 当前配置实例，用于链式调用
     */
    public MyBatisCacheConfig setScanIntervalSeconds(int scanIntervalSeconds) {
        if (scanIntervalSeconds > 0) {
            this.scanIntervalSeconds = scanIntervalSeconds;
        }
        return this;
    }

    /**
     * 是否启用内存优化
     *
     * @return 是否启用内存优化
     */
    public boolean isEnableMemoryOptimization() {
        return enableMemoryOptimization;
    }

    /**
     * 设置是否启用内存优化
     *
     * @param enableMemoryOptimization 是否启用内存优化
     * @return 当前配置实例，用于链式调用
     */
    public MyBatisCacheConfig setEnableMemoryOptimization(boolean enableMemoryOptimization) {
        this.enableMemoryOptimization = enableMemoryOptimization;
        return this;
    }

    /**
     * 获取清理批次大小
     *
     * @return 清理批次大小
     */
    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    /**
     * 设置清理批次大小
     *
     * @param cleanupBatchSize 清理批次大小
     * @return 当前配置实例，用于链式调用
     */
    public MyBatisCacheConfig setCleanupBatchSize(int cleanupBatchSize) {
        if (cleanupBatchSize > 0) {
            this.cleanupBatchSize = cleanupBatchSize;
        }
        return this;
    }

    @Override
    public String toString() {
        return "MyBatisCacheConfig{" + "maxCacheSize=" + maxCacheSize + ", cacheExpiryTimeSeconds=" + cacheExpiryTimeSeconds + ", scanIntervalSeconds=" + scanIntervalSeconds + ", enableMemoryOptimization=" + enableMemoryOptimization + ", cleanupBatchSize=" + cleanupBatchSize + '}';
    }
}