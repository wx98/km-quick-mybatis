package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.actions.popup.root.leve1;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.MyBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 一级子项：添加方法
 */
public class AddMethodAction extends AnAction {

    public AddMethodAction() {
        super(MyBundle.message("km.quick.mybatis.add.method"));
    }

    /**
     * 点击“添加方法”后的业务逻辑
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // todo : 选择方法名直接修改配置的逻辑，预留给后面修改
    }

}