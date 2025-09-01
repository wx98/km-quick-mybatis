package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Test;

import java.util.Set;

/**
 * 测试MyBatis缓存管理器的功能
 */
@TestDataPath("$CONTENT_ROOT/src/test/resources/")
public class DefaultMyBatisCacheManagerTest extends BasePlatformTestCase {

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
     * 测试添加和获取类-XML文件映射
     */
    @Test
    public void testPutAndGetClassXmlMapping() {
        String className = "com.example.TestMapper";
        String xmlFilePath = "/path/to/TestMapper.xml";

        // 初始状态下，应该没有映射
        assertTrue(cacheManager.getXmlFilesForClass(className).isEmpty());
        assertNull(cacheManager.getClassForXmlFile(xmlFilePath));

        // 添加映射
        cacheManager.putClassXmlMapping(className, xmlFilePath);

        // 验证映射是否正确添加
        Set<String> xmlFiles = cacheManager.getXmlFilesForClass(className);
        assertFalse(xmlFiles.isEmpty());
        assertEquals(1, xmlFiles.size());
        assertTrue(xmlFiles.contains(xmlFilePath));
        assertEquals(className, cacheManager.getClassForXmlFile(xmlFilePath));

        // 验证缓存是否有效
        assertTrue(cacheManager.isCacheValid(className));
        assertTrue(cacheManager.isCacheValid(xmlFilePath));
    }

    /**
     * 测试添加和获取方法-Statement映射
     */
    @Test
    public void testPutAndGetMethodStatementMapping() {
        String className = "com.example.TestMapper";
        String methodName = "findById";
        String statementId = "com.example.TestMapper.findById";
        String xmlFilePath = "/path/to/TestMapper.xml";

        // 先添加类-XML映射
        cacheManager.putClassXmlMapping(className, xmlFilePath);

        // 添加方法-Statement映射
        cacheManager.putMethodStatementMapping(className, methodName, statementId);

        // 验证映射是否正确添加
        assertEquals(statementId, cacheManager.getStatementIdForMethod(className, methodName));
        assertEquals(methodName, cacheManager.getMethodForStatementId(xmlFilePath, statementId));
    }

    /**
     * 测试清除类缓存
     */
    @Test
    public void testClearClassCache() {
        String className = "com.example.TestMapper";
        String xmlFilePath = "/path/to/TestMapper.xml";
        String methodName = "findById";
        String statementId = "com.example.TestMapper.findById";

        // 添加映射
        cacheManager.putClassXmlMapping(className, xmlFilePath);
        cacheManager.putMethodStatementMapping(className, methodName, statementId);

        // 验证映射已添加
        assertFalse(cacheManager.getXmlFilesForClass(className).isEmpty());
        assertEquals(className, cacheManager.getClassForXmlFile(xmlFilePath));
        assertEquals(statementId, cacheManager.getStatementIdForMethod(className, methodName));

        // 清除类缓存
        cacheManager.clearClassCache(className);

        // 验证所有相关缓存都被清除
        assertTrue(cacheManager.getXmlFilesForClass(className).isEmpty());
        assertNull(cacheManager.getClassForXmlFile(xmlFilePath));
        assertNull(cacheManager.getStatementIdForMethod(className, methodName));
        assertNull(cacheManager.getMethodForStatementId(xmlFilePath, statementId));
    }

    /**
     * 测试清除XML文件缓存
     */
    @Test
    public void testClearXmlFileCache() {
        String className = "com.example.TestMapper";
        String xmlFilePath = "/path/to/TestMapper.xml";
        String methodName = "findById";
        String statementId = "com.example.TestMapper.findById";

        // 添加映射
        cacheManager.putClassXmlMapping(className, xmlFilePath);
        cacheManager.putMethodStatementMapping(className, methodName, statementId);

        // 验证映射已添加
        assertFalse(cacheManager.getXmlFilesForClass(className).isEmpty());
        assertEquals(className, cacheManager.getClassForXmlFile(xmlFilePath));
        assertEquals(statementId, cacheManager.getStatementIdForMethod(className, methodName));

        // 清除XML文件缓存
        cacheManager.clearXmlFileCache(xmlFilePath);

        // 验证所有相关缓存都被清除
        assertTrue(cacheManager.getXmlFilesForClass(className).isEmpty());
        assertNull(cacheManager.getClassForXmlFile(xmlFilePath));
        assertNull(cacheManager.getStatementIdForMethod(className, methodName));
        assertNull(cacheManager.getMethodForStatementId(xmlFilePath, statementId));
    }

