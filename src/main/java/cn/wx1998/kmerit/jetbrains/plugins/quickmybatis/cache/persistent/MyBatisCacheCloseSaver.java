package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.persistent;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.DataBaseManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseHandler;
import org.jetbrains.annotations.NotNull;

public class MyBatisCacheCloseSaver implements ProjectCloseHandler {

    @Override
    public boolean canClose(@NotNull Project project) {
        DataBaseManager.getInstance(project).close();
        return true;
    }
}
