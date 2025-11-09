package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.XmlElementInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis缓存配置核心类，管理SQL ID与Java/XML元素的映射关系
 */
public class MyBatisCacheConfig {
    /**
     * 单例模式（按项目隔离缓存）
     */
    private static final Map<Project, MyBatisCacheConfig> INSTANCES = new ConcurrentHashMap<>();
    /**
     * 1. SQL ID -> 关联的Java元素（一对多）
     */
    private final Map<String, Set<JavaElementInfo>> sqlIdToJavaElements = new ConcurrentHashMap<>();
    /**
     * 2. SQL ID -> 关联的XML元素（一对多）
     */
    private final Map<String, Set<XmlElementInfo>> sqlIdToXmlElements = new ConcurrentHashMap<>();
    /**
     * 3. Java文件路径 -> 该文件涉及的所有SQL ID（一对多）
     */
    private final Map<String, Set<String>> javaFileToSqlIds = new ConcurrentHashMap<>();
    /**
     * 4. XML文件路径 -> 该文件包含的所有SQL ID（一对多）
     */
    private final Map<String, Set<String>> xmlFileToSqlIds = new ConcurrentHashMap<>();
    /**
     * 5. 文件摘要缓存（用于后续定时校验文件是否变更）
     */
    private final Map<String, String> fileDigestCache = new ConcurrentHashMap<>();

    /**
     * 私有构造器
     */
    private MyBatisCacheConfig() {
    }

    public static MyBatisCacheConfig getInstance(@NotNull Project project) {
        return INSTANCES.computeIfAbsent(project, k -> new MyBatisCacheConfig());
    }

    // ========================= SQL ID与Java元素映射操作 =========================

    /**
     * 添加SQL ID与Java元素的映射
     */
    public void addJavaElementMapping(@NotNull String sqlId, @NotNull JavaElementInfo javaInfo) {
        // 1. 更新SQL ID到Java元素的映射
        sqlIdToJavaElements.computeIfAbsent(sqlId, k -> ConcurrentHashMap.newKeySet()).add(javaInfo);
        // 2. 更新Java文件到SQL ID的映射
        javaFileToSqlIds.computeIfAbsent(javaInfo.getFilePath(), k -> ConcurrentHashMap.newKeySet()).add(sqlId);
    }

    /**
     * 根据SQL ID获取关联的Java元素
     */
    @NotNull
    public Set<JavaElementInfo> getJavaElementsBySqlId(@NotNull String sqlId) {
        return sqlIdToJavaElements.getOrDefault(sqlId, Collections.emptySet());
    }

    // ========================= SQL ID与XML元素映射操作 =========================

    /**
     * 添加SQL ID与XML元素的映射
     */
    public void addXmlElementMapping(@NotNull String sqlId, @NotNull XmlElementInfo xmlInfo) {
        // 1. 更新SQL ID到XML元素的映射
        sqlIdToXmlElements.computeIfAbsent(sqlId, k -> ConcurrentHashMap.newKeySet()).add(xmlInfo);
        // 2. 更新XML文件到SQL ID的映射
        xmlFileToSqlIds.computeIfAbsent(xmlInfo.getFilePath(), k -> ConcurrentHashMap.newKeySet()).add(sqlId);
    }

    /**
     * 根据SQL ID获取关联的XML元素
     */
    @NotNull
    public Set<XmlElementInfo> getXmlElementsBySqlId(@NotNull String sqlId) {
        return sqlIdToXmlElements.getOrDefault(sqlId, Collections.emptySet());
    }

    // ========================= 文件与SQL ID的映射操作 =========================

    /**
     * 获取Java文件涉及的所有SQL ID
     */
    @NotNull
    public Set<String> getSqlIdsByJavaFile(@NotNull String javaFilePath) {
        return javaFileToSqlIds.getOrDefault(javaFilePath, Collections.emptySet());
    }

    /**
     * 获取XML文件包含的所有SQL ID
     */
    @NotNull
    public Set<String> getSqlIdsByXmlFile(@NotNull String xmlFilePath) {
        return xmlFileToSqlIds.getOrDefault(xmlFilePath, Collections.emptySet());
    }

    // ========================= 文件摘要操作（为后续缓存驱逐做准备） =========================

    /**
     * 保存文件摘要
     */
    public void saveFileDigest(@NotNull VirtualFile file, @NotNull String digest) {
        fileDigestCache.put(file.getPath(), digest);
    }

    /**
     * 获取文件缓存的摘要
     */
    @Nullable
    public String getFileDigest(@NotNull VirtualFile file) {
        return fileDigestCache.get(file.getPath());
    }


    /**
     * 获取文件缓存的摘要
     */
    @Nullable
    public Map<String, String> getAllFileDigest() {
        return fileDigestCache;
    }

    // ========================= 缓存清理操作（基础方法，为后续阶段准备） =========================

    /**
     * 清除指定Java文件的所有缓存映射
     */
    public void clearJavaFileCache(@NotNull String javaFilePath) {
        // 1. 从javaFileToSqlIds中获取该文件关联的所有SQL ID
        Set<String> sqlIds = javaFileToSqlIds.remove(javaFilePath);
        if (sqlIds == null) return;

        // 2. 从sqlIdToJavaElements中移除这些SQL ID关联的该文件元素
        for (String sqlId : sqlIds) {
            Set<JavaElementInfo> javaElements = sqlIdToJavaElements.get(sqlId);
            if (javaElements != null) {
                javaElements.removeIf(info -> javaFilePath.equals(info.getFilePath()));
                // 若SQL ID对应的Java元素为空，移除该SQL ID
                if (javaElements.isEmpty()) {
                    sqlIdToJavaElements.remove(sqlId);
                }
            }
        }
    }

    /**
     * 清除指定XML文件的所有缓存映射
     */
    public void clearXmlFileCache(@NotNull String xmlFilePath) {
        // 1. 从xmlFileToSqlIds中获取该文件关联的所有SQL ID
        Set<String> sqlIds = xmlFileToSqlIds.remove(xmlFilePath);
        if (sqlIds == null) return;

        // 2. 从sqlIdToXmlElements中移除这些SQL ID关联的该文件元素
        for (String sqlId : sqlIds) {
            Set<XmlElementInfo> xmlElements = sqlIdToXmlElements.get(sqlId);
            if (xmlElements != null) {
                xmlElements.removeIf(info -> xmlFilePath.equals(info.getFilePath()));
                // 若SQL ID对应的XML元素为空，移除该SQL ID
                if (xmlElements.isEmpty()) {
                    sqlIdToXmlElements.remove(sqlId);
                }
            }
        }
        // 3. 清除文件摘要
        fileDigestCache.remove(xmlFilePath);
    }

    /**
     * 清除所有缓存（用于全局刷新）
     */
    public void clearAllCache() {
        sqlIdToJavaElements.clear();
        sqlIdToXmlElements.clear();
        javaFileToSqlIds.clear();
        xmlFileToSqlIds.clear();
        fileDigestCache.clear();
    }


    public Map<String, Set<JavaElementInfo>> getSqlIdToJavaElements() {
        return sqlIdToJavaElements;
    }

    public Map<String, Set<XmlElementInfo>> getSqlIdToXmlElements() {
        return sqlIdToXmlElements;
    }

    public Map<String, Set<String>> getJavaFileToSqlIds() {
        return javaFileToSqlIds;
    }

    public Map<String, Set<String>> getXmlFileToSqlIds() {
        return xmlFileToSqlIds;
    }

    public Map<String, String> getFileDigestCache() {
        return fileDigestCache;
    }
}