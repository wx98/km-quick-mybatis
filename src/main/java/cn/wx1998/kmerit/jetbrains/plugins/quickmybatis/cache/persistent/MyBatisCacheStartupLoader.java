package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.persistent;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.MyBatisCacheManager;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.MyBatisCacheManagerFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MyBatisCacheStartupLoader implements ProjectActivity {

    private long lastKnownVersion = 0;

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {

        MyBatisCacheManager cacheManager = MyBatisCacheManagerFactory.getRecommendedParser(project);
        this.lastKnownVersion = cacheManager.getCurrentCacheVersion();

        // 注册版本检查定时任务
        scheduleVersionCheck(project);

        return null;
    }

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
                MyBatisCacheManager cacheManager = MyBatisCacheManagerFactory.getRecommendedParser(project);
                cacheManager.checkForCacheInvalidationAndNotify(project);
            }
        }, 0, 5000); // 每5秒检查一次
    }
}