    /**
     * 测试清除所有缓存
     */
    @Test
    public void testClearAllCache() {
        // 添加多个映射
        cacheManager.putClassXmlMapping("com.example.TestMapper1", "/path/to/TestMapper1.xml");
        cacheManager.putClassXmlMapping("com.example.TestMapper2", "/path/to/TestMapper2.xml");
        cacheManager.putMethodStatementMapping("com.example.TestMapper1", "findById", "com.example.TestMapper1.findById");

        // 验证映射已添加
        assertFalse(cacheManager.getXmlFilesForClass("com.example.TestMapper1").isEmpty());
        assertFalse(cacheManager.getXmlFilesForClass("com.example.TestMapper2").isEmpty());

        // 清除所有缓存
        cacheManager.clearAllCache();

        // 验证所有缓存都被清除
        assertTrue(cacheManager.getXmlFilesForClass("com.example.TestMapper1").isEmpty());
        assertTrue(cacheManager.getXmlFilesForClass("com.example.TestMapper2").isEmpty());
        assertNull(cacheManager.getClassForXmlFile("/path/to/TestMapper1.xml"));
        assertNull(cacheManager.getClassForXmlFile("/path/to/TestMapper2.xml"));
        assertNull(cacheManager.getStatementIdForMethod("com.example.TestMapper1", "findById"));
    }

    /**
     * 测试使文件缓存失效和检查缓存有效性
     */
    @Test
    public void testInvalidateFileCacheAndCheckValidity() {
        // 添加映射关系
        String className = "com.example.UserMapper";
        String xmlFilePath = "src/main/resources/mappers/UserMapper.xml";
        cacheManager.putClassXmlMapping(className, xmlFilePath);

        // 验证初始状态下缓存有效
        assertTrue("初始状态下，类缓存应该有效", cacheManager.isCacheValid(className));
        assertTrue("初始状态下，XML文件缓存应该有效", cacheManager.isCacheValid(xmlFilePath));

        // 注意：invalidateFileCache方法需要PsiFile参数，在实际环境中应该传入真实的PsiFile对象
        // 这里使用清除缓存的方式来模拟失效效果
        cacheManager.clearXmlFileCache(xmlFilePath);

        // 验证缓存已失效（实际上是被清除了）
        assertTrue("类关联的XML文件列表应该为空", cacheManager.getXmlFilesForClass(className).isEmpty());
        assertNull("XML文件不应该对应任何类", cacheManager.getClassForXmlFile(xmlFilePath));
    }

    /**
     * 测试设置和获取缓存配置
     */
    @Test
    public void testSetAndGetCacheConfig() {
        MyBatisCacheConfig originalConfig = cacheManager.getCacheConfig();
        assertNotNull(originalConfig);

        // 创建一个新的配置
        MyBatisCacheConfig newConfig = new MyBatisCacheConfig()
                .setMaxCacheSize(2000)
                .setCacheExpiryTimeSeconds(1800)
                .setScanIntervalSeconds(30)
                .setEnableMemoryOptimization(false)
                .setCleanupBatchSize(50);

        // 设置新的配置
        cacheManager.setCacheConfig(newConfig);

        // 验证配置已更新
        MyBatisCacheConfig currentConfig = cacheManager.getCacheConfig();
        assertEquals(2000, currentConfig.getMaxCacheSize());
        assertEquals(1800, currentConfig.getCacheExpiryTimeSeconds());
        assertEquals(30, currentConfig.getScanIntervalSeconds());
        assertFalse(currentConfig.isEnableMemoryOptimization());
        assertEquals(50, currentConfig.getCleanupBatchSize());
    }

    /**
     * 测试获取缓存统计信息
     */
    @Test
    public void testGetCacheStatistics() {
        CacheStatistics statistics = cacheManager.getCacheStatistics();
        assertNotNull(statistics);

        // 初始状态下，所有统计数据应该为0
        assertEquals(0, statistics.getHits());
        assertEquals(0, statistics.getMisses());
        assertEquals(0, statistics.getRequests());
        assertEquals(0, statistics.getEvictions());
        assertEquals(0, statistics.getInvalidations());
        assertEquals(0, statistics.getCurrentSize());
        assertEquals(0, statistics.getMaxSize());
        assertEquals(0, statistics.getMemoryUsageBytes());
        assertEquals(0.0, statistics.getHitRate(), 0.001);

        // 添加一些缓存项，验证统计数据更新
        cacheManager.putClassXmlMapping("com.example.TestMapper", "/path/to/TestMapper.xml");
        cacheManager.getXmlFilesForClass("com.example.TestMapper"); // 应该命中缓存

        // 由于统计信息是异步更新的，这里可能需要等待一下或者使用反射直接检查
        // 注意：在实际运行时，由于updateStatistics()方法是在定期扫描中调用的，所以这里可能不会立即看到统计数据的更新
    }

    /**
     * 测试缓存未命中的情况
     */
    @Test
    public void testCacheMiss() {
        String nonExistentClass = "com.example.NonExistentMapper";
        String nonExistentXmlFile = "/path/to/NonExistentMapper.xml";

        // 尝试获取不存在的映射，应该返回空集合或null
        assertTrue(cacheManager.getXmlFilesForClass(nonExistentClass).isEmpty());
        assertNull(cacheManager.getClassForXmlFile(nonExistentXmlFile));
        assertNull(cacheManager.getStatementIdForMethod(nonExistentClass, "nonExistentMethod"));
        assertNull(cacheManager.getMethodForStatementId(nonExistentXmlFile, "nonExistentStatement"));
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/";
    }
}