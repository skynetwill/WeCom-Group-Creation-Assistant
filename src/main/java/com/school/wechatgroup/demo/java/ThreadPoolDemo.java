package com.school.wechatgroup.demo.java;

import java.util.concurrent.*;

/**
 * 线程池 + 并发编程 — 面试必考
 *
 * 本项目实际使用:
 *   @Async → AuditService 异步写审计日志
 *   ThreadLocal → SecurityContextHolder
 *   volatile → WeChatTokenManager.accessToken（旧版）
 *   ConcurrentHashMap → SessionRegistry（旧版）
 *   @Scheduled → 定时刷新 Token + 清理限流缓存
 *
 * 面试必问: 线程池参数
 *   new ThreadPoolExecutor(
 *       corePoolSize,      // 核心线程数（常驻）
 *       maxPoolSize,       // 最大线程数
 *       keepAliveTime,     // 空闲线程存活时间
 *       unit,              // 时间单位
 *       workQueue,         // 任务队列
 *                          //   SynchronousQueue: 不排队，直接创建线程
 *                          //   LinkedBlockingQueue: 无界队列(危险！OOM)
 *                          //   ArrayBlockingQueue: 有界队列(推荐)
 *       handler            // 拒绝策略
 *                          //   AbortPolicy: 抛异常(默认)
 *                          //   CallerRunsPolicy: 调用者线程执行
 *                          //   DiscardPolicy: 静默丢弃
 *   )
 */
public class ThreadPoolDemo {

    public static void main(String[] args) throws Exception {
        // 业务线程池（对标 @Async 自定义线程池）
        ThreadPoolExecutor bizPool = new ThreadPoolExecutor(
                2, 4, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy());

        // 提交 5 个任务
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            bizPool.submit(() -> {
                System.out.printf("[线程池] 任务%d 执行中 (线程: %s)%n",
                        taskId, Thread.currentThread().getName());
                try { Thread.sleep(500); }
                catch (InterruptedException ignored) {}
            });
        }

        bizPool.shutdown();
        bizPool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("活跃线程: " + bizPool.getActiveCount());
        System.out.println("完成任务: " + bizPool.getCompletedTaskCount());

        // —— 不走线程池的场景: @Async —
        // AuditService.logLoginSuccess() 用 @Async + Spring 默认线程池
        // 适合: 日志/通知/邮件等非核心路径
        // 不适合: 需要事务/需要顺序/核心业务

        // —— ThreadLocal 场景: SecurityContext —
        // SecurityContextHolder 用 ThreadLocal 存当前请求用户
        // 每个请求独立，互不影响
    }
}
