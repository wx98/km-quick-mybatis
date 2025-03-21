package cn.wx1998.kmerit.intellij.plugins.quickmybatis.toolWindow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.ContentFactory;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.MyBundle;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.services.MyProjectService;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;

/**
 * MyToolWindowFactory 类实现了 ToolWindowFactory 接口，用于创建和初始化工具窗口。
 */
public class MyToolWindowFactory implements ToolWindowFactory {

    private static final Logger LOG = Logger.getInstance(MyToolWindowFactory.class);

    /**
     * 构造函数，初始化时输出警告信息，提示移除不必要的示例代码文件。
     */
    public MyToolWindowFactory() {
        LOG.warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.");
    }

    /**
     * 创建工具窗口内容的方法。
     *
     * @param project     当前项目实例。
     * @param toolWindow  工具窗口实例。
     */
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        MyToolWindow myToolWindow = new MyToolWindow(toolWindow);
        ContentFactory contentFactory = ContentFactory.getInstance();
        final var content = contentFactory.createContent(myToolWindow.getContent(), null, false);
        toolWindow.getContentManager().addContent(content);
    }

    /**
     * 判断工具窗口是否应该对指定项目可用。
     *
     * @param project 当前项目实例。
     * @return 如果工具窗口应该可用则返回 true，否则返回 false。
     */
    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

    /**
     * MyToolWindow 内部类，用于创建工具窗口的具体内容。
     */
    public static class MyToolWindow {

        private final MyProjectService service;

        /**
         * 构造函数，初始化项目服务。
         *
         * @param toolWindow 工具窗口实例。
         */
        public MyToolWindow(@NotNull ToolWindow toolWindow) {
            this.service = toolWindow.getProject().getService(MyProjectService.class);
        }

        /**
         * 获取工具窗口的内容面板。
         *
         * @return 包含工具窗口内容的 JBPanel 实例。
         */
        public JBPanel<JBPanel<?>> getContent() {
            JBPanel<JBPanel<?>> panel = new JBPanel<>();
            JBLabel label = new JBLabel(MyBundle.message("randomLabel", "?"));
            JButton button = new JButton(MyBundle.message("shuffle"));

            panel.add(label);
            panel.add(button);

            button.addActionListener(e -> label.setText(MyBundle.message("randomLabel", service.getRandomNumber())));

            return panel;
        }
    }
}
