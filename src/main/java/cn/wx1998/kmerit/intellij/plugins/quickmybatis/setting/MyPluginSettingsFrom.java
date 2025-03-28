package cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.ui.MybatisClassFilterEditor;
import com.intellij.debugger.ui.PatternFilterEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ComponentUtil;

import javax.swing.*;
import java.awt.*;


public class MyPluginSettingsFrom {
    public JPanel main;
    MybatisClassFilterEditor mybatisClassFilterEditor;
    public JScrollPane listPane;

    Project myProject;

    private void createUIComponents() {
        if (myProject == null) {
            myProject = getActiveProject();
        }
        mybatisClassFilterEditor =  new MybatisClassFilterEditor(myProject);
        mybatisClassFilterEditor.setFilters(MyPluginSettings.getInstance().getClassFilters());
    }

    public static Project getActiveProject() {
        // 方式1：使用官方API获取激活窗口（推荐）
        for (IdeFrame frame : WindowManager.getInstance().getAllProjectFrames()) {
            if (frame.getComponent().hasFocus()) {
                return frame.getProject();
            }
        }
        // 方式2：通过焦点链向上追溯
        Component focusOwner = FocusManager.getCurrentManager().getFocusOwner();
        if (focusOwner != null) {
            IdeFrame frame = ComponentUtil.getParentOfType((Class<? extends IdeFrame>)IdeFrame.class, focusOwner);
            if (frame != null) return frame.getProject();
        }

        // 方式3：获取最近访问项目（适用于多窗口场景）
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        return openProjects.length > 0 ? openProjects[0] : ProjectManager.getInstance().getDefaultProject();
    }

}
