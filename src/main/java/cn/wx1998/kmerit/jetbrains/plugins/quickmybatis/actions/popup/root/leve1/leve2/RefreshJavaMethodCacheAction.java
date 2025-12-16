package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.actions.popup.root.leve1.leve2;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.MyBatisCacheManager;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.MyBatisCacheManagerFactory;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.MyBatisCacheRefreshRange;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.MyBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 二级子项：刷新Java方法调用缓存
 */
public class RefreshJavaMethodCacheAction extends AnAction {

    public RefreshJavaMethodCacheAction() {
        super(MyBundle.message("km.quick.mybatis.refresh.java.method"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        MyBatisCacheManager cacheManager = MyBatisCacheManagerFactory.getRecommendedParser(Objects.requireNonNull(e.getProject()));
        cacheManager.performFullCacheRefresh(MyBatisCacheRefreshRange.JAVA_METHOD_CALL, 0);
    }
}