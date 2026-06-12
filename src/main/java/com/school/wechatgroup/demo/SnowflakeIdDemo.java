package com.school.wechatgroup.demo;

/**
 * 分布式 ID 生成 — 雪花算法 (Snowflake)
 *
 * 为什么不用数据库自增 ID？
 *   单库: AUTO_INCREMENT 够用
 *   分库分表后: 每个库独立自增 → ID 冲突！
 *      库1: id=1,2,3...
 *      库2: id=1,2,3...  ← 冲突！
 *      需要全局唯一 ID
 *
 * Snowflake 算法 (Twitter 开源):
 *   64 位长整型 = 1位未用 + 41位时间戳 + 10位机器ID + 12位序列号
 *
 *   ┌─┬──────────────────────┬───────────┬────────────┐
 *   │0│    41位 毫秒时间戳     │10位 机器ID │12位 序列号  │
 *   └─┴──────────────────────┴───────────┴────────────┘
 *
 *   特点:
 *     - 趋势递增（不是严格递增，因为多机器并发）
 *     - 不依赖数据库，纯内存计算
 *     - 每秒可生成 409.6 万个 ID
 *
 * 使用方式:
 *   @Bean
 *   public SnowflakeIdGenerator idGenerator() {
 *       return new SnowflakeIdGenerator(workerId, datacenterId);
 *   }
 *   long id = idGenerator.nextId();
 */
public class SnowflakeIdDemo {

    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdDemo(long workerId, long datacenterId) {
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时钟回拨，拒绝生成 ID");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & 0xFFF; // 4095 取模
            if (sequence == 0) {
                // 当前毫秒序列号用完，等下一毫秒
                while (timestamp <= lastTimestamp) {
                    timestamp = System.currentTimeMillis();
                }
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - 1288834974657L) << 22)
                | (datacenterId << 17)
                | (workerId << 12)
                | sequence;
    }

    // 演示用 main
    public static void main(String[] args) {
        SnowflakeIdDemo gen = new SnowflakeIdDemo(1, 1);
        for (int i = 0; i < 5; i++) {
            System.out.println("Snowflake ID: " + gen.nextId());
        }
    }
}
