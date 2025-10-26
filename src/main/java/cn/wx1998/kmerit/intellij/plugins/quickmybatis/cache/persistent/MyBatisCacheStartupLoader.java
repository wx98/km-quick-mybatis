package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.persistent;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.DefaultMyBatisCacheManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MyBatisCacheStartupLoader extends MyBatisCachePersistenceManager implements ProjectActivity {

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        loadCache(project);

        DefaultMyBatisCacheManager cacheManager = DefaultMyBatisCacheManager.getInstance(project);
        this.lastKnownVersion = cacheManager.getCurrentCacheVersion();

        // 注册版本检查定时任务
        scheduleVersionCheck(project);

        return null;
    }


    private long lastKnownVersion;


    /**
     * 定时检查缓存版本是否更新
     */
    private void scheduleVersionCheck(Project project) {
        java.util.Timer timer = new java.util.Timer(true);
        timer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                if (project.isDisposed()) {
                    timer.cancel();
                    return;
                }

                DefaultMyBatisCacheManager cacheManager = DefaultMyBatisCacheManager.getInstance(project);

                if (!cacheManager.isCacheUpToDate(lastKnownVersion)) {
                    // 缓存已更新，执行相应的处理逻辑
                    handleCacheUpdate(project);
                    lastKnownVersion = cacheManager.getCurrentCacheVersion();
                }
            }
        }, 0, 5000); // 每5秒检查一次
    }

    /**
     * 处理缓存更新逻辑
     */
    private void handleCacheUpdate(Project project) {
        // 可以在这里通知其他组件缓存已更新
        // 例如刷新UI、更新相关视图等
    }
}
