package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.actions;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.MyBatisCacheManager;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.MyBatisCacheManagerFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 全局缓存刷新动作，可在菜单和快捷键中使用
 */
public class RefreshMyBatisCacheAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {

        Project project = e.getProject();
        if (project == null) return;

        MyBatisCacheManager cacheManager = MyBatisCacheManagerFactory.getRecommendedParser(project);

        cacheManager.performFullCacheRefresh(0);

    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 只在有项目打开时显示该动作
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
}