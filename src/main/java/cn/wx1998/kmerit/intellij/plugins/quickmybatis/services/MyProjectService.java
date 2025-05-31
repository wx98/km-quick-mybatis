package cn.wx1998.kmerit.intellij.plugins.quickmybatis.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.MyBundle;

/**
 * MyProjectService 类是一个项目级别的服务，用于提供项目相关的功能。
 */
@Service(Service.Level.PROJECT)
public final class MyProjectService { // 修改: 将类声明为 final

    private static final Logger LOG = Logger.getInstance(MyProjectService.class);

    /**
     * 构造函数，初始化时记录项目名称和警告信息。
     *
     * @param project 当前项目实例。
     */
    public MyProjectService(Project project) {
        LOG.info(MyBundle.message("projectService", project.getName()));
        LOG.warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.");
    }

    /**
     * 获取一个1到100之间的随机数。
     *
     * @return 一个1到100之间的随机整数。
     */
    public int getRandomNumber() {
        return (int) (Math.random() * 100) + 1;
    }
}