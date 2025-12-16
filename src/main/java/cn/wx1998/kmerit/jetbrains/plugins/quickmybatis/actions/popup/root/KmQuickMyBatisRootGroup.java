package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.actions.popup.root;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.actions.popup.root.leve1.leve2.RefreshActionGroup;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.MyBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 一级根菜单：km-quick-mybatis
 */
public class KmQuickMyBatisRootGroup extends ActionGroup {


    // 构造函数：设置根菜单名称，true表示作为子菜单展示
    public KmQuickMyBatisRootGroup() {
        super(MyBundle.message("km.quick.mybatis.root"), true);
    }

    /**
     * 返回根菜单下的子项
     */
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
//         return new AnAction[]{new AddMethodAction(), new RefreshActionGroup()};
        return new AnAction[]{new RefreshActionGroup()};
    }
}