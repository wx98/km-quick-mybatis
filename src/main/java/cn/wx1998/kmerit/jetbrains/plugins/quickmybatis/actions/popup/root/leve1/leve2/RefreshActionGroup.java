package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.actions.popup.root.leve1.leve2;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.MyBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 二级子菜单：刷新（包含4个刷新子项）
 */
public class RefreshActionGroup extends ActionGroup {

    // 构造函数：设置子菜单名称，true表示作为子菜单展示
    public RefreshActionGroup() {
        super(MyBundle.message("km.quick.mybatis.refresh"), true);
    }

    /**
     * 返回刷新子菜单下的所有二级子项
     */
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
        return new AnAction[]{
                new RefreshAllCacheAction(),        // 刷新所有缓存
                new RefreshXmlCacheAction(),        // 刷新Xml文件缓存
                new RefreshJavaFileCacheAction(),   // 刷新Java文件缓存
                new RefreshJavaMethodCacheAction()  // 刷新Java方法调用缓存
        };
    }

    /**
     * 控制刷新子菜单是否显示：仅当项目存在时显示
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean isVisible = e.getProject() != null;
        e.getPresentation().setVisible(isVisible);
        e.getPresentation().setEnabled(isVisible);
    }
}