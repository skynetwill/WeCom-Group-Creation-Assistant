package com.school.wechatgroup.demo.java;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * CompletableFuture 异步编排 — 面试必考
 *
 * 场景: 创建群聊需要同时做 3 件事:
 *   1. 调用企业微信 API（2秒）
 *   2. 写数据库（0.5秒）
 *   3. 发审计日志（0.3秒）
 *
 * 同步执行: 2 + 0.5 + 0.3 = 2.8秒
 * 异步并行: max(2, 0.5, 0.3) = 2秒  ← 快了 30%
 *
 * 常用 API:
 *   supplyAsync     — 异步执行有返回值
 *   thenApply       — 转换结果
 *   thenCompose     — 链式异步（前一个完成后触发下一个）
 *   thenCombine     — 合并两个异步结果
 *   allOf / anyOf   — 等待全部/任一完成
 *   exceptionally   — 异常处理
 */
public class CompletableFutureDemo {

    public static void main(String[] args) {
        // 模拟创建群聊的三个异步任务
        CompletableFuture<String> apiCall = CompletableFuture.supplyAsync(() -> {
            sleep(2000);
            return "chat_id_12345";
        });

        CompletableFuture<Boolean> dbSave = CompletableFuture.supplyAsync(() -> {
            sleep(500);
            return true;
        });

        CompletableFuture<Boolean> auditLog = CompletableFuture.supplyAsync(() -> {
            sleep(300);
            return true;
        });

        // 等全部完成
        CompletableFuture.allOf(apiCall, dbSave, auditLog).join();

        System.out.println("所有异步任务完成");

        // thenCombine: 合并 API 结果和 DB 结果
        CompletableFuture<String> result = apiCall
                .thenCombine(dbSave, (chatId, dbOk) ->
                        dbOk ? "群创建成功: " + chatId : "群创建失败")
                .exceptionally(ex -> "系统异常: " + ex.getMessage());

        System.out.println(result.join());
    }

    private static void sleep(long ms) {
        try { TimeUnit.MILLISECONDS.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
