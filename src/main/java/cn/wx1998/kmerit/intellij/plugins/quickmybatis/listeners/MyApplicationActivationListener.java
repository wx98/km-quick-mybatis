package cn.wx1998.kmerit.intellij.plugins.quickmybatis.listeners;

import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.IdeFrame;
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
        // 记录警告信息，提示移除不必要的示例代码文件
        LOG.warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.");
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