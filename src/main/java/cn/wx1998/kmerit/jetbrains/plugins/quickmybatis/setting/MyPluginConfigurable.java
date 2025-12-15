package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.setting;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.classFilter.ClassFilter;
import lombok.Getter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 配置类，用于管理插件的设置界面。
 */
public class MyPluginConfigurable implements SearchableConfigurable {

    // 使用Getter注解，方便外部获取插件设置实例
    @Getter
    private final MyPluginSettings pluginSettings;

    // 设置表单对象，用于显示和编辑插件设置
    private MyPluginSettingsFrom mybatisSettingForm;


    /**
     * 构造函数，初始化插件设置实例。
     */
    public MyPluginConfigurable() {
        pluginSettings = MyPluginSettings.getInstance();
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
        ClassFilter[] filters = this.mybatisSettingForm.mybatisClassFilterEditor.getFilters();
        return !filterEquals(classFilters, filters);
    }

    /**
     * 应用配置更改。
     */
    @Override
    public void apply() {
        final var filters = this.mybatisSettingForm.mybatisClassFilterEditor.getFilters();
        MyPluginSettings.getInstance().setClassFilters(filters);
    }

    @Override
    public void reset() {
        final var filters = MyPluginSettings.getInstance().getClassFilters();
        this.mybatisSettingForm.mybatisClassFilterEditor.setFilters(filters);
    }
}