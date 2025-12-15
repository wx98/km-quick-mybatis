package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * MyBatis缓存工厂
 */
public class MyBatisCacheManagerFactory {

    private MyBatisCacheManagerFactory() {
        throw new UnsupportedOperationException();
    }

    public static MyBatisCacheManager createDefaultParser(@NotNull Project project) {
        return MyBatisCacheManagerDefault.getInstance(project);
    }

    public static MyBatisCacheManager getRecommendedParser(@NotNull Project project) {
        return createDefaultParser(project);
    }
}