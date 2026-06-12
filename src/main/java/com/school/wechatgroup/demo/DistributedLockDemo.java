package com.school.wechatgroup.demo;

/**
 * 分布式锁 面试必考
 *
 * 为什么需要分布式锁？
 *   单机: synchronized / ReentrantLock 就够了
 *   分布式: 3 个实例都在抢同一个"建群 token 刷新"任务
 *          synchronized 只能锁当前 JVM，其他实例不受影响
 *          必须用一个所有实例都能看到的"第三方"来实现锁
 *
 * 本项目实现:
 *
 *   Redis SETNX 锁（WeChatTokenManager.java）:
 *     SETNX wechat:token:lock "1" EX 30
 *     → 只有一个实例能 SET 成功（获取锁）
 *     → 其他实例 SET 失败（等待）
 *     → 30 秒后锁自动过期（防止死锁）
 *
 *   Redisson 企业级增强（本项目未依赖 Redisson，仅演示概念）:
 *     1. 看门狗 (Watchdog): 自动续期
 *        - 业务执行超过 30 秒 → 看门狗自动续期，不会锁过期
 *        - 业务完成后 → 看门狗停止
 *     2. 可重入锁: 同一线程可多次获取
 *     3. 公平锁: 排队等待
 *     4. 读写锁: 读共享，写独占
 *     5. 红锁 (RedLock): 多个 Redis 节点投票
 *
 * 面试官问: Redis 锁挂了怎么办？
 *   答: RedLock 算法 — N 个独立 Redis 节点，过半投票成功才算获取锁
 *      （实际生产很少用，太重了。ZooKeeper 更适合做分布式锁）
 */
public class DistributedLockDemo {
    // 纯文档类 — 具体实现在 WeChatTokenManager.refreshAccessToken()
}
