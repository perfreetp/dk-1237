-- 数据权限服务数据库初始化脚本
-- 数据库名称: data_permission

CREATE DATABASE IF NOT EXISTS data_permission DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE data_permission;

-- 1. 组织架构表
CREATE TABLE IF NOT EXISTS sys_organization (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    org_code VARCHAR(50) NOT NULL COMMENT '组织编码',
    org_name VARCHAR(200) NOT NULL COMMENT '组织名称',
    org_type ENUM('HEADQUARTER','COMPANY','REGION','DEPT','PROJECT') NOT NULL COMMENT '组织类型',
    parent_id BIGINT COMMENT '父组织ID',
    hierarchy_level INT NOT NULL DEFAULT 1 COMMENT '层级深度',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    status TINYINT DEFAULT 1 COMMENT '状态：0禁用 1启用',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_parent_id (parent_id),
    INDEX idx_org_type (org_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织架构表';

-- 2. 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(100) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(200) NOT NULL COMMENT '密码',
    real_name VARCHAR(100) COMMENT '真实姓名',
    email VARCHAR(200) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    org_id BIGINT NOT NULL COMMENT '所属组织ID',
    post_id BIGINT COMMENT '岗位ID',
    status TINYINT DEFAULT 1 COMMENT '状态：0离职 1在职',
    leave_date DATETIME COMMENT '离职日期',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_org_id (org_id),
    INDEX idx_post_id (post_id),
    INDEX idx_status (status),
    FOREIGN KEY (org_id) REFERENCES sys_organization(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 3. 岗位表
CREATE TABLE IF NOT EXISTS sys_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    post_code VARCHAR(50) NOT NULL COMMENT '岗位编码',
    post_name VARCHAR(200) NOT NULL COMMENT '岗位名称',
    org_id BIGINT COMMENT '所属组织ID',
    post_level INT DEFAULT 1 COMMENT '岗位级别',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_code (post_code),
    INDEX idx_org_id (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位表';

-- 4. 资源表
CREATE TABLE IF NOT EXISTS sys_resource (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    resource_code VARCHAR(100) NOT NULL UNIQUE COMMENT '资源编码',
    resource_name VARCHAR(200) NOT NULL COMMENT '资源名称',
    resource_type VARCHAR(50) NOT NULL COMMENT '资源类型：TABLE,API,FILE',
    description TEXT COMMENT '资源描述',
    sensitivity_level INT DEFAULT 1 COMMENT '敏感等级：1-5',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_resource_type (resource_type),
    INDEX idx_sensitivity_level (sensitivity_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源表';

-- 5. 敏感字段表
CREATE TABLE IF NOT EXISTS sys_sensitive_field (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    field_name VARCHAR(100) NOT NULL COMMENT '字段名称',
    field_label VARCHAR(200) COMMENT '字段标签',
    sensitivity_level INT DEFAULT 1 COMMENT '敏感等级：1-5',
    desensitization_type ENUM('NONE','MASK','HASH','ENCRYPT','HIDE') DEFAULT 'NONE' COMMENT '脱敏类型',
    mask_pattern VARCHAR(100) COMMENT '掩码模式',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_resource_field (resource_id, field_name),
    INDEX idx_sensitivity_level (sensitivity_level),
    FOREIGN KEY (resource_id) REFERENCES sys_resource(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感字段表';

-- 6. 权限模板表
CREATE TABLE IF NOT EXISTS sys_permission_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    template_code VARCHAR(50) NOT NULL UNIQUE COMMENT '模板编码',
    template_name VARCHAR(200) NOT NULL COMMENT '模板名称',
    description TEXT COMMENT '模板描述',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限模板表';

-- 7. 权限模板明细表
CREATE TABLE IF NOT EXISTS sys_permission_template_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    template_id BIGINT NOT NULL COMMENT '模板ID',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型：READ,WRITE,DELETE,EXPORT',
    field_level_map TEXT COMMENT '字段等级JSON',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_template_id (template_id),
    INDEX idx_resource_id (resource_id),
    FOREIGN KEY (template_id) REFERENCES sys_permission_template(id),
    FOREIGN KEY (resource_id) REFERENCES sys_resource(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限模板明细表';

-- 8. 组织范围授权表
CREATE TABLE IF NOT EXISTS sys_org_scope (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    grant_type ENUM('HEADQUARTER_VIEW_SUB','REGION_ISOLATED','PROJECT_TEMP') NOT NULL COMMENT '授权类型',
    source_org_id BIGINT NOT NULL COMMENT '源组织ID',
    target_org_id BIGINT COMMENT '目标组织ID',
    target_org_type VARCHAR(50) COMMENT '目标组织类型',
    hierarchy_depth INT COMMENT '层级深度',
    priority INT DEFAULT 600 COMMENT '优先级，数字越小优先级越高',
    rule_name VARCHAR(200) COMMENT '规则名称',
    start_time DATETIME COMMENT '生效时间',
    end_time DATETIME COMMENT '失效时间',
    status TINYINT DEFAULT 1 COMMENT '状态',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_grant_type (grant_type),
    INDEX idx_source_org (source_org_id),
    INDEX idx_target_org (target_org_id),
    INDEX idx_status (status),
    INDEX idx_priority (priority),
    FOREIGN KEY (source_org_id) REFERENCES sys_organization(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织范围授权表';

-- 9. 用户权限授权表
CREATE TABLE IF NOT EXISTS sys_user_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    org_scope_type ENUM('ALL','SPECIFIC','HIERARCHY') DEFAULT 'ALL' COMMENT '组织范围类型',
    org_scope_value TEXT COMMENT '组织范围值，JSON格式',
    permission_template_id BIGINT COMMENT '关联权限模板ID',
    operation_types VARCHAR(200) COMMENT '允许的操作类型',
    field_access_level INT DEFAULT 1 COMMENT '可访问字段等级',
    desensitization_enabled TINYINT DEFAULT 1 COMMENT '脱敏开关：0关闭 1开启',
    start_time DATETIME COMMENT '授权开始时间',
    end_time DATETIME COMMENT '授权结束时间',
    grant_reason VARCHAR(500) COMMENT '授权原因',
    grant_type VARCHAR(50) DEFAULT 'MANUAL' COMMENT '授权方式：AUTO,MANUAL,TEMP',
    source_grant_id BIGINT COMMENT '来源授权ID',
    status TINYINT DEFAULT 1 COMMENT '状态：0撤销 1生效',
    created_by BIGINT COMMENT '授权人',
    last_used_time DATETIME COMMENT '最后使用时间',
    used_count BIGINT DEFAULT 0 COMMENT '使用次数',
    risk_tags VARCHAR(500) COMMENT '风险标签',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_resource_id (resource_id),
    INDEX idx_status (status),
    INDEX idx_end_time (end_time),
    INDEX idx_last_used_time (last_used_time),
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (resource_id) REFERENCES sys_resource(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户权限授权表';

-- 10. 权限变更记录表
CREATE TABLE IF NOT EXISTS sys_permission_change_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    permission_id BIGINT COMMENT '权限ID',
    change_type VARCHAR(50) NOT NULL COMMENT '变更类型：GRANT,REVOKE,MODIFY,EXPIRE',
    change_content TEXT COMMENT '变更内容JSON',
    before_value TEXT COMMENT '变更前值',
    after_value TEXT COMMENT '变更后值',
    change_reason VARCHAR(500) COMMENT '变更原因',
    change_by BIGINT COMMENT '变更操作人',
    change_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    client_ip VARCHAR(50) COMMENT '客户端IP',
    user_agent VARCHAR(500) COMMENT '用户代理',
    INDEX idx_permission_id (permission_id),
    INDEX idx_change_type (change_type),
    INDEX idx_change_time (change_time),
    INDEX idx_change_by (change_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限变更记录表';

-- 11. 访问日志表
CREATE TABLE IF NOT EXISTS sys_access_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    access_decision ENUM('ALLOW','DENY','PARTIAL') NOT NULL COMMENT '访问决策',
    denied_reason VARCHAR(500) COMMENT '拒绝原因',
    query_conditions TEXT COMMENT '查询条件JSON',
    result_scope TEXT COMMENT '返回结果范围',
    hidden_fields TEXT COMMENT '隐藏字段列表',
    masked_fields TEXT COMMENT '脱敏字段列表',
    sensitive_fields_accessed TEXT COMMENT '访问的敏感字段列表',
    record_count BIGINT COMMENT '访问记录数',
    data_volume BIGINT COMMENT '数据量',
    request_params TEXT COMMENT '请求参数JSON',
    client_ip VARCHAR(50) COMMENT '客户端IP',
    user_agent VARCHAR(500) COMMENT '用户代理',
    execution_time_ms BIGINT COMMENT '执行耗时',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_resource_id (resource_id),
    INDEX idx_access_decision (access_decision),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访问日志表';

-- 12. 异常操作预警表
CREATE TABLE IF NOT EXISTS sys_anomaly_alert (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    alert_type VARCHAR(50) NOT NULL COMMENT '预警类型',
    alert_content TEXT COMMENT '预警内容',
    alert_level INT DEFAULT 1 COMMENT '预警级别：1低 2中 3高',
    risk_score INT COMMENT '风险评分',
    triggered_dimensions TEXT COMMENT '触发的维度JSON',
    related_access_logs TEXT COMMENT '关联的访问日志ID列表',
    suggestions TEXT COMMENT '处理建议',
    restrict_actions TEXT COMMENT '限制操作',
    handle_status TINYINT DEFAULT 0 COMMENT '处理状态：0未处理 1已处理',
    handle_by BIGINT COMMENT '处理人',
    handle_time DATETIME COMMENT '处理时间',
    handle_result TEXT COMMENT '处理结果',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_alert_type (alert_type),
    INDEX idx_handle_status (handle_status),
    INDEX idx_risk_score (risk_score),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异常操作预警表';

-- 13. 到期提醒记录表
CREATE TABLE IF NOT EXISTS sys_expiration_notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    notice_type VARCHAR(50) NOT NULL COMMENT '提醒类型：BEFORE_EXPIRE,EXPIRED',
    notice_time DATETIME NOT NULL COMMENT '提醒时间',
    notice_status TINYINT DEFAULT 0 COMMENT '发送状态：0待发送 1已发送 2发送失败',
    sent_time DATETIME COMMENT '实际发送时间',
    notice_result TEXT COMMENT '发送结果',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_permission_id (permission_id),
    INDEX idx_notice_time (notice_time),
    INDEX idx_notice_status (notice_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='到期提醒记录表';

-- 14. 权限任务表（离职/转岗）
CREATE TABLE IF NOT EXISTS sys_permission_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id VARCHAR(50) NOT NULL UNIQUE COMMENT '任务ID',
    task_type VARCHAR(50) NOT NULL COMMENT '任务类型：LEAVE,TRANSFER',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    target_user_id BIGINT COMMENT '目标用户ID',
    status VARCHAR(50) DEFAULT 'INITIATED' COMMENT '状态',
    current_step VARCHAR(50) COMMENT '当前步骤',
    steps TEXT COMMENT '步骤JSON',
    affected_permissions TEXT COMMENT '影响的权限JSON',
    change_details TEXT COMMENT '变更详情JSON',
    change_reason VARCHAR(500) COMMENT '变更原因',
    change_by BIGINT COMMENT '操作人',
    due_date DATETIME COMMENT '截止日期',
    completed_time DATETIME COMMENT '完成时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_task_type (task_type),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限任务表';

-- 15. 权限复核任务表
CREATE TABLE IF NOT EXISTS sys_permission_review_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id VARCHAR(50) NOT NULL UNIQUE COMMENT '任务ID',
    task_name VARCHAR(200) NOT NULL COMMENT '任务名称',
    scope_org_ids TEXT COMMENT '组织范围',
    scope_user_ids TEXT COMMENT '用户范围',
    scope_resource_types TEXT COMMENT '资源类型范围',
    risk_filters TEXT COMMENT '风险过滤器',
    reviewers TEXT COMMENT '复核人',
    due_date DATETIME COMMENT '截止日期',
    auto_remind TINYINT DEFAULT 1 COMMENT '自动提醒',
    remind_interval INT DEFAULT 3 COMMENT '提醒间隔天数',
    status VARCHAR(50) DEFAULT 'CREATED' COMMENT '状态',
    statistics_total BIGINT COMMENT '统计总数',
    statistics_expiring BIGINT COMMENT '快到期数量',
    statistics_unused BIGINT COMMENT '长期未使用数量',
    statistics_over_granted BIGINT COMMENT '权限过大数量',
    completed_count BIGINT DEFAULT 0 COMMENT '已完成数量',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_status (status),
    INDEX idx_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限复核任务表';

-- 16. 权限复核项表
CREATE TABLE IF NOT EXISTS sys_permission_review_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id VARCHAR(50) NOT NULL COMMENT '任务ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    risk_type VARCHAR(50) COMMENT '风险类型',
    risk_level INT COMMENT '风险级别',
    risk_details TEXT COMMENT '风险详情JSON',
    suggestions TEXT COMMENT '建议',
    review_status VARCHAR(50) DEFAULT 'PENDING' COMMENT '复核状态',
    reviewer_id BIGINT COMMENT '复核人',
    review_time DATETIME COMMENT '复核时间',
    review_comment TEXT COMMENT '复核意见',
    recommended_action VARCHAR(200) COMMENT '建议操作',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_permission_id (permission_id),
    INDEX idx_review_status (review_status),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限复核项表';

-- 插入示例数据

-- 组织架构示例数据
INSERT INTO sys_organization (org_code, org_name, org_type, parent_id, hierarchy_level, sort_order) VALUES
('HQ', '集团总部', 'HEADQUARTER', NULL, 1, 1),
('C01', '华东区域', 'REGION', 1, 2, 1),
('C02', '华北区域', 'REGION', 1, 2, 2),
('D001', '人力资源部', 'DEPT', 1, 2, 3),
('D002', '销售一部', 'DEPT', 2, 3, 1),
('D003', '销售二部', 'DEPT', 2, 3, 2),
('D004', '财务部', 'DEPT', 3, 3, 1),
('P001', '项目A', 'PROJECT', 5, 4, 1);

-- 用户示例数据 (密码为123456的MD5值)
INSERT INTO sys_user (username, password, real_name, email, phone, org_id, post_id, status) VALUES
('admin', 'e10adc3949ba59abbe56e057f20f883e', '系统管理员', 'admin@company.com', '13800138000', 1, 1, 1),
('manager', 'e10adc3949ba59abbe56e057f20f883e', '区域经理', 'manager@company.com', '13800138001', 2, 2, 1),
('employee1', 'e10adc3949ba59abbe56e057f20f883e', '员工1', 'emp1@company.com', '13800138002', 5, 3, 1),
('employee2', 'e10adc3949ba59abbe56e057f20f883e', '员工2', 'emp2@company.com', '13800138003', 6, 3, 1),
('hr', 'e10adc3949ba59abbe56e057f20f883e', '人事专员', 'hr@company.com', '13800138004', 4, 4, 1);

-- 岗位示例数据
INSERT INTO sys_post (post_code, post_name, org_id, post_level) VALUES
('ADMIN', '系统管理员', 1, 10),
('REGION_MGR', '区域经理', 2, 8),
('SALES_MGR', '销售经理', 5, 6),
('SALES', '销售员', 5, 3),
('HR_SPECIALIST', '人事专员', 4, 5);

-- 资源示例数据
INSERT INTO sys_resource (resource_code, resource_name, resource_type, description, sensitivity_level) VALUES
('employee_table', '员工信息表', 'TABLE', '员工基本信息表', 2),
('salary_table', '薪资信息表', 'TABLE', '员工薪资信息表', 4),
('customer_table', '客户信息表', 'TABLE', '客户信息表', 3),
('project_table', '项目信息表', 'TABLE', '项目信息表', 2),
('financial_table', '财务数据表', 'TABLE', '财务数据表', 5);

-- 敏感字段示例数据
INSERT INTO sys_sensitive_field (resource_id, field_name, field_label, sensitivity_level, desensitization_type, mask_pattern) VALUES
(1, 'id_card', '身份证号', 5, 'HIDE', NULL),
(1, 'phone', '手机号', 3, 'MASK', '前3后4'),
(1, 'email', '邮箱', 2, 'MASK', '前2后@'),
(1, 'address', '家庭住址', 4, 'MASK', '只显示省市'),
(2, 'salary', '工资', 5, 'MASK', '只显示万'),
(2, 'bonus', '奖金', 4, 'MASK', '前1后2'),
(3, 'customer_phone', '客户电话', 3, 'MASK', '前3后4'),
(3, 'customer_name', '客户姓名', 2, 'NONE', NULL),
(5, 'revenue', '营收', 5, 'MASK', '万为单位');

-- 权限模板示例数据
INSERT INTO sys_permission_template (template_code, template_name, description) VALUES
('MANAGER_READ', '经理查看模板', '适用于经理岗位的数据查看权限'),
('EMPLOYEE_BASIC', '员工基础模板', '适用于普通员工的基础数据查看权限'),
('HR_FULL', '人事全权模板', '适用于人事岗位的完整权限');

-- 权限模板明细示例数据
INSERT INTO sys_permission_template_detail (template_id, resource_id, operation_type, field_level_map) VALUES
(1, 1, 'READ', '{"id_card":5,"phone":3,"email":2,"address":4,"name":1,"department":1}'),
(1, 2, 'READ', '{"salary":3,"bonus":3}'),
(1, 3, 'READ', '{"customer_phone":3,"customer_name":1}'),
(2, 1, 'READ', '{"name":1,"department":1,"phone":1,"email":1}'),
(2, 4, 'READ', '{"*":1}'),
(3, 1, 'READ', '{"*":5}'),
(3, 2, 'READ', '{"*":5}'),
(3, 2, 'WRITE', '{"*":5}');

-- 组织范围授权示例数据
INSERT INTO sys_org_scope (grant_type, source_org_id, target_org_type, hierarchy_depth, start_time, end_time, status, created_by) VALUES
('HEADQUARTER_VIEW_SUB', 1, 'DEPT', 5, '2024-01-01 00:00:00', '2025-12-31 23:59:59', 1, 1),
('HEADQUARTER_VIEW_SUB', 2, 'DEPT', 2, '2024-01-01 00:00:00', '2025-12-31 23:59:59', 1, 1),
('REGION_ISOLATED', 2, 'REGION', NULL, '2024-01-01 00:00:00', '2025-12-31 23:59:59', 1, 1);

-- 用户权限授权示例数据
INSERT INTO sys_user_permission (user_id, resource_id, org_scope_type, permission_template_id, operation_types, field_access_level, desensitization_enabled, status, created_by) VALUES
(2, 1, 'ALL', 1, 'READ', 3, 1, 1, 1),
(2, 2, 'ALL', 1, 'READ', 3, 1, 1, 1),
(3, 1, 'SPECIFIC', NULL, 'READ', 2, 1, 1, 1),
(3, 4, 'ALL', NULL, 'READ,WRITE', 2, 1, 1, 1),
(5, 1, 'ALL', 3, 'READ,WRITE', 5, 1, 1, 1),
(5, 2, 'ALL', 3, 'READ,WRITE', 5, 1, 1, 1);
