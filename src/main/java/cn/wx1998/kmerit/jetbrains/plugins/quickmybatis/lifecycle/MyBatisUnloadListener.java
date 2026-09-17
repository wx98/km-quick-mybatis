package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.lifecycle;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.MyBatisCacheManagerDefault;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.DataBaseManager;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 插件卸载监听器，负责清理内存和磁盘资源
 */
public class MyBatisUnloadListener implements DynamicPluginListener {
    public static final String PLUGINS_ID = "cn.wx1998.kmerit.jetbrains.plugins.quickmybatis";
    private static final String IDEA_DIR_NAME = ".idea";
    private static final String CACHE_DB_FILE_PREFIX = "km_mybatis_cache";
    private static final String PROJECT_SETTING_FILE_NAME = "quick-mybatis-settings.xml";
    private static final String NOTIFICATION_SETTING_FILE_NAME = "km-quick-mybatis-notification.xml";
    private static final String APPLICATION_SETTING_FILE_NAME = "KmQuickMybatis.xml";
    // 日志前缀
    private static final String LOG_PREFIX = "[kmQuickMybatis 插件卸载监听器]";
    // IntelliJ 推荐的日志工具
    private static final Logger LOG = Logger.getInstance(MyBatisUnloadListener.class);

    /**
     * 插件卸载前触发：清理内存资源（线程、监听器、连接等）
     */
    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        if (!PLUGINS_ID.equals(pluginDescriptor.getPluginId().getIdString())) {
            return;
        }

        LOG.info(LOG_PREFIX + "开始清理插件运行时资源...");
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : openProjects) {
            disposeCacheManager(project);
            closeDatabaseConnections(project);
        }
        LOG.info(LOG_PREFIX + "插件运行时资源清理完成");

        // 插件更新时保留缓存和配置；真正卸载时再删除磁盘数据
        if (!isUpdate) {
            LOG.info(LOG_PREFIX + "开始清理插件磁盘资源...");
            for (Project project : openProjects) {
                deleteProjectFiles(project);
            }
            deleteApplicationConfigFile();
            LOG.info(LOG_PREFIX + "插件磁盘资源清理完成");
        }
    }

    private void disposeCacheManager(Project project) {
        if (project.isDisposed()) return;
        MyBatisCacheManagerDefault cacheManager = MyBatisCacheManagerDefault.getExistingInstance(project);
        if (cacheManager != null) {
            cacheManager.dispose();
        }
    }

    /**
     * 关闭数据库连接。
     */
    private void closeDatabaseConnections(Project project) {
        if (project.isDisposed()) return;
        DataBaseManager instance = project.getServiceIfCreated(DataBaseManager.class);
        if (instance != null && instance.isDataSourceActive()) {
            instance.close();
        }
        LOG.debug(LOG_PREFIX + "数据库连接已关闭，项目: " + project.getName());
    }

    /**
     * 删除项目级缓存和配置。
     */
    private void deleteProjectFiles(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            LOG.warn(LOG_PREFIX + "项目路径为空，跳过项目级磁盘清理，项目: " + project.getName());
            return;
        }

        Path ideaDir = Paths.get(basePath, IDEA_DIR_NAME);
        deleteFileIfExists(ideaDir.resolve(PROJECT_SETTING_FILE_NAME));
        deleteFileIfExists(ideaDir.resolve(NOTIFICATION_SETTING_FILE_NAME));
        deleteH2CacheFiles(ideaDir);
    }

    private void deleteH2CacheFiles(Path ideaDir) {
        deleteFileIfExists(ideaDir.resolve(CACHE_DB_FILE_PREFIX + ".mv.db"));
        deleteFileIfExists(ideaDir.resolve(CACHE_DB_FILE_PREFIX + ".trace.db"));
        deleteFileIfExists(ideaDir.resolve(CACHE_DB_FILE_PREFIX + ".lock.db"));
    }

    private void deleteApplicationConfigFile() {
        deleteFileIfExists(Paths.get(PathManager.getConfigPath(), "options", APPLICATION_SETTING_FILE_NAME));
    }

    private void deleteFileIfExists(Path path) {
        try {
            if (FileUtil.delete(path.toFile())) {
                LOG.info(LOG_PREFIX + "已删除文件: " + path);
            } else {
                LOG.debug(LOG_PREFIX + "文件不存在或无需删除: " + path);
            }
        } catch (Exception e) {
            LOG.warn(LOG_PREFIX + "删除文件失败: " + path, e);
        }
    }
}
