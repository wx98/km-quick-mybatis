package cn.wx1998.kmerit.intellij.plugins.quickmybatis;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

/**
 * MyBundle 类用于加载和获取国际化资源文件中的消息。
 * 它继承自 DynamicBundle 类，并使用了多个注解来确保功能的正确性和兼容性。
 */
public final class MyBundle extends DynamicBundle {
    /**
     * 定义资源文件的路径前缀。
     */
    @NonNls
    private static final String BUNDLE = "messages.MyBundle";

    /**
     * 创建 MyBundle 类的单例实例。
     */
    private static final MyBundle INSTANCE = new MyBundle();

    /**
     * 私有构造函数，用于初始化资源文件路径前缀。
     */
    private MyBundle() {
        super(BUNDLE);
    }

    /**
     * 获取 MyBundle 类的单例实例。
     *
     * @return MyBundle 类的单例实例。
     */
    @NotNull
    public static MyBundle getBundle() {
        return INSTANCE;
    }

    /**
     * 根据键获取资源文件中的消息。
     *
     * @param key 消息键。
     * @param params 替换消息中的占位符的参数。
     * @return 资源文件中的消息。
     */
    @NotNull
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }

    /**
     * 提供延迟加载消息的方法。
     *
     * @param key 消息键。
     * @return 包含消息的 Supplier 对象。
     */
    @NotNull
    public static Supplier<String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key) {
        return INSTANCE.getLazyMessage(key);
    }
}