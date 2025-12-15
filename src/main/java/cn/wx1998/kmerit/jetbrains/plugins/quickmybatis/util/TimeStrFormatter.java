package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util;

public class TimeStrFormatter {

    /**
     * 格式化毫秒数为易读的时间字符串
     *
     * @param ms 要格式化的毫秒数
     * @return 格式化后的字符串（如：500毫秒、1分10秒10毫秒、2小时3分4秒5毫秒）
     */
    public static String format(long ms) {
        // 处理负数情况
        if (ms < 0) {
            return "0毫秒";
        }

        // 定义时间单位常量
        final long MS_PER_SECOND = 1000;
        final long MS_PER_MINUTE = 60 * MS_PER_SECOND;
        final long MS_PER_HOUR = 60 * MS_PER_MINUTE;
        final long MS_PER_DAY = 24 * MS_PER_HOUR;

        // 计算各时间单位的数值
        long days = ms / MS_PER_DAY;
        long hours = (ms % MS_PER_DAY) / MS_PER_HOUR;
        long minutes = (ms % MS_PER_HOUR) / MS_PER_MINUTE;
        long seconds = (ms % MS_PER_MINUTE) / MS_PER_SECOND;
        long remainingMs = ms % MS_PER_SECOND;

        // 拼接结果字符串
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("天");
        }
        if (hours > 0) {
            sb.append(hours).append("小时");
        }
        if (minutes > 0) {
            sb.append(minutes).append("分");
        }
        if (seconds > 0) {
            sb.append(seconds).append("秒");
        }
        // 始终保留毫秒部分（即使为0，也会显示0毫秒）
        sb.append(remainingMs).append("毫秒");

        return sb.toString();
    }
}
