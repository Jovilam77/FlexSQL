package cn.vonce.sql.java.datasource;

import cn.vonce.sql.uitls.StringUtil;

import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * 事务超时管理器
 * 负责调度和管理事务超时任务
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2026/5/18
 */
public class TransactionTimeoutManager {

    private static final Logger logger = Logger.getLogger(TransactionTimeoutManager.class.getName());
    
    /**
     * 定时任务调度器
     */
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4, r -> {
        Thread t = new Thread(r, "transaction-timeout-scheduler");
        t.setDaemon(true);
        return t;
    });
    
    /**
     * 存储正在运行的超时任务
     * key: xid, value: ScheduledFuture
     */
    private static final Map<String, ScheduledFuture<?>> timeoutTasks = new ConcurrentHashMap<>();

    /**
     * 调度事务超时任务
     *
     * @param xid     事务ID
     * @param timeout 超时时间（秒）
     */
    public static void scheduleTimeout(String xid, int timeout) {
        if (timeout <= 0 || StringUtil.isBlank(xid)) {
            return;
        }
        
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            // 检查事务是否仍在进行中
            String currentXid = TransactionalContextHolder.getXid();
            if (StringUtil.isNotBlank(currentXid) && currentXid.equals(xid)) {
                logger.warning("Transaction timeout: xid=" + xid + ", timeout=" + timeout + "s");
                // 强制回滚事务
                try {
                    ConnectionContextHolder.commitOrRollback(false);
                } catch (Exception e) {
                    logger.severe("Failed to rollback timeout transaction: " + e.getMessage());
                }
            }
            // 移除任务
            timeoutTasks.remove(xid);
        }, timeout, TimeUnit.SECONDS);
        
        timeoutTasks.put(xid, future);
        logger.fine("Scheduled timeout task: xid=" + xid + ", timeout=" + timeout + "s");
    }

    /**
     * 取消事务超时任务
     *
     * @param xid 事务ID
     */
    public static void cancelTimeout(String xid) {
        if (StringUtil.isBlank(xid)) {
            return;
        }
        
        ScheduledFuture<?> future = timeoutTasks.remove(xid);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            logger.fine("Cancelled timeout task: xid=" + xid);
        }
    }

    /**
     * 检查是否存在超时任务
     *
     * @param xid 事务ID
     * @return 是否存在
     */
    public static boolean hasTimeoutTask(String xid) {
        return timeoutTasks.containsKey(xid);
    }

    /**
     * 关闭调度器
     */
    public static void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}