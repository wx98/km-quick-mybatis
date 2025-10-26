package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.persistent;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.MyBatisCacheConfig;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 缓存持久化管理器，监听项目生命周期事件
 */
public class MyBatisCachePersistenceManager {

    /**
     * 保存缓存到持久化存储
     */
    public void saveCache(@NotNull Project project) {
        MyBatisCacheConfig cacheConfig = MyBatisCacheConfig.getInstance(project);
        MyBatisCachePersistentState persistentState = MyBatisCachePersistentState.getInstance(project);
        persistentState.syncFromCacheConfig(cacheConfig);
    }

    /**
     * 从持久化存储加载缓存
     */
    public void loadCache(@NotNull Project project) {
        MyBatisCacheConfig cacheConfig = MyBatisCacheConfig.getInstance(project);
        MyBatisCachePersistentState persistentState = MyBatisCachePersistentState.getInstance(project);
        persistentState.syncToCacheConfig(cacheConfig);
    }

    /**
     * 手动触发缓存保存
     */
    public static void manualSaveCache(@NotNull Project project) {
        new MyBatisCachePersistenceManager().saveCache(project);
    }

    /**
     * 手动触发缓存加载
     */
    public static void manualLoadCache(@NotNull Project project) {
        new MyBatisCachePersistenceManager().loadCache(project);
    }

}