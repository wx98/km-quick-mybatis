package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.persistent;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseHandler;
import org.jetbrains.annotations.NotNull;

public class MyBatisCacheCloseSaver extends MyBatisCachePersistenceManager implements ProjectCloseHandler {

    @Override
    public boolean canClose(@NotNull Project project) {
        // 项目关闭时保存缓存
        saveCache(project);
        return true;
    }
}
