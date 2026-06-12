package com.school.wechatgroup.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * JVM 指标监控 — Micrometer + Prometheus
 *
 * 暴露端点: GET /actuator/prometheus
 * 采集指标: JVM 内存/GC/线程/类加载/CPU
 *
 * 启动参数（生产环境建议）:
 *   -XX:+UseG1GC                         G1 垃圾回收器
 *   -XX:MaxGCPauseMillis=200             最大 GC 暂停 200ms
 *   -Xlog:gc*:file=logs/gc.log:time,level,tags  GC 日志
 *   -XX:+HeapDumpOnOutOfMemoryError      OOM 时自动 dump
 *   -XX:HeapDumpPath=logs/heapdump.hprof  dump 文件路径
 */
@Configuration
public class JvmMetricsConfig {

    private final MeterRegistry registry;

    public JvmMetricsConfig(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void bindJvmMetrics() {
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
    }
}
