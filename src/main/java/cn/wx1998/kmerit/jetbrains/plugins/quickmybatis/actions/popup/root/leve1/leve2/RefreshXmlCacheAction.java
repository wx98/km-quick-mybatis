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
 * 二级子项：刷新Xml文件缓存
 */
public class RefreshXmlCacheAction extends AnAction {

    public RefreshXmlCacheAction() {
        super(MyBundle.message("km.quick.mybatis.refresh.xml"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        MyBatisCacheManager cacheManager = MyBatisCacheManagerFactory.getRecommendedParser(Objects.requireNonNull(e.getProject()));
        cacheManager.performFullCacheRefresh(MyBatisCacheRefreshRange.XML, 0);
    }

}