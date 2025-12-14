package cn.wx1998.kmerit.intellij.plugins.quickmybatis.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class ProjectFileUtils {

    /**
     * 获取项目中指定类型的所有文件的路径
     *
     * @param project    project
     * @param extensions 类型列表范围
     * @return 符合类型列表范围的文件列表
     */
    public static List<String> getFilePathListByTypeInSourceRoots(@NotNull Project project, @NotNull String... extensions) {
        return ReadAction.compute(() -> {
            List<PsiFile> mybatisFiles = new ArrayList<>();
            for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentSourceRoots()) {
                if (contentRoot != null && contentRoot.isDirectory() && contentRoot.isValid()) {
                    findXmlFilesRecursively(project, contentRoot, mybatisFiles, extensions);
                }
            }
            List<String> filePathList = new ArrayList<>();
            mybatisFiles.forEach(file -> filePathList.add(file.getVirtualFile().getPath()));
            return filePathList;
        });
    }

    /**
     * 获取项目中指定类型的所有文件
     *
     * @param project    project
     * @param extensions 类型列表范围
     * @return 符合类型列表范围的文件列表
     */
    public static List<PsiFile> getVirtualFileListByTypeInSourceRoots(@NotNull Project project, @NotNull String... extensions) {
        return ReadAction.compute(() -> {
            List<PsiFile> mybatisFiles = new ArrayList<>();
            VirtualFile[] contentSourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
            for (VirtualFile contentRoot : contentSourceRoots) {
                if (contentRoot != null && contentRoot.isDirectory() && contentRoot.isValid()) {
                    findXmlFilesRecursively(project, contentRoot, mybatisFiles, extensions);
                }
            }
            return mybatisFiles;
        });
    }

    private static void findXmlFilesRecursively(@NotNull Project project, VirtualFile directory, List<PsiFile> result, String... extensions) {
        // 递归退出条件
        if (directory == null || !directory.isDirectory() || !directory.isValid() || extensions == null || extensions.length == 0)
            return;

        // 类型范围列表
        Set<String> extensionRange = new HashSet<>(Arrays.asList(extensions));

        VirtualFile[] children = directory.getChildren();
        // 循环当前目录内容
        for (VirtualFile file : children) {
            // 如果是文件夹则递归
            if (file.isDirectory()) {
                findXmlFilesRecursively(project, file, result, extensions);
            } else {
                String extension = file.getExtension() != null ? file.getExtension().toLowerCase() : null;
                // 如果在类型范围内则处理
                if (extensionRange.contains(extension)) {
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
                    // 当前文件已经忽略则跳过
                    if (fileIndex.isExcluded(file)) continue;
                    // 不是源码目录文件则跳过
                    if (!fileIndex.isInSourceContent(file)) continue;
                    // 加入结果中
                    result.add(psiFile);
                }

            }
        }
    }
}