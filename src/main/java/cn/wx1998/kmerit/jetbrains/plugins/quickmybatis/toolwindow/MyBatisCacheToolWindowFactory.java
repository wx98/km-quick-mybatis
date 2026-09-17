package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.toolwindow;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.actions.popup.root.leve1.leve2.RefreshActionGroup;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.toolwindow.query.CacheRelationQueryService;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.MyBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.setting.MyPluginConfigurable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.Icons.IMAGES_MAPPER_METHOD_SVG;
import static cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.Icons.IMAGES_STATEMENT_SVG;

public final class MyBatisCacheToolWindowFactory implements ToolWindowFactory {
    private static final javax.swing.Icon TOOL_WINDOW_ICON = IconLoader.getIcon("/images/icon.png", MyBatisCacheToolWindowFactory.class);

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        toolWindow.setIcon(TOOL_WINDOW_ICON);
        MyBatisCacheToolWindowPanel panel = new MyBatisCacheToolWindowPanel(project);
        toolWindow.getContentManager().addContent(toolWindow.getContentManager().getFactory().createContent(panel, "", false));
    }

    private static final class MyBatisCacheToolWindowPanel extends SimpleToolWindowPanel {
        private final Project project;
        private final CacheRelationQueryService queryService;
        private final SimpleTree tree = new SimpleTree();
        private final SimpleTree currentTree = new SimpleTree();
        private final JTabbedPane tabs = new JTabbedPane();
        private final JComboBox<CacheRelationQueryService.DataType> dataType = new JComboBox<>(CacheRelationQueryService.DataType.values());
        private final SearchTextField search = new SearchTextField();
        private final CacheTreeCellRenderer allTreeRenderer = new CacheTreeCellRenderer(false);
        private final CacheTreeCellRenderer currentTreeRenderer = new CacheTreeCellRenderer(true);
        private CacheRelationQueryService.CacheScope scope = CacheRelationQueryService.CacheScope.RELATED;
        private CacheRelationQueryService.GroupMode group = CacheRelationQueryService.GroupMode.NONE;
        private PendingNavigation pendingNavigation;
        private TreeViewState pendingTreeState;

        private MyBatisCacheToolWindowPanel(Project project) {
            super(true, true);
            this.project = project;
            this.queryService = new CacheRelationQueryService(project);
            MessageBusConnection connection = project.getMessageBus().connect();
            connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
                @Override
                public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                    if (tabs.getSelectedIndex() == 0 && pendingNavigation == null && event.getNewFile() != null) {
                        TreePath selectedPath = currentTree.getSelectionPath();
                        pendingNavigation = new PendingNavigation(event.getNewFile().getPath(), findSqlId(selectedPath));
                    }
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (!project.isDisposed()) reload();
                    });
                }
            });
            setContent(buildContent());
            reload();
        }

        private JComponent buildContent() {
            JPanel root = new JPanel(new BorderLayout());
            JPanel toolbar = new JPanel(new BorderLayout());
            JPanel searchPanel = new JPanel(new BorderLayout());
            searchPanel.setBorder(JBUI.Borders.empty(2, 4, 4, 4));
            search.setVisible(true);
            search.getTextEditor().getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) {
                    reload();
                }

                public void removeUpdate(DocumentEvent e) {
                    reload();
                }

                public void changedUpdate(DocumentEvent e) {
                    reload();
                }
            });
            searchPanel.add(search, BorderLayout.CENTER);
            searchPanel.setVisible(false);
            DefaultActionGroup searchActions = new DefaultActionGroup();
            searchActions.add(new AnAction(MyBundle.message("km.quick.mybatis.toolwindow.search"), MyBundle.message("km.quick.mybatis.toolwindow.search"), AllIcons.Actions.Find) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    searchPanel.setVisible(!searchPanel.isVisible());
                    if (searchPanel.isVisible()) search.getTextEditor().requestFocusInWindow();
                    root.revalidate();
                    root.repaint();
                }
            });
            searchActions.addSeparator();
            searchActions.add(new AnAction(MyBundle.message("km.quick.mybatis.toolwindow.refresh"), MyBundle.message("km.quick.mybatis.toolwindow.refresh"), AllIcons.Actions.Refresh) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    Object source = e.getInputEvent() == null ? null : e.getInputEvent().getSource();
                    showRefreshPopup(source instanceof JComponent ? (JComponent) source : root);
                }
            });
            ActionToolbar searchToolbar = ActionManager.getInstance().createActionToolbar("KmQuickMybatis.CacheToolWindow.Search", searchActions, true);
            searchToolbar.setMiniMode(true);
            searchToolbar.setTargetComponent(root);
            toolbar.add(searchToolbar.getComponent(), BorderLayout.WEST);

            DefaultActionGroup rightActions = new DefaultActionGroup();
            rightActions.add(new AnAction(MyBundle.message("km.quick.mybatis.toolwindow.settings"), MyBundle.message("km.quick.mybatis.toolwindow.settings"), AllIcons.General.Settings) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, MyPluginConfigurable.class);
                }
            });
            rightActions.addSeparator();
            rightActions.add(new AnAction(MyBundle.message("km.quick.mybatis.toolwindow.group", groupLabel()), MyBundle.message("km.quick.mybatis.toolwindow.group", groupLabel()), AllIcons.Actions.GroupBy) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    group = CacheRelationQueryService.GroupMode.values()[(group.ordinal() + 1) % CacheRelationQueryService.GroupMode.values().length];
                    getTemplatePresentation().setDescription(MyBundle.message("km.quick.mybatis.toolwindow.group", groupLabel()));
                    reload();
                }
            });
            rightActions.add(new AnAction(MyBundle.message("km.quick.mybatis.toolwindow.scope"), scope == CacheRelationQueryService.CacheScope.RELATED ? MyBundle.message("km.quick.mybatis.toolwindow.scope.related") : MyBundle.message("km.quick.mybatis.toolwindow.scope.all"), AllIcons.Actions.ShowAsTree) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    scope = scope == CacheRelationQueryService.CacheScope.ALL ? CacheRelationQueryService.CacheScope.RELATED : CacheRelationQueryService.CacheScope.ALL;
                    getTemplatePresentation().setDescription(scope == CacheRelationQueryService.CacheScope.ALL ? MyBundle.message("km.quick.mybatis.toolwindow.scope.all") : MyBundle.message("km.quick.mybatis.toolwindow.scope.related"));
                    reload();
                }
            });
            rightActions.addSeparator();
            rightActions.add(new AnAction(MyBundle.message("km.quick.mybatis.toolwindow.expand"), MyBundle.message("km.quick.mybatis.toolwindow.expand"), AllIcons.Actions.Expandall) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    expandAll();
                }
            });
            rightActions.add(new AnAction(MyBundle.message("km.quick.mybatis.toolwindow.collapse"), MyBundle.message("km.quick.mybatis.toolwindow.collapse"), AllIcons.Actions.Collapseall) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    collapseAll();
                }
            });
            ActionToolbar rightToolbar = ActionManager.getInstance().createActionToolbar("KmQuickMybatis.CacheToolWindow.Right", rightActions, true);
            rightToolbar.setMiniMode(true);
            rightToolbar.setTargetComponent(root);
            toolbar.add(rightToolbar.getComponent(), BorderLayout.EAST);
            JPanel north = new JPanel(new BorderLayout());
            north.add(toolbar, BorderLayout.NORTH);
            north.add(searchPanel, BorderLayout.CENTER);
            root.add(north, BorderLayout.NORTH);

            JPanel allPanel = new JPanel(new BorderLayout());
            JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            typePanel.add(new JBLabel(MyBundle.message("km.quick.mybatis.toolwindow.type")));
            typePanel.add(dataType);
            dataType.addActionListener(e -> reload());
            allPanel.add(typePanel, BorderLayout.NORTH);
            allPanel.add(new JScrollPane(tree), BorderLayout.CENTER);
            JPanel currentPanel = new JPanel(new BorderLayout());
            currentPanel.add(new JScrollPane(currentTree), BorderLayout.CENTER);
            tree.setCellRenderer(allTreeRenderer);
            currentTree.setCellRenderer(currentTreeRenderer);
            tabs.addTab(MyBundle.message("km.quick.mybatis.toolwindow.tab.current"), currentPanel);
            tabs.addTab(MyBundle.message("km.quick.mybatis.toolwindow.tab.all"), allPanel);
            tabs.addChangeListener(e -> reload());
            root.add(tabs, BorderLayout.CENTER);
            MouseAdapter navigationListener = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) navigate();
                }
            };
            tree.addMouseListener(navigationListener);
            currentTree.addMouseListener(navigationListener);
            root.setBorder(JBUI.Borders.empty(2));
            return root;
        }

        private JButton button(String text, javax.swing.Icon icon) {
            JButton button = new JButton("", icon);
            button.setToolTipText(text);
            button.setFocusable(false);
            button.setMargin(JBUI.emptyInsets());
            return button;
        }

        private static final class CacheTreeCellRenderer extends ColoredTreeCellRenderer {
            private static final javax.swing.Icon JAVA_TARGET_ICON = IconLoader.getIcon(IMAGES_STATEMENT_SVG, MyBatisCacheToolWindowFactory.class);
            private static final javax.swing.Icon XML_TARGET_ICON = IconLoader.getIcon(IMAGES_MAPPER_METHOD_SVG, MyBatisCacheToolWindowFactory.class);
            private static final javax.swing.Icon SQL_ID_ICON = IconUtil.scale(
                    IconLoader.getIcon("/images/icon-40.png", MyBatisCacheToolWindowFactory.class), 0.4f);
            private static final SimpleTextAttributes SEARCH_MATCH_ATTRIBUTES =
                    new SimpleTextAttributes(SimpleTextAttributes.STYLE_SEARCH_MATCH, null);
            private final boolean currentView;
            private String searchText = "";

            private CacheTreeCellRenderer(boolean currentView) {
                this.currentView = currentView;
            }

            private void setSearchText(String searchText) {
                this.searchText = searchText == null ? "" : searchText;
            }

            @Override
            public void customizeCellRenderer(javax.swing.JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                if (value instanceof CacheTreeNode node) {
                    setIcon(iconFor(tree, node.getKey(), leaf));
                    appendSearchHighlighted(node.toString());
                } else {
                    append(String.valueOf(value), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }
            }

            private void appendSearchHighlighted(String text) {
                if (searchText.isEmpty()) {
                    append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    return;
                }
                String lowerText = text.toLowerCase(Locale.ROOT);
                String lowerSearchText = searchText.toLowerCase(Locale.ROOT);
                int from = 0;
                int match;
                while ((match = lowerText.indexOf(lowerSearchText, from)) >= 0) {
                    if (match > from) append(text.substring(from, match), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    append(text.substring(match, match + searchText.length()), SEARCH_MATCH_ATTRIBUTES);
                    from = match + searchText.length();
                }
                if (from < text.length()) append(text.substring(from), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }

            private javax.swing.Icon iconFor(javax.swing.JTree tree, String key, boolean leaf) {
                if (key.startsWith("java-file:interface:")) return AllIcons.Nodes.Interface;
                if (key.startsWith("java-file:class:")) return AllIcons.Nodes.Class;
                if (key.startsWith("java-file:")) return AllIcons.Nodes.Class;
                if (key.startsWith("xml-variant:")) return AllIcons.FileTypes.Xml;
                if (key.startsWith("java-call:") || key.startsWith("java:")) {
                    return currentView ? XML_TARGET_ICON : JAVA_TARGET_ICON;
                }
                if (key.startsWith("xml:")) return currentView ? JAVA_TARGET_ICON : XML_TARGET_ICON;
                if (key.startsWith("current-sql:")) {
                    Object root = tree.getModel().getRoot();
                    if (root instanceof CacheTreeNode currentRoot) {
                        String rootKey = currentRoot.getKey().toLowerCase();
                        if (rootKey.endsWith(".java")) return XML_TARGET_ICON;
                        if (rootKey.endsWith(".xml")) return JAVA_TARGET_ICON;
                    }
                    return AllIcons.Nodes.Tag;
                }
                if (key.startsWith("sql:")) return currentView ? AllIcons.Nodes.Tag : SQL_ID_ICON;
                if (key.startsWith("directory:") || key.startsWith("module:")) return AllIcons.Nodes.Folder;
                if (key.startsWith("current:")) {
                    if (key.toLowerCase().endsWith(".java")) return AllIcons.FileTypes.Java;
                    if (key.toLowerCase().endsWith(".xml")) return AllIcons.FileTypes.Xml;
                }
                return AllIcons.Nodes.Folder;
            }
        }

        private String groupLabel() {
            return switch (group) {
                case NONE -> MyBundle.message("km.quick.mybatis.toolwindow.group.none");
                case DIRECTORY -> MyBundle.message("km.quick.mybatis.toolwindow.group.directory");
                case MODULE -> MyBundle.message("km.quick.mybatis.toolwindow.group.module");
            };
        }

        private void showRefreshPopup(JComponent component) {
            JPopupMenu popup = ActionManager.getInstance().createActionPopupMenu("KmQuickMybatis.CacheToolWindow", new RefreshActionGroup()).getComponent();
            popup.show(component, 0, component.getHeight());
        }

        private void reload() {
            allTreeRenderer.setSearchText(search.getText());
            currentTreeRenderer.setSearchText(search.getText());
            boolean current = tabs.getSelectedIndex() == 0;
            CacheRelationQueryService.DataType type = current ? CacheRelationQueryService.DataType.ALL : (CacheRelationQueryService.DataType) dataType.getSelectedItem();
            DefaultMutableTreeNode root = queryService.buildTree(current ? CacheRelationQueryService.Tab.CURRENT : CacheRelationQueryService.Tab.ALL, type == null ? CacheRelationQueryService.DataType.ALL : type, scope, group, search.getText());
            treeForView().setModel(new DefaultTreeModel(root));
            restoreTreeState(treeForView());
            restorePendingNavigation(current);
        }

        private void navigate() {
            SimpleTree selectedTree = treeForView();
            TreePath selectedPath = selectedTree.getSelectionPath();
            Object selected = selectedPath == null ? null : selectedPath.getLastPathComponent();
            if (!(selected instanceof CacheTreeNode node) || !node.canNavigate()) return;
            pendingTreeState = captureTreeState(selectedTree);
            if (tabs.getSelectedIndex() == 0) {
                pendingNavigation = new PendingNavigation(node.getFilePath(), findSqlId(selectedPath));
            }
            com.intellij.openapi.vfs.VirtualFile file = LocalFileSystem.getInstance().findFileByPath(node.getFilePath());
            if (file != null) new OpenFileDescriptor(project, file, Math.max(0, node.getOffset())).navigate(true);
        }

        private void restorePendingNavigation(boolean currentTab) {
            if (!currentTab || pendingNavigation == null || !pendingNavigation.filePath.equals(queryService.currentPathForNavigation())) return;
            CacheTreeNode target = findNavigationNode((CacheTreeNode) currentTree.getModel().getRoot(), pendingNavigation.sqlId);
            if (target == null) return;
            TreePath targetPath = new TreePath(target.getPath());
            TreePath parentPath = new TreePath(targetPath.getPathComponent(0));
            for (int i = 1; i < targetPath.getPathCount(); i++) {
                parentPath = parentPath.pathByAddingChild(targetPath.getPathComponent(i));
                currentTree.expandPath(parentPath);
            }
            currentTree.setSelectionPath(targetPath);
            currentTree.scrollPathToVisible(targetPath);
            pendingNavigation = null;
        }

        private TreeViewState captureTreeState(SimpleTree target) {
            List<List<String>> expandedPaths = new ArrayList<>();
            for (int row = 0; row < target.getRowCount(); row++) {
                TreePath path = target.getPathForRow(row);
                if (path != null && target.isExpanded(path)) expandedPaths.add(nodeKeys(path));
            }
            TreePath selectedPath = target.getSelectionPath();
            return new TreeViewState(expandedPaths, selectedPath == null ? List.of() : nodeKeys(selectedPath));
        }

        private void restoreTreeState(SimpleTree target) {
            if (pendingTreeState == null) return;
            for (List<String> keys : pendingTreeState.expandedPaths) {
                TreePath path = findPath((DefaultMutableTreeNode) target.getModel().getRoot(), keys);
                if (path != null) target.expandPath(path);
            }
            if (!pendingTreeState.selectedPath.isEmpty()) {
                TreePath selectedPath = findPath((DefaultMutableTreeNode) target.getModel().getRoot(), pendingTreeState.selectedPath);
                if (selectedPath != null) {
                    target.setSelectionPath(selectedPath);
                    target.scrollPathToVisible(selectedPath);
                }
            }
            pendingTreeState = null;
        }

        private List<String> nodeKeys(TreePath path) {
            List<String> keys = new ArrayList<>();
            for (Object component : path.getPath()) {
                keys.add(component instanceof CacheTreeNode node ? node.getKey() : String.valueOf(component));
            }
            return keys;
        }

        private TreePath findPath(DefaultMutableTreeNode root, List<String> keys) {
            if (keys.isEmpty() || !keys.get(0).equals(((CacheTreeNode) root).getKey())) return null;
            TreePath path = new TreePath(root);
            DefaultMutableTreeNode current = root;
            for (int i = 1; i < keys.size(); i++) {
                DefaultMutableTreeNode next = null;
                for (int childIndex = 0; childIndex < current.getChildCount(); childIndex++) {
                    DefaultMutableTreeNode child = (DefaultMutableTreeNode) current.getChildAt(childIndex);
                    if (child instanceof CacheTreeNode node && keys.get(i).equals(node.getKey())) {
                        next = child;
                        break;
                    }
                }
                if (next == null) return null;
                current = next;
                path = path.pathByAddingChild(current);
            }
            return path;
        }

        private CacheTreeNode findNavigationNode(CacheTreeNode node, String sqlId) {
            if (sqlId != null && ("current-sql:" + sqlId).equals(node.getKey())) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                CacheTreeNode target = findNavigationNode((CacheTreeNode) node.getChildAt(i), sqlId);
                if (target != null) return target;
            }
            if (sqlId == null && node.getKey().startsWith("current-sql:")) return node;
            if (pendingNavigation != null && node.getKey().equals("current:" + pendingNavigation.filePath)) return node;
            return null;
        }

        private String findSqlId(TreePath path) {
            if (path == null) return null;
            for (Object component : path.getPath()) {
                if (component instanceof CacheTreeNode node && node.getKey().startsWith("current-sql:")) {
                    return node.getKey().substring("current-sql:".length());
                }
            }
            return null;
        }

        private static final class PendingNavigation {
            private final String filePath;
            private final String sqlId;

            private PendingNavigation(String filePath, String sqlId) {
                this.filePath = filePath;
                this.sqlId = sqlId;
            }
        }

        private static final class TreeViewState {
            private final List<List<String>> expandedPaths;
            private final List<String> selectedPath;

            private TreeViewState(List<List<String>> expandedPaths, List<String> selectedPath) {
                this.expandedPaths = expandedPaths;
                this.selectedPath = selectedPath;
            }
        }

        private SimpleTree treeForView() {
            return tabs.getSelectedIndex() == 0 ? currentTree : tree;
        }

        private void expandAll() {
            SimpleTree target = treeForView();
            expandNode((TreeNode) target.getModel().getRoot(), new TreePath(target.getModel().getRoot()), target);
        }

        private void expandNode(TreeNode node, TreePath path, SimpleTree target) {
            target.expandPath(path);
            for (int i = 0; i < node.getChildCount(); i++) {
                TreeNode child = node.getChildAt(i);
                expandNode(child, path.pathByAddingChild(child), target);
            }
        }

        private void collapseAll() {
            SimpleTree target = treeForView();
            for (int i = target.getRowCount() - 1; i > 0; i--) target.collapseRow(i);
        }
    }
}
