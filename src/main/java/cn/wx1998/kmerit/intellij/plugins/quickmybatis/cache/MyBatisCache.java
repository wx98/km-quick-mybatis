package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.XmlElementInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MyBatisCache {

    void addJavaElementMapping(@NotNull List<JavaElementInfo> javaElementInfoList);

    void addXmlElementMapping(@NotNull List<XmlElementInfo> xmlElementInfoList);

    @NotNull Set<JavaElementInfo> getJavaElementsBySqlId(@NotNull String sqlId);

    @NotNull Set<XmlElementInfo> getXmlElementsBySqlId(@NotNull String sqlId);

    @NotNull Set<String> getSqlIdsByJavaFile(@NotNull String javaFilePath);

    @NotNull Set<String> getSqlIdsByXmlFile(@NotNull String xmlFilePath);

    int saveFileDigest(@NotNull VirtualFile file, @NotNull String digest);

    @Nullable String getFileDigest(@NotNull VirtualFile file);

    @Nullable Map<String, String> getAllFileDigest();

    int clearJavaFileCache(@NotNull String javaFilePath);

    int clearXmlFileCache(@NotNull String xmlFilePath);

    int clearAllCache();

    Map<String, Set<JavaElementInfo>> getSqlIdToJavaElements();

    Map<String, Set<XmlElementInfo>> getSqlIdToXmlElements();

    Set<String> getAllSqlIdByFilePath(String filePath);

    Set<String> getAllFilePathsBySqlIdList(Set<String> stringSet);

    int removeBySqlIdList(Set<String> sqlIdList);

    /**
     * 计算文件摘要
     */
    default String calculateFileDigest(String filePath) {
        File file = new File(filePath);
        // 校验文件合法性
        if (file == null || !file.exists() || !file.isFile()) {
            return "";
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            // 获取消息摘要实例
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192]; // 8KB缓冲区，平衡内存和效率
            int readBytes;
            // 分块读取文件并更新摘要（避免大文件加载到内存）
            while ((readBytes = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, readBytes);
            }

            StringBuilder hexString = new StringBuilder();
            for (byte b : digest.digest()) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0'); // 补前导0，保证两位
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // 算法不支持时返回空
            System.err.println("不支持的哈希算法：" + "SHA-256");
            return "";
        } catch (IOException e) {
            // 文件读取异常时返回空
            System.err.println("读取文件失败：" + filePath + "，异常：" + e.getMessage());
            return "";
        }
    }

}
