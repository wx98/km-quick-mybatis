package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.persistent;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.MyBatisCacheConfig;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.XmlElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.NotificationUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 缓存持久化组件，负责MyBatis缓存的序列化和反序列化
 */
@State(name = "kmQuickMybatisCachePersistentState", storages = @Storage("km-quick-mybatis-cache.xml"))
public class MyBatisCachePersistentState implements PersistentStateComponent<MyBatisCachePersistentState> {

    private Project project;
    // 缓存版本号，用于处理版本兼容
    public int cacheVersion = 1;

    // 持久化存储的缓存数据
    public Map<String, Set<JavaElementInfo>> sqlIdToJavaElements = new HashMap<>();
    public Map<String, Set<XmlElementInfo>> sqlIdToXmlElements = new HashMap<>();
    public Map<String, Set<String>> javaFileToSqlIds = new HashMap<>();
    public Map<String, Set<String>> xmlFileToSqlIds = new HashMap<>();
    public Map<String, String> fileDigestCache = new HashMap<>();
    public Map<String, Set<String>> sqlIdToFiles = new HashMap<>();

    public MyBatisCachePersistentState() {
    }

    public MyBatisCachePersistentState(Project project) {
        this.project = project;
    }

    public static MyBatisCachePersistentState getInstance(@NotNull Project project) {
        Objects.requireNonNull(project, "Project cannot be null");
        return project.getService(MyBatisCachePersistentState.class);
    }

    @Nullable
    @Override
    public MyBatisCachePersistentState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull MyBatisCachePersistentState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    /**
     * 从缓存配置同步到持久化状态
     */
    public void syncFromCacheConfig(MyBatisCacheConfig config) {
        this.sqlIdToJavaElements = new HashMap<>(config.getSqlIdToJavaElements());
        this.sqlIdToXmlElements = new HashMap<>(config.getSqlIdToXmlElements());
        this.javaFileToSqlIds = new HashMap<>(config.getJavaFileToSqlIds());
        this.xmlFileToSqlIds = new HashMap<>(config.getXmlFileToSqlIds());
        this.fileDigestCache = new HashMap<>(config.getFileDigestCache());
        this.sqlIdToFiles = new HashMap<>(config.getSqlIdToFiles());
    }

    /**
     * 从持久化状态同步到缓存配置
     */
    public void syncToCacheConfig(MyBatisCacheConfig config) {
        Objects.requireNonNull(config, "MyBatisCacheConfig cannot be null");
        Objects.requireNonNull(project, "Project has not been initialized");
        // 如果没有 km-quick-mybatis-cache.xml 文件，则通知
        if (!isCacheFileValid()) {
            showCacheMissingNotification();
            return;
        }
        config.clearAllCache();
        config.getSqlIdToJavaElements().putAll(this.sqlIdToJavaElements);
        config.getSqlIdToXmlElements().putAll(this.sqlIdToXmlElements);
        config.getJavaFileToSqlIds().putAll(this.javaFileToSqlIds);
        config.getXmlFileToSqlIds().putAll(this.xmlFileToSqlIds);
        config.getFileDigestCache().putAll(this.fileDigestCache);
        config.getSqlIdToFiles().putAll(this.sqlIdToFiles);
    }

    /**
     * 检查缓存文件是否存在且非空
     */
    private boolean isCacheFileValid() {
        // 缓存文件路径：项目/.idea/km-quick-mybatis-cache.xml
        String projectBasePath = project.getBasePath();
        if (projectBasePath == null) return false;

        File cacheFile = new File(projectBasePath + "/.idea/km-quick-mybatis-cache.xml");
        // 条件：文件存在 + 大小大于0（避免空文件）
        return cacheFile.exists() && cacheFile.length() > 0;
    }

    private void showCacheMissingNotification() {
        String notificationKey = this.getClass().getSimpleName();
        NotificationUtil.showCacheRefreshNotification(project, notificationKey, // 新增：通知唯一标识
                "MyBatis缓存提示", "检测到缓存文件缺失或无效，建议刷新缓存以确保功能正常", "刷新缓存", "不再建议");
    }

}