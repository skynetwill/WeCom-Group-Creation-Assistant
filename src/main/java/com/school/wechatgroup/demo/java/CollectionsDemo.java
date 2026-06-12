package com.school.wechatgroup.demo.java;

import java.util.*;
import java.util.concurrent.*;

/**
 * Java 集合框架 — 面试高频
 *
 * 本项目实际使用:
 *   HashMap → AuthController.loginAttempts（限流计数器）
 *   ConcurrentHashMap → SessionRegistryConfig（会话管理）
 *   LinkedHashMap → HttpSecurity.authRules（保持插入顺序的安全规则）
 *   Deque → SessionRegistry 旧版本（双向队列）
 *   CopyOnWriteArrayList → （可替换为并发安全列表）
 *
 * 面试必问: HashMap 原理
 * ┌──────────────────────────────────────────────────────┐
 * │ HashMap = 数组 + 链表 + 红黑树（JDK 8+）              │
 * │   put(k, v):                                       │
 * │     1. 计算 hash = k.hashCode() ^ (h >>> 16)       │
 * │     2. 求索引 index = (n-1) & hash                  │
 * │     3. 数组[index] 为空 → 直接放入                  │
 * │     4. 数组[index] 冲突 → 链表追加                   │
 * │     5. 链表长度 > 8 → 转为红黑树（O(n) → O(log n)） │
 * │   get(k):  同样的 hash → 找索引 → 遍历链表/树匹配    │
 * └──────────────────────────────────────────────────────┘
 */
public class CollectionsDemo {

    public static void main(String[] args) {
        // HashMap: O(1) 查找，无序
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put("c", "3");
        hashMap.put("a", "1");
        hashMap.put("b", "2");
        System.out.println("HashMap(无序):    " + hashMap.keySet());

        // LinkedHashMap: O(1) 查找，保持插入顺序
        Map<String, String> linkedMap = new LinkedHashMap<>();
        linkedMap.put("c", "3");
        linkedMap.put("a", "1");
        linkedMap.put("b", "2");
        System.out.println("LinkedHashMap:    " + linkedMap.keySet());

        // TreeMap: O(log n) 查找，按 key 排序
        Map<String, String> treeMap = new TreeMap<>();
        treeMap.put("c", "3");
        treeMap.put("a", "1");
        treeMap.put("b", "2");
        System.out.println("TreeMap(排序):    " + treeMap.keySet());

        // ConcurrentHashMap: 线程安全，分段锁（JDK 7）→ CAS + synchronized（JDK 8）
        ConcurrentHashMap<String, Integer> chm = new ConcurrentHashMap<>();
        chm.put("a", 1);
        System.out.println("ConcurrentHashMap: " + chm.get("a"));

        // ArrayList vs LinkedList
        // ArrayList: 数组，随机访问 O(1)，中间插入 O(n)
        // LinkedList: 双向链表，随机访问 O(n)，头尾操作 O(1)
        List<String> arrayList = new ArrayList<>();
        List<String> linkedList = new LinkedList<>();
        System.out.println("ArrayList适合: 随机读取");
        System.out.println("LinkedList适合: 队列(offer/poll)");
    }
}
