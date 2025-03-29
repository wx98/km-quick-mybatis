package cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Arrays;

@State(name = "KmQuickMybatis", storages = @Storage(value = "$APP_CONFIG$/KmQuickMybatis.xml"))
public class MyPluginSettings implements PersistentStateComponent<MyPluginSettings>, Serializable {

    private ClassFilter[] classFilters = ClassFilter.EMPTY_ARRAY;

    public ClassFilter[] getClassFilters() {
        return classFilters;
    }

    public void setClassFilters(ClassFilter[] classFilters) {
        this.classFilters = classFilters;
    }

    public static MyPluginSettings getInstance() {
        MyPluginSettings service = ApplicationManager.getApplication().getService(MyPluginSettings.class);
        if (Arrays.equals(ClassFilter.EMPTY_ARRAY, service.classFilters)) {
            // 初始化默认值
            service.setClassFilters(
                    new ClassFilter[]{
                            new ClassFilter("org.apache.ibatis.session.SqlSession"),
                            new ClassFilter("org.mybatis.spring.SqlSessionTemplate"),
                            new ClassFilter("com.kmerit.core.dao.BaseDAOMybatis")
                    }
            );
        }
        return service;
    }

    @Override
    public @Nullable MyPluginSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull MyPluginSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
