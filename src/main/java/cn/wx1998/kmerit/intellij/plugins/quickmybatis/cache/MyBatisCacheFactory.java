package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * MyBatis缓存工厂
 */
public final class MyBatisCacheFactory {

    private MyBatisCacheFactory() {
        throw new UnsupportedOperationException();
    }

    public static MyBatisCache createDefaultParser(@NotNull Project project) {
        return MyBatisCacheDefault.getInstance(project);
    }

    public static MyBatisCache getRecommendedParser(@NotNull Project project) {
        return createDefaultParser(project);
    }
}