package cn.wx1998.kmerit.intellij.plugins.quickmybatis;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Test;

/**
 * MyPluginTest 类继承自 BasePlatformTestCase，用于测试 MyPlugin 插件的功能。
 */
@TestDataPath("$CONTENT_ROOT/src/test/testData")
public class MyPluginTest extends BasePlatformTestCase {

    private static final Logger LOG = Logger.getInstance(MyPluginTest.class);
    /**
     * 测试重命名功能。
     */
    @Test
    public void test() {
        LOG.warn("test");
    }

    /**
     * 获取测试数据路径。
     *
     * @return 测试数据路径字符串。
     */
    @Override
    protected String getTestDataPath() {
        return "src/test/testData/rename";
    }
}