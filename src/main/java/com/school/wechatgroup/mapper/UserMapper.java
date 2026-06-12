package com.school.wechatgroup.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.wechatgroup.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * MyBatis-Plus UserMapper（与 JPA AppUserRepository 并存）
 * BaseMapper 自动提供 CRUD：insert/deleteById/updateById/selectById/selectList
 */
@Mapper
public interface UserMapper extends BaseMapper<AppUser> {

    /** 复杂联表查询 — MyBatis 注解版 */
    @Select("""
        SELECT u.username, u.nickname, u.role, u.last_login_time,
               u.login_count, u.failed_attempts, u.must_change_password
        FROM t_app_user u
        WHERE u.enabled = true
        ORDER BY u.last_login_time DESC
        LIMIT #{limit}
    """)
    List<Map<String, Object>> selectActiveUsers(int limit);

    /** 统计角色分布 */
    @Select("SELECT role, COUNT(*) as cnt FROM t_app_user WHERE enabled = true GROUP BY role")
    List<Map<String, Object>> countByRole();

    /** 分页查询（配合 MyBatis-Plus 分页插件） */
    @Select("SELECT * FROM t_app_user WHERE username LIKE CONCAT('%', #{keyword}, '%')")
    List<AppUser> searchByUsername(String keyword);
}
