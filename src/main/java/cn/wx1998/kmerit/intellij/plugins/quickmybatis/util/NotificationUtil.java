package cn.wx1998.kmerit.intellij.plugins.quickmybatis.util;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.MyBatisCacheManager;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.MyBatisCacheManagerFactory;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 官方文档的通知工具类：<a href="https://plugins.jetbrains.com/docs/intellij/notifications.html">Notification</a>
 */
public class NotificationUtil {

    private static final String DEFAULT_NOTIFICATION_GROUP_ID = "cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.NotificationUtil";

    /**
     * 通用通知显示方法（全自定义）
     *
     * @param project              当前项目（不可为null）
     * @param notificationKey      通知唯一标识（用于区分不同通知的"不再建议"状态，不可为null/空）
     * @param title                通知标题（不可为null/空）
     * @param content              通知内容（不可为null/空）
     * @param leftBtnText          左侧按钮文本（不可为null/空）
     * @param leftBtnAction        左侧按钮点击逻辑（不可为null）
     * @param rightBtnText         右侧按钮文本（不可为null/空）
     * @param rightBtnAction       右侧按钮点击逻辑（不可为null）
     * @param enableDoNotShowAgain 是否启用"不再建议"持久化（true=启用，false=不启用）
     */
    public static void showCustomNotification(@NotNull Project project, @NotNull String notificationKey, @NotNull String title, @NotNull String content, @NotNull String leftBtnText, @NotNull NotificationActionCallback leftBtnAction, @NotNull String rightBtnText, @NotNull NotificationActionCallback rightBtnAction, boolean enableDoNotShowAgain) {
        // 1. 参数校验（避免空指针）
        Objects.requireNonNull(project, "Project cannot be null");
        Objects.requireNonNull(notificationKey, "NotificationKey cannot be null or empty");
        Objects.requireNonNull(title, "Notification title cannot be null");
        Objects.requireNonNull(content, "Notification content cannot be null");
        Objects.requireNonNull(leftBtnText, "Left button text cannot be null");
        Objects.requireNonNull(leftBtnAction, "Left button action cannot be null");
        Objects.requireNonNull(rightBtnText, "Right button text cannot be null");
        Objects.requireNonNull(rightBtnAction, "Right button action cannot be null");


        // 2. 若启用"不再建议"，先检查状态
        if (enableDoNotShowAgain) {
            NotificationSettings settings = NotificationSettings.getInstance(project);
            if (settings.isDoNotShowAgain(notificationKey)) {
                return;
            }
        }

        // 3. 创建通知（使用通用通知组）
        Notification notification = NotificationGroupManager.getInstance().getNotificationGroup(DEFAULT_NOTIFICATION_GROUP_ID).createNotification(title, content, NotificationType.INFORMATION);

        // 4. 绑定左侧按钮（外部传入逻辑）
        notification.addAction(new NotificationAction(leftBtnText) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                // 执行外部传入的点击逻辑
                leftBtnAction.execute(e.getProject(), notification);
                // 执行后自动关闭通知（可根据需求改为由外部控制）
                notification.expire();
            }
        });

        // 5. 绑定右侧按钮（外部传入逻辑）
        notification.addAction(new NotificationAction(rightBtnText) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                // 若启用"不再建议"，点击右侧按钮时保存状态
                if (enableDoNotShowAgain) {
                    project.getService(NotificationSettings.class).setDoNotShowAgain(notificationKey, true);
                }
                // 执行外部传入的点击逻辑
                rightBtnAction.execute(e.getProject(), notification);
                // 执行后自动关闭通知
                notification.expire();
            }
        });

        // 6. 显示通知
        notification.notify(project);
    }


    /**
     * 简化方法：快速显示"MyBatis缓存刷新"通知（兼容原有逻辑，支持独立控制）
     *
     * @param project          当前项目
     * @param notificationKey  通知唯一标识（例如："cache.refresh.tip1"、"cache.refresh.tip2"）
     * @param title            通知标题
     * @param content          通知内容
     * @param refreshBtnText   刷新按钮文本
     * @param doNotShowBtnText 不再建议按钮文本
     */
    public static void showCacheRefreshNotification(@NotNull Project project, @NotNull String notificationKey, @NotNull String title, @NotNull String content, @NotNull String refreshBtnText, @NotNull String doNotShowBtnText) {
        showCustomNotification(project, notificationKey, title, content, refreshBtnText,
                // 左侧按钮：缓存刷新逻辑
                (currentProject, notification) -> {
                    if (currentProject != null) {
                        MyBatisCacheManager cacheManager = MyBatisCacheManagerFactory.getRecommendedParser(project);
                        cacheManager.performFullCacheRefresh(0);
                    }
                }, doNotShowBtnText,
                // 右侧按钮：空逻辑
                (currentProject, notification) -> {
                }, true // 启用不再建议持久化
        );
    }

    /**
     * 函数式接口：通知按钮点击回调
     */
    @FunctionalInterface
    public interface NotificationActionCallback {
        void execute(@Nullable Project project, @NotNull Notification notification);
    }

    /**
     * 持久化配置服务（按 NotificationKey 存储不再建议状态）
     */
    @State(name = "kmQuickMybatisNotificationSettingsState", storages = @Storage("km-quick-mybatis-notification.xml"))
    public static class NotificationSettings implements PersistentStateComponent<NotificationSettings.State> {

        /**
         * 状态存储类：用 Map 按 NotificationKey 存储不再建议状态
         */
        public static class State {
            // key: NotificationKey, value: 是否不再显示
            public Map<String, Boolean> doNotShowAgainMap = new HashMap<>();
        }

        private State state = new State();

        public static NotificationSettings getInstance(@NotNull Project project) {
            Objects.requireNonNull(project, "Project cannot be null");
            return project.getService(NotificationSettings.class);
        }

        /**
         * 无参构造函数（IDE 服务必须，用于自动实例化）
         */
        public NotificationSettings() {
        }

        @Nullable
        @Override
        public State getState() {
            return state;
        }

        /**
         * 加载持久化状态（官方要求实现）
         */
        @Override
        public void loadState(@NotNull State state) {
            this.state = state;
            // 兼容旧数据：若 Map 为 null 则初始化
            if (this.state.doNotShowAgainMap == null) {
                this.state.doNotShowAgainMap = new HashMap<>();
            }
        }

        /**
         * 是否不再显示通知
         */
        public boolean isDoNotShowAgain(@NotNull String notificationKey) {
            // 若 key 不存在，默认返回 false（显示通知）
            return state.doNotShowAgainMap.getOrDefault(notificationKey, false);
        }

        /**
         * 按 NotificationKey 设置不再显示状态
         */
        public void setDoNotShowAgain(@NotNull String notificationKey, boolean doNotShowAgain) {
            state.doNotShowAgainMap.put(notificationKey, doNotShowAgain);
        }

        /**
         * 按 NotificationKey 重置不再显示状态（单个通知）
         */
        public void resetDoNotShowAgain(@NotNull String notificationKey) {
            state.doNotShowAgainMap.remove(notificationKey);
        }

        /**
         * 重置所有通知的不再显示状态
         */
        public void resetAllDoNotShowAgain() {
            state.doNotShowAgainMap.clear();
        }
    }
}