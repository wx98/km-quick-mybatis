package cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting;
// 导入相关类和接口

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.services.JavaService;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.ui.MybatisClassFilterEditor;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.Icons;
import com.github.weisj.jsvg.T;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.settings.ViewsGeneralSettings;
import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.debugger.ui.tree.render.PrimitiveRenderer;
import com.intellij.debugger.ui.tree.render.ToStringRenderer;
import com.intellij.execution.ui.ClassBrowser;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.gotoByName.SimpleChooseByNameModel;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.ui.*;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.classFilter.ClassFilterEditor;
import com.intellij.util.ui.JBUI;
import lombok.Getter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static java.awt.GridBagConstraints.*;

/**
 * 配置类，用于管理插件的设置界面。
 */
public class MyPluginConfigurable implements SearchableConfigurable {

    // 使用Getter注解，方便外部获取插件设置实例
    @Getter
    private final MyPluginSettings pluginSettings;

    // 设置表单对象，用于显示和编辑插件设置
    private MyPluginSettingsFrom mybatisSettingForm;

    private MybatisClassFilterEditor mybatisClassFilterEditor;

    /**
     * 构造函数，初始化插件设置实例。
     */
    public MyPluginConfigurable() {
        pluginSettings = MyPluginSettings.getInstance();
    }

    /**
     * 获取配置项的唯一标识符。
     *
     * @return 配置项的唯一标识符
     */
    @Override
    public @NotNull @NonNls String getId() {
        return "KmQuickMybatis";
    }

    /**
     * 获取配置项的显示名称。
     *
     * @return 配置项的显示名称
     */
    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return getId();
    }

    /**
     * 创建配置界面组件。
     *
     * @return 配置界面组件
     */
    @Override
    public @Nullable JComponent createComponent() {
        if (null == mybatisSettingForm) {
            this.mybatisSettingForm = new MyPluginSettingsFrom();
        }
        mybatisClassFilterEditor = this.mybatisSettingForm.mybatisClassFilterEditor;
        return mybatisSettingForm.main;
    }

    /**
     * 检查配置是否已修改。
     *
     * @return 如果配置已修改则返回true，否则返回false
     */
    @Override
    public boolean isModified() {
        ClassFilter[] classFilters = MyPluginSettings.getInstance().getClassFilters();
        ClassFilter[] filters = mybatisClassFilterEditor.getFilters();
        return !filterEquals(classFilters, filters);
    }

    /**
     * 应用配置更改。
     *
     * @throws ConfigurationException 如果配置更改应用失败则抛出异常
     */
    @Override
    public void apply() {
        final var filters = mybatisClassFilterEditor.getFilters();
        MyPluginSettings.getInstance().setClassFilters(filters);
    }

    @Override
    public void reset() {
        final var filters = MyPluginSettings.getInstance().getClassFilters();
        mybatisClassFilterEditor.setFilters(filters);
    }

    public static boolean filterEquals(ClassFilter[] filters1, ClassFilter[] filters2) {
        if (filters1.length != filters2.length) {
            return false;
        }
        final Set<ClassFilter> f1 = new HashSet<>(Math.max((int) (filters1.length / .75f) + 1, 16));
        final Set<ClassFilter> f2 = new HashSet<>(Math.max((int) (filters2.length / .75f) + 1, 16));
        Collections.addAll(f1, filters1);
        Collections.addAll(f2, filters2);
        return f2.equals(f1);
    }
}