package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.setting;

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

    public static MyPluginSettings getInstance() {
        MyPluginSettings service = ApplicationManager.getApplication().getService(MyPluginSettings.class);
        if (service == null) {
            // 创建默认实例作为fallback
            service = new MyPluginSettings();
            service.setClassFilters(
                    new ClassFilter[]{
                            new ClassFilter("org.apache.ibatis.session.SqlSession"),
                            new ClassFilter("org.mybatis.spring.SqlSessionTemplate"),
                            new ClassFilter("com.kmerit.core.dao.BaseDAOMybatis")
                    }
            );
        } else if (Arrays.equals(ClassFilter.EMPTY_ARRAY, service.classFilters)) {
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

    public ClassFilter[] getClassFilters() {
        return classFilters;
    }

    public void setClassFilters(ClassFilter[] classFilters) {
        this.classFilters = classFilters;
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
