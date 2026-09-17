package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.lifecycle;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.DataBaseManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseHandler;
import org.jetbrains.annotations.NotNull;

public class MyBatisCloseSaver implements ProjectCloseHandler {

    @Override
    public boolean canClose(@NotNull Project project) {
        DataBaseManager instance = project.getServiceIfCreated(DataBaseManager.class);
        if (instance != null) {
            instance.close();
        }
        return true;
    }
}
