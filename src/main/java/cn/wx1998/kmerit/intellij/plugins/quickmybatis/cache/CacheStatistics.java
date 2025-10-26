package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

public interface CacheStatistics {
    long getHitCount();

    long getMissCount();

    long getInvalidationCount();

    double getHitRate();

    void recordHit();

    void recordMiss();

    void recordInvalidation();

    void reset();
}
