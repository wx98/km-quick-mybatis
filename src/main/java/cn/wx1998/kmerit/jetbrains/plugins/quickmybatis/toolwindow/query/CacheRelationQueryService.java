package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.toolwindow.query;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.MyBatisCache;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.MyBatisCacheManager;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.MyBatisCacheManagerFactory;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.info.XmlElementInfo;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.services.JavaService;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.toolwindow.CacheTreeNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CacheRelationQueryService {
    public enum Tab {CURRENT, ALL}

    public enum DataType {ALL, JAVA, XML}

    public enum CacheScope {ALL, RELATED}

    public enum GroupMode {NONE, DIRECTORY, MODULE}

    private final Project project;
    private final MyBatisCacheManager cacheManager;

    public CacheRelationQueryService(@NotNull Project project) {
        this.project = project;
        this.cacheManager = MyBatisCacheManagerFactory.getRecommendedParser(project);
    }

    public DefaultMutableTreeNode buildTree(@NotNull Tab tab, @NotNull DataType dataType, @NotNull CacheScope scope, @NotNull GroupMode groupMode, String searchText) {
        MyBatisCache cache = cacheManager.getCacheConfig();
        Map<String, Set<JavaElementInfo>> javaBySql = cache.getSqlIdToJavaElements();
        Map<String, Set<XmlElementInfo>> xmlBySql = cache.getSqlIdToXmlElements();
        if (tab == Tab.CURRENT) {
            return buildCurrentTree(cache, javaBySql, xmlBySql, scope, searchText);
        }
        Set<String> sqlIds = new LinkedHashSet<>();
        sqlIds.addAll(javaBySql.keySet());
        sqlIds.addAll(xmlBySql.keySet());
        String currentPath = currentPath();

        DefaultMutableTreeNode root = new CacheTreeNode("root", tab == Tab.CURRENT ? "当前缓存" : "全部缓存");
        Map<String, CacheTreeNode> groups = new LinkedHashMap<>();
        sqlIds.stream().filter(id -> related(id, javaBySql, xmlBySql, scope)).filter(id -> matches(id, javaBySql.get(id), xmlBySql.get(id), searchText)).sorted().forEach(id -> {
            Set<JavaElementInfo> javaRecords = javaBySql.getOrDefault(id, Set.of());
            Set<XmlElementInfo> xmlRecords = xmlBySql.getOrDefault(id, Set.of());
            if (dataType == DataType.JAVA && javaRecords.isEmpty()) return;
            if (dataType == DataType.XML && xmlRecords.isEmpty()) return;
            CacheTreeNode parent = rootFor(groupMode, root, groups, firstPath(javaRecords, xmlRecords));
            CacheTreeNode sqlNode = new CacheTreeNode("sql:" + id, sqlLabel(id, javaRecords, xmlRecords));
            parent.add(sqlNode);
            if (dataType != DataType.XML) addJavaNodes(sqlNode, javaRecords, javaRecords.size() > 1);
            if (dataType != DataType.JAVA) addXmlNodes(sqlNode, xmlRecords);
        });
        if (root.getChildCount() == 0) root.add(new CacheTreeNode("empty", "没有匹配的缓存关系"));
        return root;
    }

    private DefaultMutableTreeNode buildCurrentTree(MyBatisCache cache, Map<String, Set<JavaElementInfo>> javaBySql, Map<String, Set<XmlElementInfo>> xmlBySql, CacheScope scope, String searchText) {
        String path = currentPath();
        boolean javaFile = path != null && path.toLowerCase().endsWith(".java");
        boolean xmlFile = path != null && path.toLowerCase().endsWith(".xml");
        CacheTreeNode root = new CacheTreeNode("current:" + String.valueOf(path), path == null ? "当前文件" : displayPath(path));
        if (!javaFile && !xmlFile) {
            root.add(new CacheTreeNode("empty", "当前文件不是 Java 或 XML 文件"));
            return root;
        }
        Set<String> currentSqlIds = cache.getAllSqlIdByFilePath(path);
        boolean singleSqlId = currentSqlIds.size() == 1;
        if (singleSqlId) {
            String sqlId = currentSqlIds.iterator().next();
            if (!related(sqlId, javaBySql, xmlBySql, scope) || !matches(sqlId, javaBySql.get(sqlId), xmlBySql.get(sqlId), searchText)) {
                root.add(new CacheTreeNode("empty", "当前文件没有匹配的缓存关系"));
            }
            return root;
        }
        currentSqlIds.stream().sorted().forEach(sqlId -> {
            Set<JavaElementInfo> allJavaRecords = javaBySql.getOrDefault(sqlId, Set.of());
            Set<XmlElementInfo> allXmlRecords = xmlBySql.getOrDefault(sqlId, Set.of());
            if (!related(sqlId, javaBySql, xmlBySql, scope) || !matches(sqlId, allJavaRecords, allXmlRecords, searchText)) return;
            CacheTreeNode sqlNode;
            if (javaFile && countRecordsForPath(allJavaRecords, path) == 1 && allXmlRecords.size() == 1) {
                XmlElementInfo target = allXmlRecords.iterator().next();
                sqlNode = new CacheTreeNode("current-sql:" + sqlId, sqlLabel(sqlId, allJavaRecords, allXmlRecords) + databaseLabel(target.getDatabaseId()), target.getFilePath(), target.getStartOffset());
            } else if (xmlFile && countRecordsForPath(allXmlRecords, path) == 1 && allJavaRecords.size() == 1) {
                JavaElementInfo target = allJavaRecords.iterator().next();
                sqlNode = new CacheTreeNode("current-sql:" + sqlId, sqlLabel(sqlId, allJavaRecords, allXmlRecords), target.getFilePath(), target.getStartOffset());
            } else {
                sqlNode = new CacheTreeNode("current-sql:" + sqlId, sqlLabel(sqlId, allJavaRecords, allXmlRecords));
            }
            root.add(sqlNode);
            if (javaFile && !sqlNode.canNavigate()) {
                addXmlNodes(sqlNode, allXmlRecords);
            } else if (xmlFile && !sqlNode.canNavigate()) {
                addJavaNodes(sqlNode, allJavaRecords, true);
            }
        });
        if (root.getChildCount() == 0) root.add(new CacheTreeNode("empty", "当前文件没有匹配的缓存关系"));
        return root;
    }

    private void addJavaNodes(CacheTreeNode parent, Set<JavaElementInfo> records, boolean showReference) {
        Map<String, List<JavaElementInfo>> byFile = new LinkedHashMap<>();
        records.stream().sorted(Comparator.comparing(JavaElementInfo::getFilePath).thenComparingInt(JavaElementInfo::getStartOffset)).forEach(r -> byFile.computeIfAbsent(r.getFilePath(), k -> new ArrayList<>()).add(r));
        byFile.forEach((path, list) -> {
            boolean interfaceFile = list.stream().anyMatch(r -> JavaService.TYPE_INTERFACE_CLASS.equals(r.getElementType()));
            CacheTreeNode file = new CacheTreeNode("java-file:" + (interfaceFile ? "interface:" : "class:") + path,
                    displayPath(path), path, list.get(0).getStartOffset());
            parent.add(file);
            if (showReference && list.size() > 1) for (JavaElementInfo r : list) {
                String label = "line:" + line(r.getFilePath(), r.getStartOffset()) + " " + r.getSqlId();
                file.add(new CacheTreeNode(javaKey(r), label, r.getFilePath(), r.getStartOffset()));
            }
        });
    }

    private void addXmlNodes(CacheTreeNode parent, Set<XmlElementInfo> records) {
        Map<String, List<XmlElementInfo>> byVariant = new LinkedHashMap<>();
        records.stream().sorted(Comparator.comparing(XmlElementInfo::getFilePath).thenComparingInt(XmlElementInfo::getStartOffset)).forEach(r -> byVariant.computeIfAbsent(r.getFilePath() + "|" + String.valueOf(r.getDatabaseId()), k -> new ArrayList<>()).add(r));
        byVariant.forEach((variant, list) -> {
            XmlElementInfo first = list.get(0);
            String databaseId = first.getDatabaseId();
            String label = displayPath(first.getFilePath()) + (databaseId == null || databaseId.isEmpty() ? "" : " [" + databaseId + "]");
            CacheTreeNode file = new CacheTreeNode("xml-variant:" + variant, label, first.getFilePath(), first.getStartOffset());
            parent.add(file);
            if (list.size() > 1) {
                for (XmlElementInfo r : list) {
                    file.add(new CacheTreeNode(xmlKey(r), "line:" + line(r.getFilePath(), r.getStartOffset()) + " " + xmlSqlId(r) + databaseLabel(r.getDatabaseId()), r.getFilePath(), r.getStartOffset()));
                }
            }
        });
    }

    private CacheTreeNode rootFor(GroupMode mode, DefaultMutableTreeNode root, Map<String, CacheTreeNode> groups, String path) {
        if (mode == GroupMode.NONE) return (CacheTreeNode) root;
        String group = mode == GroupMode.DIRECTORY ? directory(path) : module(path);
        return groups.computeIfAbsent(group, key -> {
            CacheTreeNode node = new CacheTreeNode(mode.name() + ":" + key, key);
            root.add(node);
            return node;
        });
    }

    private String directory(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash > 0 ? displayPath(path.substring(0, slash)) : "项目根目录";
    }

    private String module(String path) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        if (file == null) return "未归属模块";
        com.intellij.openapi.module.Module module = com.intellij.openapi.roots.ProjectFileIndex.getInstance(project).getModuleForFile(file);
        return module == null ? "未归属模块" : module.getName();
    }

    private String currentPath() {
        VirtualFile[] selectedFiles = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedFiles();
        if (selectedFiles.length > 0) return selectedFiles[0].getPath();
        com.intellij.openapi.fileEditor.FileEditor editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedEditor();
        VirtualFile file = editor == null ? null : editor.getFile();
        return file == null ? null : file.getPath();
    }

    public String currentPathForNavigation() {
        return currentPath();
    }

    private boolean related(String id, Map<String, Set<JavaElementInfo>> javaBySql, Map<String, Set<XmlElementInfo>> xmlBySql, CacheScope scope) {
        return scope == CacheScope.ALL || (!javaBySql.getOrDefault(id, Set.of()).isEmpty() && !xmlBySql.getOrDefault(id, Set.of()).isEmpty());
    }

    private boolean matches(String id, Set<JavaElementInfo> javaRecords, Set<XmlElementInfo> xmlRecords, String text) {
        javaRecords = javaRecords == null ? Set.of() : javaRecords;
        xmlRecords = xmlRecords == null ? Set.of() : xmlRecords;
        if (text == null || text.trim().isEmpty()) return true;
        String value = text.toLowerCase();
        if (id.toLowerCase().contains(value)) return true;
        for (JavaElementInfo r : javaRecords) if ((r.getFilePath() + r.getElementType()).toLowerCase().contains(value)) return true;
        for (XmlElementInfo r : xmlRecords) if ((r.getFilePath() + r.getTagName() + r.getDatabaseId()).toLowerCase().contains(value)) return true;
        return false;
    }

    private String sqlLabel(String sqlId, Set<JavaElementInfo> javaRecords, Set<XmlElementInfo> xmlRecords) {
        if (!xmlRecords.isEmpty()) return xmlSqlId(xmlRecords.iterator().next());
        return sqlId;
    }

    private String xmlSqlId(XmlElementInfo record) {
        String sqlId = record.getSqlId();
        if (sqlId == null || sqlId.isEmpty() || "mapper".equalsIgnoreCase(record.getTagName())) return sqlId;
        String namespace = ReadAction.compute(() -> {
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(record.getFilePath());
            if (file == null) return null;
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (!(psiFile instanceof XmlFile xmlFile) || xmlFile.getDocument() == null) return null;
            XmlTag rootTag = xmlFile.getDocument().getRootTag();
            return rootTag == null ? null : rootTag.getAttributeValue("namespace");
        });
        if (namespace == null || namespace.isEmpty() || sqlId.equals(namespace) || sqlId.startsWith(namespace + ".")) {
            return sqlId;
        }
        return namespace + "." + sqlId;
    }

    private String firstPath(Set<JavaElementInfo> javaRecords, Set<XmlElementInfo> xmlRecords) {
        if (!javaRecords.isEmpty()) return javaRecords.iterator().next().getFilePath();
        return xmlRecords.iterator().next().getFilePath();
    }

    private String displayPath(String path) {
        if (path == null || project.getBasePath() == null) return path;
        String relativePath = FileUtil.getRelativePath(project.getBasePath(), path, '/');
        return relativePath == null ? path : relativePath;
    }

    private String databaseLabel(String databaseId) {
        return databaseId == null || databaseId.isEmpty() ? "" : " [" + databaseId + "]";
    }

    private int countRecordsForPath(Set<?> records, String path) {
        int count = 0;
        for (Object record : records) {
            String recordPath = record instanceof JavaElementInfo ? ((JavaElementInfo) record).getFilePath() : ((XmlElementInfo) record).getFilePath();
            if (path.equals(recordPath)) count++;
        }
        return count;
    }

    private int line(String path, int offset) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        if (file == null) return 0;
        return ReadAction.compute(() -> {
            com.intellij.openapi.editor.Document document = FileDocumentManager.getInstance().getDocument(file);
            return document == null ? 0 : document.getLineNumber(Math.min(offset, document.getTextLength())) + 1;
        });
    }

    private String javaKey(JavaElementInfo r) {
        String prefix = JavaService.TYPE_METHOD_CALL.equals(r.getElementType()) ? "java-call:" : "java:";
        return prefix + r.getFilePath() + ":" + r.getStartOffset() + ":" + r.getEndOffset() + ":" + r.getSqlId();
    }

    private String xmlKey(XmlElementInfo r) {
        return "xml:" + r.getFilePath() + ":" + r.getDatabaseId() + ":" + r.getStartOffset() + ":" + r.getEndOffset() + ":" + r.getSqlId();
    }
}
