package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.persistent;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.MyBatisCacheConfig;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.XmlElementInfo;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 缓存持久化组件，负责MyBatis缓存的序列化和反序列化
 */
@State(
        name = "MyBatisCachePersistentState",
        storages = @Storage("quick-mybatis-cache.xml")
)
public class MyBatisCachePersistentState implements PersistentStateComponent<MyBatisCachePersistentState> {

    // 缓存版本号，用于处理版本兼容
    public int cacheVersion = 1;

    // 持久化存储的缓存数据
    public Map<String, Set<JavaElementInfo>> sqlIdToJavaElements = new HashMap<>();
    public Map<String, Set<XmlElementInfo>> sqlIdToXmlElements = new HashMap<>();
    public Map<String, Set<String>> javaFileToSqlIds = new HashMap<>();
    public Map<String, Set<String>> xmlFileToSqlIds = new HashMap<>();
    public Map<String, String> fileDigestCache = new HashMap<>();

    public MyBatisCachePersistentState() {
    }


    public static MyBatisCachePersistentState getInstance(Project project) {
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
    }

    /**
     * 从持久化状态同步到缓存配置
     */
    public void syncToCacheConfig(MyBatisCacheConfig config) {
        config.clearAllCache();
        config.getSqlIdToJavaElements().putAll(this.sqlIdToJavaElements);
        config.getSqlIdToXmlElements().putAll(this.sqlIdToXmlElements);
        config.getJavaFileToSqlIds().putAll(this.javaFileToSqlIds);
        config.getXmlFileToSqlIds().putAll(this.xmlFileToSqlIds);
        config.getFileDigestCache().putAll(this.fileDigestCache);
    }
}