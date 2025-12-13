package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.XmlElementInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
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
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
        if (file != null && file.exists()) {
            try {
                byte[] content = file.contentsToByteArray();
                return Integer.toHexString(Arrays.hashCode(content));
            } catch (Exception e) {
                return "";
            }
        } else {
            return "";
        }
    }

}
