package com.school.wechatgroup.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring 事务传播行为演示
 *
 * 面试必问 7 种传播级别:
 *   REQUIRED       — 有事务就加入，没有就新建（默认）
 *   REQUIRES_NEW   — 总是新建事务，挂起当前事务
 *   NESTED         — 嵌套事务（保存点回滚）
 *   SUPPORTS       — 有事务就加入，没有就非事务运行
 *   NOT_SUPPORTED  — 总是非事务运行，挂起当前事务
 *   MANDATORY      — 必须在事务中，否则抛异常
 *   NEVER          — 必须在非事务中，否则抛异常
 *
 * 场景演示：
 *   建群操作(A) + 记录日志(B)
 *   如果用 REQUIRED:  B 失败 → A 也回滚 → 群建了但没记录
 *   如果用 REQUIRES_NEW: B 失败 → B 单独回滚 → A 的群还是建了，日志表留下失败记录
 */
@Service
public class TransactionDemo {

    private static final Logger log = LoggerFactory.getLogger(TransactionDemo.class);

    /**
     * 外层事务 — 默认 REQUIRED
     */
    @Transactional
    public void outerTransaction() {
        log.info("[事务演示] 外层事务开始（REQUIRED）");
        // 模拟数据库操作
        log.info("[事务演示] 外层事务 — 写入主表数据");

        try {
            // 调用内层事务
            innerTransactionRequiresNew();
        } catch (RuntimeException e) {
            log.info("[事务演示] 内层事务异常被捕获，外层事务不受影响");
        }

        log.info("[事务演示] 外层事务提交");
    }

    /**
     * 内层事务 — REQUIRES_NEW：独立提交，不依赖外层
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void innerTransactionRequiresNew() {
        log.info("[事务演示]   内层事务开始（REQUIRES_NEW）— 挂起外层事务");
        log.info("[事务演示]   内层事务 — 写入审计日志");
        // 模拟异常：即使内层抛异常，外层也不回滚
        throw new RuntimeException("模拟内层事务失败 — 仅回滚审计日志，主数据不受影响");
    }
}
