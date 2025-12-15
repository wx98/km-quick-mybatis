package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * MyBatis 缓存管理器接口
 * 负责管理Java类和MyBatis XML文件之间的映射关系缓存
 */
public interface MyBatisCacheManager {

    /**
     * 获取指定项目的缓存管理器实例
     *
     * @param project 当前项目
     * @return 缓存管理器实例
     */
    static MyBatisCacheManager getInstance(@NotNull Project project) {
        return null;
    }

    /**
     * 获取Java类与XML文件的映射关系
     *
     * @param className Java类名
     * @return XML文件路径列表
     */
    Set<String> getXmlFilesForClass(@NotNull String className);

    /**
     * 获取XML文件与Java类的映射关系
     *
     * @param xmlFilePath XML文件路径
     * @return Java类名
     */
    String getClassForXmlFile(@NotNull String xmlFilePath);

    /**
     * 获取Java方法与XML语句ID的映射关系
     *
     * @param className  Java类名
     * @param methodName Java方法名
     * @return XML语句ID
     */
    String getStatementIdForMethod(@NotNull String className, @NotNull String methodName);

    /**
     * 获取XML语句ID与Java方法的映射关系
     *
     * @param xmlFilePath XML文件路径
     * @param statementId XML语句ID
     * @return Java方法名
     */
    String getMethodForStatementId(@NotNull String xmlFilePath, @NotNull String statementId);

    /**
     * 存储Java类与XML文件的映射关系
     *
     * @param className   Java类名
     * @param xmlFilePath XML文件路径
     */
    void putClassXmlMapping(@NotNull String className, @NotNull String xmlFilePath);

    /**
     * 存储Java方法与XML语句ID的映射关系
     *
     * @param className   Java类名
     * @param methodName  Java方法名
     * @param statementId XML语句ID
     */
    void putMethodStatementMapping(@NotNull String className, @NotNull String methodName, @NotNull String statementId);

    /**
     * 清除指定Java类的所有缓存
     *
     * @param className Java类名
     */
    void clearClassCache(@NotNull String className);

    /**
     * 清除指定XML文件的所有缓存
     *
     * @param xmlFilePath XML文件路径
     */
    void clearXmlFileCache(@NotNull String xmlFilePath);

    /**
     * 清除指定文件缓存
     *
     * @param filePath 文件路径
     */
    void clearFileCache(@NotNull String filePath);

    /**
     * 清除所有缓存
     */
    void clearAllCache();


    /**
     * 刷新所有失效的缓存
     */
    void refreshInvalidatedCaches(String filePath);

    /**
     * 获取当前缓存配置
     *
     * @return 缓存配置
     */
    @NotNull MyBatisCache getCacheConfig();

    /**
     * 设置缓存配置
     *
     * @param config 缓存配置
     */
    void setCacheConfig(@NotNull MyBatisCache config);


    void setScanInterval(long intervalMs);

    void performFullCacheRefresh(int numberOfRefreshes);

    long getCurrentCacheVersion();

    boolean isCacheUpToDate(long lastKnownVersion);

    boolean checkForCacheInvalidationAndNotify(Project project);
}