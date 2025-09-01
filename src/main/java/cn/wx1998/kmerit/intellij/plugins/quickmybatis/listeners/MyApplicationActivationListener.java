package cn.wx1998.kmerit.intellij.plugins.quickmybatis.listeners;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.MyPluginSettings;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.classFilter.ClassFilter;
import org.jetbrains.annotations.NotNull;

/**
 * MyApplicationActivationListener 类实现了 ApplicationActivationListener 接口，
 * 用于监听应用程序激活事件。
 */
public class MyApplicationActivationListener implements ApplicationActivationListener {

    // 获取日志记录器实例
    private static final Logger LOG = Logger.getInstance(MyApplicationActivationListener.class);

    /**
     * 当应用程序激活时调用此方法。
     *
     * @param ideFrame 当前的 IDE 窗口框架。
     */
    @Override
    public void applicationActivated(@NotNull IdeFrame ideFrame) {
        ClassFilter[] classFilters = MyPluginSettings.getInstance().getClassFilters();
        for (ClassFilter classFilter : classFilters) {
            final String pattern = classFilter.getPattern();
            LOG.debug("pattern:" + pattern);
        }
    }

    /**
     * 当应用程序停用时调用此方法。
     *
     * @param ideFrame 当前的 IDE 窗口框架。
     */
    @Override
    public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
        // 当前方法暂无实现
    }
}