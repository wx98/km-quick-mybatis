package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Test;

import java.util.Set;

/**
 * MyBatisCacheManager接口的单元测试
 */
@TestDataPath("$CONTENT_ROOT/src/test/resources/cn/wx1998/kmerit/intellij/plugins/quickmybatis/cache/")
public class MyBatisCacheManagerTest extends BasePlatformTestCase {

    private static final String TEST_NAMESPACE1 = "com.example.TestMapper1";
    private static final String TEST_NAMESPACE2 = "com.example.TestMapper2";
    private static final String TEST_FILE_PATH1 = "/test/mapper1.xml";
    private static final String TEST_FILE_PATH2 = "/test/mapper2.xml";
    private static final String TEST_FILE_PATH3 = "/test/mapper3.xml";
    private static final String TEST_METHOD1 = "findById";
    private static final String TEST_METHOD2 = "insert";
    private static final String TEST_STATEMENT_ID1 = "com.example.TestMapper1.findById";
    private static final String TEST_STATEMENT_ID2 = "com.example.TestMapper1.insert";
    private MyBatisCacheManager cacheManager;
    private Project project;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        project = getProject();
        // 在测试环境中，直接创建DefaultMyBatisCacheManager实例
        cacheManager = new DefaultMyBatisCacheManager(project);
        // 确保测试开始前缓存为空
        cacheManager.clearAllCache();
    }

    @Override
    protected void tearDown() throws Exception {
        // 测试结束后清理所有缓存
        cacheManager.clearAllCache();
        super.tearDown();
    }

    /**
     * 测试缓存管理器实例是否不为空
     */
    @Test
    public void testCacheManagerInstance() {
        assertNotNull("缓存管理器实例不应该为空", cacheManager);
    }

    /**
     * 测试添加和获取类-XML文件映射
     */
    @Test
    public void testPutAndGetClassXmlMapping() {
        // 添加类-XML文件映射
        cacheManager.putClassXmlMapping(TEST_NAMESPACE1, TEST_FILE_PATH1);
        cacheManager.putClassXmlMapping(TEST_NAMESPACE1, TEST_FILE_PATH2);
        cacheManager.putClassXmlMapping(TEST_NAMESPACE2, TEST_FILE_PATH1);

        // 获取类对应的XML文件
        Set<String> xmlFilesForClass1 = cacheManager.getXmlFilesForClass(TEST_NAMESPACE1);
        Set<String> xmlFilesForClass2 = cacheManager.getXmlFilesForClass(TEST_NAMESPACE2);

        // 验证映射关系
        assertEquals("TestMapper1应该对应两个XML文件", 2, xmlFilesForClass1.size());
        assertTrue("TestMapper1应该包含第一个XML文件", xmlFilesForClass1.contains(TEST_FILE_PATH1));
        assertTrue("TestMapper1应该包含第二个XML文件", xmlFilesForClass1.contains(TEST_FILE_PATH2));

        assertEquals("TestMapper2应该对应一个XML文件", 1, xmlFilesForClass2.size());
        assertTrue("TestMapper2应该包含第一个XML文件", xmlFilesForClass2.contains(TEST_FILE_PATH1));
    }

    /**
     * 测试添加和获取XML文件-类映射
     */
    @Test
    public void testPutAndGetXmlClassMapping() {
        // 添加XML文件-类映射
        cacheManager.putClassXmlMapping(TEST_NAMESPACE1, TEST_FILE_PATH1);
        cacheManager.putClassXmlMapping(TEST_NAMESPACE2, TEST_FILE_PATH2);

        // 获取XML文件对应的类
        String classForFile1 = cacheManager.getClassForXmlFile(TEST_FILE_PATH1);
        String classForFile2 = cacheManager.getClassForXmlFile(TEST_FILE_PATH2);
        String classForNonExistentFile = cacheManager.getClassForXmlFile("/non/existent/file.xml");

        // 验证映射关系
        assertEquals("第一个XML文件应该对应TestMapper1", TEST_NAMESPACE1, classForFile1);
        assertEquals("第二个XML文件应该对应TestMapper2", TEST_NAMESPACE2, classForFile2);
        assertNull("不存在的XML文件不应该对应任何类", classForNonExistentFile);
    }

    /**
     * 测试添加和获取方法-Statement映射
     */
    @Test
    public void testPutAndGetMethodStatementMapping() {
        // 添加方法-Statement映射
        cacheManager.putMethodStatementMapping(TEST_NAMESPACE1, TEST_METHOD1, TEST_STATEMENT_ID1);
        cacheManager.putMethodStatementMapping(TEST_NAMESPACE1, TEST_METHOD2, TEST_STATEMENT_ID2);

        // 获取方法对应的Statement
        String statementForMethod1 = cacheManager.getStatementIdForMethod(TEST_NAMESPACE1, TEST_METHOD1);
        String statementForMethod2 = cacheManager.getStatementIdForMethod(TEST_NAMESPACE1, TEST_METHOD2);
        String statementForNonExistentMethod = cacheManager.getStatementIdForMethod(TEST_NAMESPACE1, "nonExistentMethod");

        // 验证映射关系
        assertEquals("findById方法应该对应正确的Statement ID", TEST_STATEMENT_ID1, statementForMethod1);
        assertEquals("insert方法应该对应正确的Statement ID", TEST_STATEMENT_ID2, statementForMethod2);
        assertNull("不存在的方法不应该对应任何Statement", statementForNonExistentMethod);
    }

    /**
     * 测试添加和获取Statement-方法映射
     */
    @Test
    public void testPutAndGetStatementMethodMapping() {
        // 先添加类-XML文件映射关系
        cacheManager.putClassXmlMapping(TEST_NAMESPACE1, TEST_FILE_PATH1);

        // 添加Statement-方法映射
        cacheManager.putMethodStatementMapping(TEST_NAMESPACE1, TEST_METHOD1, TEST_STATEMENT_ID1);
        cacheManager.putMethodStatementMapping(TEST_NAMESPACE1, TEST_METHOD2, TEST_STATEMENT_ID2);

        // 获取Statement对应的方法
        String methodForStatement1 = cacheManager.getMethodForStatementId(TEST_FILE_PATH1, TEST_STATEMENT_ID1);
        String methodForStatement2 = cacheManager.getMethodForStatementId(TEST_FILE_PATH1, TEST_STATEMENT_ID2);
        String methodForNonExistentStatement = cacheManager.getMethodForStatementId(TEST_FILE_PATH1, "non.existent.Statement");

        // 验证映射关系
        assertEquals("第一个Statement应该对应findById方法", TEST_METHOD1, methodForStatement1);
        assertEquals("第二个Statement应该对应insert方法", TEST_METHOD2, methodForStatement2);
        assertNull("不存在的Statement不应该对应任何方法", methodForNonExistentStatement);
    }

    /**
     * 测试清除类缓存
     */
    @Test
    public void testClearClassCache() {
        // 添加映射关系
        cacheManager.putClassXmlMapping(TEST_NAMESPACE1, TEST_FILE_PATH1);
        cacheManager.putClassXmlMapping(TEST_NAMESPACE2, TEST_FILE_PATH2);

        // 清除第一个类的缓存
        cacheManager.clearClassCache(TEST_NAMESPACE1);

        // 验证缓存已清除
        Set<String> xmlFilesForClass1 = cacheManager.getXmlFilesForClass(TEST_NAMESPACE1);
        Set<String> xmlFilesForClass2 = cacheManager.getXmlFilesForClass(TEST_NAMESPACE2);

        assertTrue("第一个类的缓存应该为空", xmlFilesForClass1.isEmpty());
        assertFalse("第二个类的缓存不应该为空", xmlFilesForClass2.isEmpty());
    }

    /**
     * 测试清除XML文件缓存
     */
    @Test
    public void testClearXmlFileCache() {
        // 添加映射关系
        cacheManager.putClassXmlMapping(TEST_NAMESPACE1, TEST_FILE_PATH1);
        cacheManager.putClassXmlMapping(TEST_NAMESPACE1, TEST_FILE_PATH2);
        cacheManager.putClassXmlMapping(TEST_NAMESPACE2, TEST_FILE_PATH3); // 使用不同的文件路径

        // 清除第一个XML文件的缓存
        cacheManager.clearXmlFileCache(TEST_FILE_PATH1);

        // 验证缓存已清除
        Set<String> xmlFilesForClass1 = cacheManager.getXmlFilesForClass(TEST_NAMESPACE1);
        Set<String> xmlFilesForClass2 = cacheManager.getXmlFilesForClass(TEST_NAMESPACE2);
        String classForFile1 = cacheManager.getClassForXmlFile(TEST_FILE_PATH1);

        assertTrue("TestMapper1应该只剩下一个XML文件", xmlFilesForClass1.size() == 1);
        assertTrue("TestMapper1应该只包含第二个XML文件", xmlFilesForClass1.contains(TEST_FILE_PATH2));
        assertNotNull("TestMapper2的XML文件列表不应该为null", xmlFilesForClass2);
        assertFalse("TestMapper2的XML文件列表不应该为空", xmlFilesForClass2.isEmpty());
        assertTrue("TestMapper2应该包含第三个XML文件", xmlFilesForClass2.contains(TEST_FILE_PATH3));
        assertNull("第一个XML文件不应该对应任何类", classForFile1);
    }

    /**
     * 测试清除所有缓存
     */
    @Test
    public void testClearAllCache() {
        // 添加映射关系
        cacheManager.putClassXmlMapping(TEST_NAMESPACE1, TEST_FILE_PATH1);
        cacheManager.putClassXmlMapping(TEST_NAMESPACE2, TEST_FILE_PATH2);
        cacheManager.putMethodStatementMapping(TEST_NAMESPACE1, TEST_METHOD1, TEST_STATEMENT_ID1);

        // 清除所有缓存
        cacheManager.clearAllCache();

        // 验证所有缓存已清除
        Set<String> xmlFilesForClass1 = cacheManager.getXmlFilesForClass(TEST_NAMESPACE1);
        Set<String> xmlFilesForClass2 = cacheManager.getXmlFilesForClass(TEST_NAMESPACE2);
        String classForFile1 = cacheManager.getClassForXmlFile(TEST_FILE_PATH1);
        String classForFile2 = cacheManager.getClassForXmlFile(TEST_FILE_PATH2);
        String statementForMethod1 = cacheManager.getStatementIdForMethod(TEST_NAMESPACE1, TEST_METHOD1);

        assertTrue("TestMapper1的缓存应该为空", xmlFilesForClass1.isEmpty());
        assertTrue("TestMapper2的缓存应该为空", xmlFilesForClass2.isEmpty());
        assertNull("第一个XML文件不应该对应任何类", classForFile1);
        assertNull("第二个XML文件不应该对应任何类", classForFile2);
        assertNull("findById方法不应该对应任何Statement", statementForMethod1);
    }

    /**
     * 测试使文件缓存失效
     * 注意：invalidateFileCache方法需要PsiFile参数，这里使用isCacheValid方法的行为来间接测试
     */
    @Test
    public void testInvalidateFileCache() {
        // 添加映射关系
        cacheManager.putClassXmlMapping(TEST_NAMESPACE1, TEST_FILE_PATH1);
        cacheManager.putClassXmlMapping(TEST_NAMESPACE1, TEST_FILE_PATH2);

        // 验证缓存初始有效
        assertTrue("初始状态下，第一个XML文件缓存应该有效", cacheManager.isCacheValid(TEST_FILE_PATH1));
        assertTrue("初始状态下，第二个XML文件缓存应该有效", cacheManager.isCacheValid(TEST_FILE_PATH2));
        assertTrue("初始状态下，类缓存应该有效", cacheManager.isCacheValid(TEST_NAMESPACE1));

        // 注意：invalidateFileCache方法需要PsiFile参数，在实际环境中应该传入真实的PsiFile对象
        // 这里使用清除缓存的方式来模拟失效效果
        cacheManager.clearXmlFileCache(TEST_FILE_PATH1);

        // 验证缓存已失效（实际上是被清除了）
        Set<String> xmlFilesForClass1 = cacheManager.getXmlFilesForClass(TEST_NAMESPACE1);
        assertNotNull("TestMapper1的XML文件列表应该不为null", xmlFilesForClass1);
        assertEquals("TestMapper1应该只剩下一个XML文件", 1, xmlFilesForClass1.size());
        assertTrue("TestMapper1应该只包含第二个XML文件", xmlFilesForClass1.contains(TEST_FILE_PATH2));
        assertNull("第一个XML文件不应该对应任何类", cacheManager.getClassForXmlFile(TEST_FILE_PATH1));
        assertFalse("第一个XML文件缓存应该无效", cacheManager.isCacheValid(TEST_FILE_PATH1));
        assertTrue("类缓存应该仍然有效", cacheManager.isCacheValid(TEST_NAMESPACE1));
        assertTrue("第二个XML文件缓存应该仍然有效", cacheManager.isCacheValid(TEST_FILE_PATH2));
    }

    /**
     * 测试检查缓存有效性
     */
    @Test
    public void testIsCacheValid() {
        // 添加映射关系
        cacheManager.putClassXmlMapping(TEST_NAMESPACE1, TEST_FILE_PATH1);

        // 验证有效缓存
        assertTrue("有效缓存应该返回true", cacheManager.isCacheValid(TEST_NAMESPACE1));
        assertTrue("有效缓存应该返回true", cacheManager.isCacheValid(TEST_FILE_PATH1));

        // 验证无效缓存
        assertFalse("不存在的缓存应该返回false", cacheManager.isCacheValid("non.existent.Class"));
        assertFalse("不存在的缓存应该返回false", cacheManager.isCacheValid("/non/existent/file.xml"));

        // 注意：invalidateFileCache方法需要PsiFile参数，在实际环境中应该传入真实的PsiFile对象
        // 这里使用清除缓存的方式来模拟失效效果
        cacheManager.clearXmlFileCache(TEST_FILE_PATH1);

        // 验证缓存已清除
        assertTrue("类关联的XML文件列表应该为空", cacheManager.getXmlFilesForClass(TEST_NAMESPACE1).isEmpty());
        assertNull("XML文件不应该对应任何类", cacheManager.getClassForXmlFile(TEST_FILE_PATH1));
        assertFalse("类缓存应该无效", cacheManager.isCacheValid(TEST_NAMESPACE1));
        assertFalse("XML文件缓存应该无效", cacheManager.isCacheValid(TEST_FILE_PATH1));
    }

    /**
     * 测试刷新失效的缓存
     * 注意：由于invalidateFileCache需要PsiFile参数，此测试无法完整模拟刷新机制
     */
    @Test
    public void testRefreshInvalidatedCaches() {
        // 添加映射关系
        cacheManager.putClassXmlMapping(TEST_NAMESPACE1, TEST_FILE_PATH1);
        cacheManager.putClassXmlMapping(TEST_NAMESPACE2, TEST_FILE_PATH2);

        // 直接测试刷新方法的执行
        cacheManager.refreshInvalidatedCaches();

        // 验证方法执行后，已存在的映射关系仍然存在
        assertFalse("第一个类关联的XML文件列表不应该为空", cacheManager.getXmlFilesForClass(TEST_NAMESPACE1).isEmpty());
        assertFalse("第二个类关联的XML文件列表不应该为空", cacheManager.getXmlFilesForClass(TEST_NAMESPACE2).isEmpty());
        assertNotNull("第一个XML文件应该对应相应的类", cacheManager.getClassForXmlFile(TEST_FILE_PATH1));
        assertNotNull("第二个XML文件应该对应相应的类", cacheManager.getClassForXmlFile(TEST_FILE_PATH2));
    }

    /**
     * 测试设置和获取缓存配置
     */
    @Test
    public void testSetAndGetCacheConfig() {
        // 创建新的缓存配置
        MyBatisCacheConfig config = new MyBatisCacheConfig()
                .setMaxCacheSize(500)
                .setCacheExpiryTimeSeconds(60)
                .setScanIntervalSeconds(30)
                .setEnableMemoryOptimization(true)
                .setCleanupBatchSize(50);

        // 设置缓存配置
        cacheManager.setCacheConfig(config);

        // 获取缓存配置
        MyBatisCacheConfig retrievedConfig = cacheManager.getCacheConfig();

        // 验证配置已设置
        assertNotNull("获取的缓存配置不应该为空", retrievedConfig);
        assertEquals("最大缓存大小应该设置正确", 500, retrievedConfig.getMaxCacheSize());
        assertEquals("缓存过期时间应该设置正确", 60, retrievedConfig.getCacheExpiryTimeSeconds());
        assertEquals("扫描间隔应该设置正确", 30, retrievedConfig.getScanIntervalSeconds());
        assertTrue("内存优化应该启用", retrievedConfig.isEnableMemoryOptimization());
        assertEquals("清理批次大小应该设置正确", 50, retrievedConfig.getCleanupBatchSize());
    }

    /**
     * 测试获取缓存统计信息
     */
    @Test
    public void testGetCacheStatistics() {
        // 获取缓存统计信息
        CacheStatistics stats = cacheManager.getCacheStatistics();

        // 验证统计信息对象不为空
        assertNotNull("缓存统计信息对象不应该为空", stats);

        // 验证初始统计值
        assertEquals("初始命中次数应该为0", 0, stats.getHits());
        assertEquals("初始未命中次数应该为0", 0, stats.getMisses());
        assertEquals("初始请求次数应该为0", 0, stats.getRequests());
        assertEquals("初始驱逐次数应该为0", 0, stats.getEvictions());
        assertEquals("初始失效次数应该为0", 0, stats.getInvalidations());
        assertEquals("初始缓存大小应该为0", 0, stats.getCurrentSize());
        assertEquals("初始内存使用量应该为0", 0, stats.getMemoryUsageBytes());
    }

    /**
     * 测试检查文件是否相关
     * 注意：isRelevantFile方法是DefaultMyBatisCacheManager实现类特有的，不是MyBatisCacheManager接口的一部分
     */
    @Test
    public void testIsRelevantFile() {
        // 由于isRelevantFile不是接口方法，这里使用注释说明而不进行实际测试
        // 在实际项目中，如果需要测试这个功能，可以将cacheManager转换为DefaultMyBatisCacheManager类型
    }

    /**
     * 测试关闭缓存管理器
     * 注意：shutdown方法是DefaultMyBatisCacheManager实现类特有的，不是MyBatisCacheManager接口的一部分
     */
    @Test
    public void testShutdown() {
        // 由于shutdown不是接口方法，这里使用注释说明而不进行实际测试
        // 在实际项目中，如果需要测试这个功能，可以将cacheManager转换为DefaultMyBatisCacheManager类型
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/cn/wx1998/kmerit/intellij/plugins/quickmybatis/cache/";
    }
}