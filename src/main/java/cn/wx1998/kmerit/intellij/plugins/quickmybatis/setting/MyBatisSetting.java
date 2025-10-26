package cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * MyBatis 插件配置类，管理命名规则和方法匹配模式
 */
@State(
        name = "MyBatisSetting",
        storages = @Storage("quick-mybatis-settings.xml")
)
public class MyBatisSetting implements PersistentStateComponent<MyBatisSetting> {

    // 默认命名规则模板
    private String classNamingRule = "${className}";
    private String methodNamingRule = "${className}.${methodName}";
    private String fieldNamingRule = "${className}.${fieldName}";

    // 默认需要匹配的 SqlSession 方法模式（正则）
    private List<String> sqlSessionMethodPatterns = Arrays.asList(
            "select.*", "insert.*", "update.*", "delete.*"
    );

    public static MyBatisSetting getInstance(@NotNull Project project) {
        return project.getService(MyBatisSetting.class);
    }

    @Nullable
    @Override
    public MyBatisSetting getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull MyBatisSetting state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    // ========================= Getter/Setter =========================

    public String getClassNamingRule() {
        return classNamingRule;
    }

    public void setClassNamingRule(String classNamingRule) {
        this.classNamingRule = classNamingRule;
    }

    public String getMethodNamingRule() {
        return methodNamingRule;
    }

    public void setMethodNamingRule(String methodNamingRule) {
        this.methodNamingRule = methodNamingRule;
    }

    public String getFieldNamingRule() {
        return fieldNamingRule;
    }

    public void setFieldNamingRule(String fieldNamingRule) {
        this.fieldNamingRule = fieldNamingRule;
    }

    public List<String> getSqlSessionMethodPatterns() {
        return sqlSessionMethodPatterns;
    }

    public void setSqlSessionMethodPatterns(List<String> sqlSessionMethodPatterns) {
        this.sqlSessionMethodPatterns = sqlSessionMethodPatterns;
    }
}