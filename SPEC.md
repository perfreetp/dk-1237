# 数据权限后端服务技术规格文档

## 1. 项目概述

### 1.1 项目名称
**DataPermissionService** - 集团型组织数据权限后端服务

### 1.2 核心功能定位
为集团型企业提供跨公司、跨部门的数据访问边界控制能力，确保数据安全合规访问。

### 1.3 目标用户
- **系统管理员**：配置组织权限、设置敏感字段、管理授权策略
- **业务系统**：调用权限校验接口获取访问控制决策
- **审计人员**：查询权限变更记录、下载操作日志

## 2. 技术架构

### 2.1 技术栈
- **框架**：Spring Boot 3.2.x
- **ORM**：MyBatis-Plus 3.5.x
- **数据库**：MySQL 8.0
- **缓存**：Redis 7.x（可选）
- **认证**：JWT Token
- **构建工具**：Maven
- **Java版本**：JDK 17+

### 2.2 项目结构
```
data-permission-service/
├── src/main/java/com/example/datapermission/
│   ├── config/           # 配置类
│   ├── controller/       # 控制器层
│   ├── service/         # 业务逻辑层
│   ├── mapper/           # 数据访问层
│   ├── entity/           # 实体类
│   ├── dto/              # 数据传输对象
│   ├── vo/               # 视图对象
│   ├── enums/            # 枚举类
│   ├── exception/        # 异常处理
│   ├── util/             # 工具类
│   ├── security/         # 安全相关
│   └── scheduler/        # 定时任务
├── src/main/resources/
│   ├── mapper/          # MyBatis映射文件
│   ├── application.yml  # 应用配置
│   └── schema.sql       # 数据库脚本
└── src/test/java/       # 测试代码
```

## 3. 数据库设计

### 3.1 核心表结构

#### 3.1.1 组织架构表 (sys_organization)
```sql
CREATE TABLE sys_organization (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    org_code VARCHAR(50) NOT NULL COMMENT '组织编码',
    org_name VARCHAR(200) NOT NULL COMMENT '组织名称',
    org_type ENUM('COMPANY','REGION','DEPT','PROJECT') NOT NULL COMMENT '组织类型',
    parent_id BIGINT COMMENT '父组织ID',
    hierarchy_level INT NOT NULL COMMENT '层级深度',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    status TINYINT DEFAULT 1 COMMENT '状态：0禁用 1启用',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### 3.1.2 用户表 (sys_user)
```sql
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE COMMENT '用户名',
    real_name VARCHAR(100) COMMENT '真实姓名',
    email VARCHAR(200) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    org_id BIGINT NOT NULL COMMENT '所属组织ID',
    post_id BIGINT COMMENT '岗位ID',
    status TINYINT DEFAULT 1 COMMENT '状态：0离职 1在职',
    leave_date DATETIME COMMENT '离职日期',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### 3.1.3 岗位表 (sys_post)
```sql
CREATE TABLE sys_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_code VARCHAR(50) NOT NULL COMMENT '岗位编码',
    post_name VARCHAR(200) NOT NULL COMMENT '岗位名称',
    org_id BIGINT COMMENT '所属组织ID',
    post_level INT DEFAULT 1 COMMENT '岗位级别',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### 3.1.4 资源表 (sys_resource)
```sql
CREATE TABLE sys_resource (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resource_code VARCHAR(100) NOT NULL UNIQUE COMMENT '资源编码',
    resource_name VARCHAR(200) NOT NULL COMMENT '资源名称',
    resource_type VARCHAR(50) NOT NULL COMMENT '资源类型：TABLE,API,FILE',
    description TEXT COMMENT '资源描述',
    sensitivity_level INT DEFAULT 1 COMMENT '敏感等级：1-5',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### 3.1.5 敏感字段表 (sys_sensitive_field)
```sql
CREATE TABLE sys_sensitive_field (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    field_name VARCHAR(100) NOT NULL COMMENT '字段名称',
    field_label VARCHAR(200) COMMENT '字段标签',
    sensitivity_level INT DEFAULT 1 COMMENT '敏感等级：1-5',
    desensitization_type ENUM('NONE','MASK','HASH','ENCRYPT','HIDE') DEFAULT 'NONE' COMMENT '脱敏类型',
    mask_pattern VARCHAR(100) COMMENT '掩码模式，如：****、前3后4',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### 3.1.6 权限模板表 (sys_permission_template)
```sql
CREATE TABLE sys_permission_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_code VARCHAR(50) NOT NULL UNIQUE COMMENT '模板编码',
    template_name VARCHAR(200) NOT NULL COMMENT '模板名称',
    description TEXT COMMENT '模板描述',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### 3.1.7 权限模板明细表 (sys_permission_template_detail)
```sql
CREATE TABLE sys_permission_template_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id BIGINT NOT NULL COMMENT '模板ID',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型：READ,WRITE,DELETE,EXPORT',
    field_level_map TEXT COMMENT '字段等级JSON，如：{field1:3,field2:5}',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

#### 3.1.8 组织范围授权表 (sys_org_scope)
```sql
CREATE TABLE sys_org_scope (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    grant_type ENUM('HEADQUARTER_VIEW_SUB','REGION_ISOLATED','PROJECT_TEMP') NOT NULL COMMENT '授权类型',
    source_org_id BIGINT NOT NULL COMMENT '源组织ID',
    target_org_id BIGINT COMMENT '目标组织ID',
    target_org_type VARCHAR(50) COMMENT '目标组织类型',
    hierarchy_depth INT COMMENT '层级深度（总部查看下级时有效）',
    start_time DATETIME COMMENT '生效时间',
    end_time DATETIME COMMENT '失效时间',
    status TINYINT DEFAULT 1 COMMENT '状态',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### 3.1.9 用户权限授权表 (sys_user_permission)
```sql
CREATE TABLE sys_user_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    org_scope_type ENUM('ALL','SPECIFIC','HIERARCHY') DEFAULT 'ALL' COMMENT '组织范围类型',
    org_scope_value TEXT COMMENT '组织范围值，JSON格式',
    permission_template_id BIGINT COMMENT '关联权限模板',
    operation_types VARCHAR(200) COMMENT '允许的操作类型，逗号分隔',
    field_access_level INT DEFAULT 1 COMMENT '可访问字段等级',
    desensitization_enabled TINYINT DEFAULT 1 COMMENT '脱敏开关：0关闭 1开启',
    start_time DATETIME COMMENT '授权开始时间',
    end_time DATETIME COMMENT '授权结束时间',
    grant_reason VARCHAR(500) COMMENT '授权原因',
    grant_type VARCHAR(50) COMMENT '授权方式：AUTO,MANUAL,TEMP',
    source_grant_id BIGINT COMMENT '来源授权ID（临时授权）',
    status TINYINT DEFAULT 1 COMMENT '状态：0撤销 1生效',
    created_by BIGINT COMMENT '授权人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### 3.1.10 权限变更记录表 (sys_permission_change_log)
```sql
CREATE TABLE sys_permission_change_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_id BIGINT COMMENT '权限ID',
    change_type VARCHAR(50) NOT NULL COMMENT '变更类型：GRANT,REVOKE,MODIFY,EXPIRE',
    change_content TEXT COMMENT '变更内容JSON',
    before_value TEXT COMMENT '变更前值',
    after_value TEXT COMMENT '变更后值',
    change_reason VARCHAR(500) COMMENT '变更原因',
    change_by BIGINT COMMENT '变更操作人',
    change_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    client_ip VARCHAR(50) COMMENT '客户端IP',
    user_agent VARCHAR(500) COMMENT '用户代理'
);
```

#### 3.1.11 访问日志表 (sys_access_log)
```sql
CREATE TABLE sys_access_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    access_decision ENUM('ALLOW','DENY','PARTIAL') NOT NULL COMMENT '访问决策',
    denied_reason VARCHAR(500) COMMENT '拒绝原因',
    query_conditions TEXT COMMENT '查询条件JSON',
    result_scope TEXT COMMENT '返回结果范围',
    hidden_fields TEXT COMMENT '隐藏字段列表',
    request_params TEXT COMMENT '请求参数JSON',
    execution_time_ms BIGINT COMMENT '执行耗时',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

#### 3.1.12 异常操作预警表 (sys_anomaly_alert)
```sql
CREATE TABLE sys_anomaly_alert (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    alert_type VARCHAR(50) NOT NULL COMMENT '预警类型：ABNORMAL_DOWNLOAD,MULTIPLE_ACCESS,OFF_HOURS_ACCESS',
    alert_content TEXT COMMENT '预警内容',
    alert_level INT DEFAULT 1 COMMENT '预警级别：1低 2中 3高',
    handle_status TINYINT DEFAULT 0 COMMENT '处理状态：0未处理 1已处理',
    handle_by BIGINT COMMENT '处理人',
    handle_time DATETIME COMMENT '处理时间',
    handle_result TEXT COMMENT '处理结果',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

#### 3.1.13 到期提醒记录表 (sys_expiration_notice)
```sql
CREATE TABLE sys_expiration_notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    notice_type VARCHAR(50) NOT NULL COMMENT '提醒类型：BEFORE_EXPIRE,EXPIRED',
    notice_time DATETIME NOT NULL COMMENT '提醒时间',
    notice_status TINYINT DEFAULT 0 COMMENT '发送状态：0待发送 1已发送 2发送失败',
    sent_time DATETIME COMMENT '实际发送时间',
    notice_result TEXT COMMENT '发送结果',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

## 4. 功能模块设计

### 4.1 组织范围授权模块

#### 4.1.1 功能说明
支持三种授权类型：
- **总部查看下级**：总部可以查看所有下级组织的数据
- **区域互不可见**：同一区域内的组织之间数据不可见
- **项目成员临时可见**：项目成员在项目周期内可见相关数据

#### 4.1.2 API接口

**创建组织范围授权**
```
POST /api/v1/org-scope
Request Body:
{
    "grantType": "HEADQUARTER_VIEW_SUB",
    "sourceOrgId": 1,
    "targetOrgType": "DEPT",
    "hierarchyDepth": 3,
    "startTime": "2024-01-01 00:00:00",
    "endTime": "2024-12-31 23:59:59"
}
Response: { "code": 0, "message": "success", "data": { "id": 1 } }
```

**查询组织范围授权列表**
```
GET /api/v1/org-scope
Query Params: sourceOrgId, targetOrgType, grantType, pageNum, pageSize
```

**更新组织范围授权**
```
PUT /api/v1/org-scope/{id}
```

**删除组织范围授权**
```
DELETE /api/v1/org-scope/{id}
```

### 4.2 岗位权限模板模块

#### 4.2.1 功能说明
定义不同岗位的数据访问权限模板，包括可访问的资源、操作类型、字段等级等。

#### 4.2.2 API接口

**创建权限模板**
```
POST /api/v1/permission-template
Request Body:
{
    "templateCode": "MANAGER_READ",
    "templateName": "经理查看模板",
    "description": "适用于经理岗位的数据查看权限",
    "details": [
        {
            "resourceId": 1,
            "operationType": "READ",
            "fieldLevelMap": {"salary": 5, "name": 1, "department": 1}
        }
    ]
}
```

**查询权限模板**
```
GET /api/v1/permission-template/{id}
GET /api/v1/permission-template?pageNum=1&pageSize=10
```

**更新权限模板**
```
PUT /api/v1/permission-template/{id}
```

**删除权限模板**
```
DELETE /api/v1/permission-template/{id}
```

### 4.3 敏感字段分级模块

#### 4.3.1 功能说明
对数据资源中的字段进行敏感等级划分，支持5个等级：
- **等级1**：公开字段（无限制）
- **等级2**：内部字段（需登录）
- **等级3**：敏感字段（需特定权限）
- **等级4**：机密字段（需高级权限+审批）
- **等级5**：绝密字段（仅限核心人员）

#### 4.3.2 API接口

**创建敏感字段**
```
POST /api/v1/sensitive-field
Request Body:
{
    "resourceId": 1,
    "fieldName": "salary",
    "fieldLabel": "工资",
    "sensitivityLevel": 5,
    "desensitizationType": "MASK",
    "maskPattern": "前4后2"
}
```

**批量导入敏感字段**
```
POST /api/v1/sensitive-field/batch
Request Body:
{
    "resourceId": 1,
    "fields": [
        {"fieldName": "id_card", "sensitivityLevel": 5, "desensitizationType": "HIDE"},
        {"fieldName": "phone", "sensitivityLevel": 3, "desensitizationType": "MASK", "maskPattern": "前3后4"}
    ]
}
```

**查询敏感字段**
```
GET /api/v1/sensitive-field?resourceId=1
```

**更新敏感字段**
```
PUT /api/v1/sensitive-field/{id}
```

### 4.4 数据脱敏开关模块

#### 4.4.1 功能说明
支持对用户或角色设置脱敏开关：
- **开启脱敏**：按字段敏感等级自动脱敏
- **关闭脱敏**：完全隐藏该字段（而非脱敏显示）

#### 4.4.2 API接口

**设置用户脱敏开关**
```
PUT /api/v1/user/{userId}/desensitization
Request Body:
{
    "resourceId": 1,
    "desensitizationEnabled": true,
    "fieldAccessLevel": 3
}
```

**查询用户脱敏配置**
```
GET /api/v1/user/{userId}/desensitization?resourceId=1
```

### 4.5 访问校验模块（核心API）

#### 4.5.1 功能说明
业务系统调用此接口获取数据访问权限决策。

#### 4.5.2 API接口

**权限校验接口**
```
POST /api/v1/access/check
Request Body:
{
    "userId": 123,
    "resourceCode": "employee_table",
    "operationType": "READ",
    "queryConditions": {
        "orgId": 456,
        "department": "销售部",
        "dateRange": ["2024-01-01", "2024-12-31"]
    },
    "requestedFields": ["name", "salary", "department", "email"]
}

Response:
{
    "code": 0,
    "message": "success",
    "data": {
        "accessDecision": "PARTIAL",
        "allowed": true,
        "accessibleScope": {
            "orgIds": [456, 457, 458],
            "orgType": "DEPT"
        },
        "hiddenFields": ["salary"],
        "maskedFields": [
            {
                "field": "email",
                "maskedValue": "z***@company.com",
                "reason": "字段等级超过用户权限"
            }
        ],
        "deniedReason": "部分字段无访问权限",
        "applyUrl": "/permission/apply?resourceId=1&fields=salary",
        "suggestions": [
            "您的岗位权限模板中不包含salary字段的访问权限",
            "可申请临时授权访问该字段"
        ],
        "queryFilters": [
            {"field": "org_id", "operator": "IN", "value": [456, 457, 458]}
        ],
        "executionTime": 45
    }
}
```

### 4.5.2 增强版访问校验接口（V2）

#### 4.5.2.1 支持的复杂查询条件

**时间范围条件**
```json
{
    "timeConditions": {
        "field": "create_time",
        "startTime": "2024-01-01 00:00:00",
        "endTime": "2024-12-31 23:59:59",
        "timeZone": "Asia/Shanghai"
    }
}
```

**客户等级条件**
```json
{
    "customerConditions": {
        "field": "customer_level",
        "levels": [1, 2, 3],
        "operator": "IN"
    }
}
```

**项目条件**
```json
{
    "projectConditions": {
        "field": "project_id",
        "projectIds": [101, 102, 103],
        "includeSubProject": true
    }
}
```

**组合条件（AND/OR）**
```json
{
    "complexConditions": {
        "operator": "AND",
        "rules": [
            {"field": "org_id", "operator": "IN", "value": [1, 2, 3]},
            {"field": "customer_level", "operator": ">=", "value": 3},
            {
                "operator": "OR",
                "rules": [
                    {"field": "project_id", "operator": "IN", "value": [101, 102]},
                    {"field": "is_test", "operator": "=", "value": false}
                ]
            }
        ]
    }
}
```

#### 4.5.2.2 增强版权限校验请求
```json
{
    "userId": 123,
    "resourceCode": "sales_data",
    "operationType": "READ",
    "version": "v2",
    "queryConditions": {
        "orgId": 456,
        "department": "销售部"
    },
    "complexConditions": {
        "timeRange": {
            "field": "order_time",
            "startTime": "2024-01-01",
            "endTime": "2024-12-31"
        },
        "customerLevel": {
            "field": "customer_level",
            "levels": [1, 2, 3, 4],
            "operator": "IN"
        },
        "projectScope": {
            "field": "project_id",
            "projectIds": [101, 102],
            "includeSubProject": true
        }
    },
    "requestedFields": ["order_no", "customer_name", "amount", "profit", "commission"],
    "returnSqlFilter": true,
    "returnAppliedRules": true
}
```

#### 4.5.2.3 增强版权限校验响应
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "accessDecision": "PARTIAL",
        "allowed": true,
        
        "appliedRules": [
            {
                "ruleId": 1001,
                "ruleType": "HEADQUARTER_VIEW_SUB",
                "ruleName": "总部查看下级组织",
                "priority": 10,
                "matched": true,
                "matchReason": "用户属于集团总部，可查看3级以内下级组织"
            },
            {
                "ruleId": 1002,
                "ruleType": "REGION_ISOLATED",
                "ruleName": "区域数据隔离",
                "priority": 20,
                "matched": false,
                "matchReason": "不涉及区域隔离规则"
            }
        ],
        
        "effectiveRule": {
            "ruleId": 1001,
            "ruleType": "HEADQUARTER_VIEW_SUB",
            "ruleName": "总部查看下级组织",
            "effectiveReason": "优先级最高且命中"
        },
        
        "accessibleScope": {
            "orgIds": [456, 457, 458, 459, 460],
            "orgType": "DEPT",
            "projectIds": [101, 102, 103],
            "timeRange": {
                "startTime": "2024-01-01 00:00:00",
                "endTime": "2024-12-31 23:59:59"
            },
            "customerLevels": [1, 2, 3]
        },
        
        "sqlFilters": {
            "whereClause": "org_id IN (456, 457, 458, 459, 460) AND customer_level IN (1, 2, 3) AND project_id IN (101, 102, 103)",
            "orderByClause": "order_time DESC",
            "limitClause": "LIMIT 1000"
        },
        
        "fieldPermissions": [
            {"field": "order_no", "allowed": true, "masked": false},
            {"field": "customer_name", "allowed": true, "masked": false},
            {"field": "amount", "allowed": true, "masked": false},
            {"field": "profit", "allowed": false, "masked": true, "maskedValue": "***", "reason": "需要等级4权限"},
            {"field": "commission", "allowed": false, "masked": false, "reason": "需要等级5权限"}
        ],
        
        "deniedReason": "profit字段需要等级4权限，commission字段需要等级5权限",
        "applyPermission": {
            "canApply": true,
            "applyUrl": "/permission/apply",
            "permissionOptions": [
                {
                    "permissionType": "FIELD_LEVEL",
                    "targetFields": ["profit"],
                    "requiredLevel": 4,
                    "approvalRequired": true,
                    "validityPeriod": "30天"
                },
                {
                    "permissionType": "FIELD_LEVEL",
                    "targetFields": ["commission"],
                    "requiredLevel": 5,
                    "approvalRequired": true,
                    "validityPeriod": "需特批"
                }
            ]
        },
        "suggestions": [
            "您的当前权限等级为3，可申请临时授权提升至等级4",
            "或联系管理员申请commission字段的特殊权限"
        ],
        "executionTime": 45
    }
}
```

**批量权限校验**
```
POST /api/v1/access/check-batch
Request Body:
{
    "userId": 123,
    "checks": [
        {
            "resourceCode": "employee_table",
            "operationType": "READ"
        },
        {
            "resourceCode": "salary_table",
            "operationType": "READ"
        }
    ]
}
```

### 4.6 授权盘点模块

#### 4.6.1 功能说明
提供权限全景视图，支持权限查询、分析和导出。

#### 4.6.2 API接口

**查询用户权限清单**
```
GET /api/v1/permission/user/{userId}
Query Params: resourceType, status, pageNum, pageSize
```

**查询资源权限分布**
```
GET /api/v1/permission/resource/{resourceId}
Query Params: startDate, endDate, orgId
```

**导出权限清单**
```
GET /api/v1/permission/export
Query Params: type(USER/RESOURCE/ORG), format(EXCEL/CSV), filters...
```

**权限统计分析**
```
GET /api/v1/permission/statistics
Query Params: type, dimension, dateRange
```

### 4.7 审计追踪模块

#### 4.7.1 功能说明
记录所有权限变更和访问操作，支持查询和导出。

#### 4.7.2 API接口

**查询权限变更日志**
```
GET /api/v1/audit/permission-change
Query Params: userId, permissionId, changeType, startDate, endDate, pageNum, pageSize
```

**查询访问日志**
```
GET /api/v1/audit/access-log
Query Params: userId, resourceId, accessDecision, startDate, endDate, pageNum, pageSize
```

**导出访问日志**
```
GET /api/v1/audit/access-log/export
Query Params: startDate, endDate, format
```

### 4.8 定时任务模块

#### 4.8.1 授权到期提醒
- 提前7天、3天、1天发送到期提醒
- 到期当天发送过期通知

#### 4.8.2 离职自动回收
- 每天凌晨扫描离职用户
- 自动撤销其所有临时授权
- 记录回收日志

#### 4.8.3 异常下载预警
- 统计用户下载量
- 单日超过阈值（默认100条）触发预警
- 检测非常用时间访问

## 5. 增强功能规格

### 5.1 权限规则优先级与冲突处理

#### 5.1.1 优先级定义
```yaml
权限规则优先级（数字越小优先级越高）：
1. 特权规则（PRIVILEGED）：100   # 最高优先级，用于紧急授权
2. 禁止规则（DENY）：200        # 禁止访问，绝对优先
3. 项目临时规则（PROJECT_TEMP）：300
4. 手动授权规则（MANUAL）：400
5. 岗位模板规则（POST_TEMPLATE）：500
6. 组织范围规则（ORG_SCOPE）：600  # 总部查看下级、区域隔离等
7. 自动继承规则（AUTO）：700      # 默认权限
```

#### 5.1.2 冲突处理策略
```
冲突场景：总部查看下级 + 区域互不可见同时命中

处理策略：
1. 评估每条规则的优先级
2. 禁止规则（DENY）绝对优先，直接拒绝
3. 特权规则（PRIVILEGED）次之，授予全部权限
4. 同优先级规则取交集：
   - HEADQUARTER_VIEW_SUB + REGION_ISOLATED
   - 结果 = (总部可见组织) ∩ (区域内组织) - (隔离组织)
5. 返回生效规则说明，帮助管理员理解决策依据
```

#### 5.1.3 冲突检测API
```
GET /api/v1/org-scope/conflict-check
Request: 
{
    "sourceOrgId": 1,
    "targetOrgId": 5,
    "grantTypes": ["HEADQUARTER_VIEW_SUB", "REGION_ISOLATED"]
}

Response:
{
    "hasConflict": true,
    "conflicts": [
        {
            "rule1": {"id": 1001, "type": "HEADQUARTER_VIEW_SUB", "priority": 600},
            "rule2": {"id": 1002, "type": "REGION_ISOLATED", "priority": 600},
            "conflictType": "SCOPE_OVERLAP",
            "resolution": "取交集"
        }
    ],
    "effectiveRules": [
        {"id": 1001, "type": "HEADQUARTER_VIEW_SUB", "effective": true}
    ]
}
```

#### 5.1.4 规则可视化
```
GET /api/v1/org-scope/visualize/{orgId}

Response:
{
    "orgId": 1,
    "orgName": "集团总部",
    "rules": [
        {
            "id": 1001,
            "ruleType": "HEADQUARTER_VIEW_SUB",
            "ruleName": "总部查看下级",
            "priority": 600,
            "status": "ACTIVE",
            "effectiveOrgIds": [2, 3, 4, 5, 6, 7, 8],
            "conflicts": [
                {"conflictRuleId": 1002, "conflictType": "REGION_ISOLATED", "resolved": true}
            ]
        }
    ],
    "summary": {
        "totalRules": 3,
        "activeRules": 2,
        "conflicts": 1,
        "conflictsResolved": 1
    }
}
```

### 5.2 增强版审计盘点

#### 5.2.1 权限清单导出
```
GET /api/v1/audit/permission/export
Query Params:
- type: USER | RESOURCE | ORG
- format: EXCEL | CSV | JSON
- riskFilter: EXPIRING | UNUSED | OVER_GRANTED
- startDate: 2024-01-01
- endDate: 2024-12-31

导出Excel包含以下Sheet:
1. 权限总览 - 用户/资源/组织维度的权限清单
2. 风险标注 - 快到期、长期未使用、权限过大
3. 变更记录 - 权限变更历史
4. 统计汇总 - 各项统计数据
```

#### 5.2.2 风险权限标注

**快到期权限（EXPIRING）**
```json
{
    "riskType": "EXPIRING",
    "daysRemaining": 7,
    "riskLevel": "MEDIUM",
    "suggestions": ["续期授权", "评估是否需要"],
    "autoAction": "发送提醒"
}
```

**长期未使用权限（UNUSED）**
```json
{
    "riskType": "UNUSED",
    "lastUsedTime": "2024-01-01 00:00:00",
    "unusedDays": 90,
    "riskLevel": "HIGH",
    "suggestions": ["回收权限", "确认是否需要"],
    "autoAction": "标记待复核"
}
```

**权限过大（OVER_GRANTED）**
```json
{
    "riskType": "OVER_GRANTED",
    "riskLevel": "CRITICAL",
    "details": {
        "currentLevel": 5,
        "typicalLevel": 2,
        "fieldCount": 50,
        "orgScope": "ALL"
    },
    "suggestions": ["降低权限等级", "限制组织范围"],
    "autoAction": "通知管理员"
}
```

#### 5.2.3 定期复核任务
```
POST /api/v1/audit/review-task
Request:
{
    "taskName": "2024年Q1权限复核",
    "scope": {
        "orgIds": [1, 2, 3],
        "userIds": null,
        "resourceTypes": ["TABLE", "API"]
    },
    "riskFilters": ["EXPIRING", "UNUSED", "OVER_GRANTED"],
    "reviewers": [101, 102, 103],
    "dueDate": "2024-03-31",
    "autoRemind": true,
    "remindInterval": 3
}

Response:
{
    "taskId": "RT202403001",
    "status": "CREATED",
    "statistics": {
        "totalPermissions": 1500,
        "expiringCount": 120,
        "unusedCount": 85,
        "overGrantedCount": 15
    },
    "assignedReviewers": [
        {"userId": 101, "assignedCount": 500},
        {"userId": 102, "assignedCount": 500},
        {"userId": 103, "assignedCount": 500}
    ]
}
```

#### 5.2.4 复核操作API
```
GET /api/v1/audit/review-task/{taskId}/items
# 获取待复核权限项

POST /api/v1/audit/review-task/{taskId}/items/{itemId}/approve
# 审批通过

POST /api/v1/audit/review-task/{taskId}/items/{itemId}/revoke
# 建议回收

POST /api/v1/audit/review-task/{taskId}/items/{itemId}/modify
# 建议修改

GET /api/v1/audit/review-task/{taskId}/report
# 生成复核报告
```

### 5.3 增强版异常预警

#### 5.3.1 多维度异常检测
```yaml
异常检测维度：
1. 下载次数异常
   - 阈值：100次/日
   - 级别：超过200次为高危

2. 数据量异常
   - 阈值：10000条/日
   - 级别：超过50000条为高危

3. 敏感字段访问异常
   - 阈值：10个敏感字段/日
   - 级别：超过30个为高危

4. 访问频率异常
   - 阈值：500次/小时
   - 级别：超过1000次为高危

5. 非工作时间访问
   - 时间范围：22:00 - 06:00
   - 级别：周末比工作日风险更高

综合评分公式：
riskScore = downloadScore * 0.3 + dataVolumeScore * 0.25 + 
            sensitiveFieldScore * 0.25 + frequencyScore * 0.1 + 
            offHoursScore * 0.1

风险等级：
- LOW: score < 30
- MEDIUM: 30 <= score < 60
- HIGH: 60 <= score < 80
- CRITICAL: score >= 80
```

#### 5.3.2 预警记录关联
```json
{
    "alertId": "AL20240315001",
    "alertType": "COMPREHENSIVE",
    "riskScore": 85,
    "riskLevel": "CRITICAL",
    "triggeredDimensions": [
        {
            "dimension": "DOWNLOAD_COUNT",
            "threshold": 100,
            "actual": 250,
            "score": 25
        },
        {
            "dimension": "DATA_VOLUME",
            "threshold": 10000,
            "actual": 80000,
            "score": 20
        },
        {
            "dimension": "SENSITIVE_FIELD_COUNT",
            "threshold": 10,
            "actual": 45,
            "score": 20
        }
    ],
    "relatedAccessLogs": [
        {
            "logId": 1001,
            "accessTime": "2024-03-15 14:30:25",
            "resourceCode": "customer_data",
            "recordCount": 5000,
            "sensitiveFields": ["phone", "id_card", "address"]
        }
    ],
    "userInfo": {
        "userId": 123,
        "username": "zhangsan",
        "orgName": "销售部",
        "postName": "客户经理",
        "avgAccessCount": 30
    },
    "suggestions": [
        "建议立即联系用户确认业务需求",
        "建议临时限制该用户的数据导出功能"
    ],
    "handleStatus": "PENDING",
    "createdTime": "2024-03-15 15:00:00"
}
```

#### 5.3.3 预警处理API
```
GET /api/v1/alert/page
# 查询预警列表

GET /api/v1/alert/{alertId}
# 获取预警详情

PUT /api/v1/alert/{alertId}/handle
Request:
{
    "action": "CONFIRMED | FALSE_ALARM | RESTRICTED",
    "handleResult": "已确认为正常业务行为",
    "restrictActions": ["EXPORT", "SENSITIVE_FIELD_ACCESS"]
}

POST /api/v1/alert/{alertId}/block-user
# 封禁用户

GET /api/v1/alert/statistics
# 预警统计
```

### 5.4 离职与转岗回收流程

#### 5.4.1 离职流程
```
流程步骤：
1. 用户状态变更为"待离职"
   - 系统自动冻结账户
   - 暂停所有数据访问

2. 权限预评估
   - 统计当前有效权限
   - 识别共享账户风险
   - 标记需交接权限

3. 权限交接
   - 指定交接人
   - 迁移必要权限
   - 记录交接清单

4. 权限回收
   - 自动回收全部授权
   - 保留变更记录
   - 生成交接报告

5. 完成离职
   - 正式禁用账户
   - 发送通知给相关人
```

#### 5.4.2 离职API
```
POST /api/v1/user/{userId}/leave
Request:
{
    "leaveDate": "2024-03-31",
    "transferToUserId": 456,
    "transferPermissions": true,
    "notifyReviewers": true
}

Response:
{
    "taskId": "LT20240331001",
    "status": "INITIATED",
    "steps": [
        {"step": "ACCOUNT_FREEZE", "status": "PENDING"},
        {"step": "PERMISSION_ASSESS", "status": "PENDING"},
        {"step": "PERMISSION_TRANSFER", "status": "PENDING"},
        {"step": "PERMISSION_REVOKE", "status": "PENDING"},
        {"step": "LEAVE_COMPLETE", "status": "PENDING"}
    ],
    "affectedPermissions": [
        {"id": 1, "resourceCode": "customer_data", "status": "TO_TRANSFER"},
        {"id": 2, "resourceCode": "sales_report", "status": "TO_REVOKE"}
    ]
}

GET /api/v1/user/{userId}/leave/progress
# 查询离职进度

POST /api/v1/user/{userId}/leave/cancel
# 取消离职（员工撤销）
```

#### 5.4.3 转岗流程
```
流程步骤：
1. 新岗位分配
   - 指定新组织
   - 指定新岗位

2. 旧权限评估
   - 保留需延续权限
   - 标记需回收权限

3. 新权限计算
   - 根据新岗位模板计算权限
   - 合并保留权限
   - 处理冲突规则

4. 权限变更执行
   - 回收旧权限
   - 授予新权限
   - 记录变更前后对比
```

#### 5.4.4 转岗API
```
POST /api/v1/user/{userId}/transfer
Request:
{
    "targetOrgId": 5,
    "targetPostId": 10,
    "transferDate": "2024-04-01",
    "keepPermissions": [
        {"resourceId": 1, "reason": "项目延续需要"},
        {"resourceId": 3, "reason": "客户关系维护"}
    ],
    "revokePermissions": [
        {"resourceId": 2, "reason": "原部门专属权限"}
    ]
}

Response:
{
    "taskId": "TR20240401001",
    "status": "INITIATED",
    "permissionChanges": {
        "toKeep": [
            {"resourceId": 1, "resourceName": "客户数据", "kept": true, "keepReason": "项目延续"}
        ],
        "toRevoke": [
            {"resourceId": 2, "resourceName": "内部报表", "revokeReason": "部门专属"}
        ],
        "toGrant": [
            {"resourceId": 10, "resourceName": "新部门报表", "fromTemplate": "新岗位模板"}
        ]
    },
    "comparison": {
        "beforeCount": 15,
        "afterCount": 12,
        "newFields": ["field1", "field2"],
        "removedFields": ["field3"]
    }
}
```

#### 5.4.5 变更留痕
```
每条变更记录包含：
{
    "changeId": "CH20240331001",
    "userId": 123,
    "changeType": "TRANSFER",
    "triggerType": "MANUAL | SYSTEM",
    "beforeState": {
        "orgId": 1,
        "orgName": "销售一部",
        "postId": 5,
        "postName": "销售经理",
        "permissions": [...]
    },
    "afterState": {
        "orgId": 2,
        "orgName": "销售二部",
        "postId": 10,
        "postName": "高级销售",
        "permissions": [...]
    },
    "changeDetails": [
        {
            "permissionId": 1,
            "action": "REVOKE",
            "beforeValue": {...},
            "afterValue": null
        },
        {
            "permissionId": 10,
            "action": "GRANT",
            "beforeValue": null,
            "afterValue": {...}
        }
    ],
    "changeReason": "员工转岗",
    "changeBy": 999,
    "changeTime": "2024-04-01 09:00:00"
}
```

## 5.5 批量访问校验V2

#### 5.5.1 功能说明
支持业务系统一次传入多个人员、多类资源和多组查询条件，按请求项分别返回放行、拒绝、脱敏字段和可拼查询范围，失败的项不影响其他项。

#### 5.5.2 API接口
```
POST /api/v1/access/check-batch-v2
Request Body:
{
    "items": [
        {
            "itemId": "item-001",
            "userId": 123,
            "resourceCode": "employee_table",
            "operationType": "READ",
            "complexConditions": {
                "timeRange": {
                    "field": "create_time",
                    "startTime": "2024-01-01",
                    "endTime": "2024-12-31"
                },
                "customerLevel": {
                    "field": "customer_level",
                    "levels": [1, 2, 3],
                    "operator": "IN"
                }
            },
            "requestedFields": ["name", "salary", "department"]
        },
        {
            "itemId": "item-002",
            "userId": 456,
            "resourceCode": "salary_table",
            "operationType": "READ",
            "requestedFields": ["amount", "bonus"]
        }
    ],
    "executionMode": "PARALLEL",
    "timeoutMs": 5000,
    "continueOnError": true
}

Response:
{
    "code": 0,
    "message": "success",
    "data": {
        "totalCount": 2,
        "successCount": 1,
        "failureCount": 1,
        "results": [
            {
                "itemId": "item-001",
                "success": true,
                "accessDecision": "PARTIAL",
                "accessibleScope": {
                    "orgIds": [1, 2, 3],
                    "projectIds": [101, 102]
                },
                "sqlFilters": {
                    "whereClause": "org_id IN (1, 2, 3) AND customer_level IN (1, 2, 3)",
                    "orderByClause": "create_time DESC"
                },
                "maskedFields": [
                    {"field": "salary", "maskedValue": "***", "reason": "需要等级5权限"}
                ]
            },
            {
                "itemId": "item-002",
                "success": false,
                "errorMessage": "用户无访问权限"
            }
        ],
        "executionTimeMs": 120
    }
}
```

### 5.6 权限回收补偿重试机制

#### 5.6.1 功能说明
离职或转岗回收中，如果某个资源系统回收失败，能看到失败原因、重试次数和最终状态，重新触发后不重复回收已经成功的权限。

#### 5.6.2 回收记录表 (sys_permission_recovery)
```sql
CREATE TABLE sys_permission_recovery (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(50) NOT NULL COMMENT '回收任务ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    status TINYINT DEFAULT 0 COMMENT '状态：0待回收 1成功 2失败',
    error_message TEXT COMMENT '失败原因',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
    max_retry_count INT DEFAULT 3 COMMENT '最大重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    recovered_by BIGINT COMMENT '回收操作人',
    recovered_time DATETIME COMMENT '回收时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### 5.6.3 API接口
```
POST /api/v1/recovery/task
Request:
{
    "userId": 123,
    "reason": "LEAVE",
    "transferToUserId": 456
}

Response:
{
    "taskId": "RCV20240331001",
    "status": "PROCESSING",
    "totalPermissions": 15,
    "pendingCount": 12,
    "successCount": 2,
    "failedCount": 1
}

GET /api/v1/recovery/task/{taskId}
# 获取任务详情

Response:
{
    "taskId": "RCV20240331001",
    "status": "PARTIAL_FAILED",
    "items": [
        {
            "id": 1,
            "permissionId": 1001,
            "resourceCode": "customer_data",
            "status": "SUCCESS",
            "recoveredTime": "2024-03-31 10:00:00"
        },
        {
            "id": 2,
            "permissionId": 1002,
            "resourceCode": "salary_table",
            "status": "FAILED",
            "errorMessage": "资源系统连接超时",
            "retryCount": 3,
            "maxRetryCount": 3,
            "nextRetryTime": null
        }
    ]
}

POST /api/v1/recovery/task/{taskId}/retry
# 重新触发回收（跳过已成功的权限）

POST /api/v1/recovery/task/{taskId}/items/{itemId}/retry
# 单个权限重试
```

### 5.7 权限风险看板

#### 5.7.1 功能说明
按组织、岗位、资源敏感级别汇总快到期、长期未使用、越权申请和异常下载数量，点某一类能继续查明细列表。

#### 5.7.2 API接口
```
GET /api/v1/risk/dashboard
Query Params:
- orgIds: 组织ID列表
- postIds: 岗位ID列表
- resourceSensitivityLevels: 敏感级别列表
- groupBy: org | post | sensitivity

Response:
{
    "code": 0,
    "data": {
        "summary": {
            "expiringCount": 45,
            "unusedCount": 120,
            "overGrantedCount": 15,
            "abnormalDownloadCount": 8,
            "totalRisks": 188,
            "riskScore": 65.5
        },
        "categoryStats": [
            {
                "category": "EXPIRING",
                "categoryName": "即将到期",
                "count": 45,
                "percentage": 23.9,
                "details": [
                    {
                        "id": 1,
                        "userName": "张三",
                        "orgName": "销售部",
                        "resourceName": "客户数据",
                        "riskType": "EXPIRING",
                        "riskLevel": 1,
                        "riskDescription": "权限将在 5 天后到期"
                    }
                ]
            }
        ],
        "crossDimensionMatrix": {
            "byOrganization": {
                "销售部": 50,
                "市场部": 35
            },
            "byPost": {
                "客户经理": 40,
                "销售主管": 25
            },
            "bySensitivity": {
                "L1": 80,
                "L3": 60,
                "L5": 48
            }
        },
        "trendData": [
            {"date": "2024-03-25", "metric": "expiring", "value": 42},
            {"date": "2024-03-25", "metric": "unused", "value": 115}
        ]
    }
}

GET /api/v1/risk/dashboard/summary
# 获取简化汇总

GET /api/v1/risk/dashboard/category/{category}
# 按类别获取统计

GET /api/v1/risk/details
Query Params:
- riskType: EXPIRING | UNUSED | OVER_GRANTED | ABNORMAL_DOWNLOAD
- pageNum: 1
- pageSize: 10

# 获取风险明细列表
```

### 5.8 深度规则模拟

#### 5.8.1 功能说明
管理员选择用户和业务查询场景，临时调整总部、区域、项目规则后预览最终可见组织、项目、脱敏字段和被拒原因，确认后再真正保存规则。

#### 5.8.2 API接口
```
POST /api/v1/rule-simulation/preview
Request:
{
    "userId": 123,
    "businessScenario": "CUSTOMER_QUERY",
    "resourceCode": "customer_data",
    "tempAdjustments": {
        "orgScope": {
            "scopeType": "HIERARCHY",
            "includeOrgIds": [1, 2, 3],
            "excludeOrgIds": [5],
            "hierarchyDepth": 3
        },
        "project": {
            "includeProjectIds": [101, 102],
            "excludeProjectIds": [105],
            "maxProjectCount": 50
        },
        "field": {
            "additionalVisibleFields": ["internal_note"],
            "removedVisibleFields": [],
            "additionalMaskedFields": ["salary"],
            "temporaryDesensitizationLevel": 3
        }
    },
    "previewMode": "FULL"
}

Response:
{
    "status": "SUCCESS",
    "userId": 123,
    "businessScenario": "CUSTOMER_QUERY",
    "resourceCode": "customer_data",
    "preview": {
        "visibleOrgs": [
            {"orgId": 1, "orgName": "集团总部", "accessLevel": "INCLUDE"},
            {"orgId": 2, "orgName": "华东区", "accessLevel": "HIERARCHY"}
        ],
        "visibleProjects": [
            {"projectId": 101, "projectName": "项目A", "accessLevel": "FULL", "isIncluded": true}
        ],
        "visibleFields": ["name", "company", "contact", "internal_note"],
        "maskedFields": ["salary", "bank_account"],
        "deniedFields": ["id_card"],
        "sqlScope": {
            "allowedConditions": ["org_id IN (1,2,3)"],
            "deniedConditions": ["org_id = 5"],
            "optimizedWhereClause": "org_id IN (1,2,3) AND project_id IN (101,102)",
            "parameterValues": {
                "userId": 123,
                "resourceCode": "customer_data"
            }
        },
        "fieldAccessLevel": {
            "effectiveLevel": 3,
            "fieldGroups": ["basic_info", "extended_info", "contact_info"],
            "fieldSpecificLevels": {
                "basic_info": 1,
                "contact_info": 3
            }
        }
    },
    "warnings": [],
    "denialReasons": ["以下字段被明确拒绝访问: id_card"],
    "ruleEvaluationDetails": {
        "appliedRules": [
            {
                "ruleId": "RULE-1001",
                "ruleName": "岗位模板规则",
                "ruleType": "POST_TEMPLATE",
                "priority": 500,
                "effect": "允许访问READ操作"
            }
        ],
        "conflicts": [],
        "finalDecision": "PARTIAL_ALLOW"
    }
}

POST /api/v1/rule-simulation/save
# 保存模拟结果到实际权限

POST /api/v1/rule-simulation/preview/comparison
# 对比调整前后的规则变化

GET /api/v1/rule-simulation/scenarios
# 获取可用的业务场景列表

GET /api/v1/rule-simulation/adjustment-templates
# 获取调整模板配置
```

## 6. 核心算法设计

### 5.1 权限计算算法

```
1. 获取用户基本信息（组织、岗位）
2. 查询用户直接授权
3. 查询岗位关联的权限模板
4. 查询组织范围授权规则
5. 合并计算最终权限范围：
   - 组织范围 = 直接授权范围 ∪ 模板授权范围 ∪ 组织规则范围
   - 字段范围 = min(用户字段等级, 资源字段等级)
6. 生成访问决策
```

### 5.2 脱敏算法

```
1. 获取用户字段访问等级
2. 遍历请求字段：
   IF 字段等级 > 用户等级:
      IF 脱敏开关开启:
         应用脱敏规则（掩码/哈希/加密）
      ELSE:
         加入隐藏字段列表
   ELSE:
      字段可正常访问
3. 返回脱敏结果
```

## 6. 安全设计

### 6.1 认证授权
- API调用需要携带JWT Token
- Token包含用户ID、组织ID、角色信息
- 支持权限验证和操作审计

### 6.2 数据安全
- 敏感数据加密存储
- 日志脱敏处理
- 操作需要权限验证

### 6.3 接口安全
- 请求频率限制
- 参数校验
- SQL注入防护

## 7. 配置项

### 7.1 应用配置 (application.yml)
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/data_permission?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: password
  
data-permission:
  # 脱敏配置
  desensitization:
    enabled: true
    default-mask-char: "*"
  
  # 预警配置
  alert:
    download-threshold: 100
    check-interval: 3600  # 秒
  
  # 到期提醒配置
  expiration:
    notice-days: 7,3,1,0
    notice-time: "09:00"
  
  # JWT配置
  jwt:
    secret: your-secret-key
    expiration: 86400000  # 毫秒
```

## 8. 验收标准

### 8.1 功能验收
- [ ] 组织范围授权CRUD完成
- [ ] 岗位权限模板CRUD完成
- [ ] 敏感字段分级CRUD完成
- [ ] 数据脱敏开关可配置
- [ ] 访问校验API返回正确的决策结果
- [ ] 授权盘点可查询和导出
- [ ] 审计日志完整记录

### 8.2 定时任务验收
- [ ] 到期提醒定时发送
- [ ] 离职自动回收生效
- [ ] 异常预警正确触发

### 8.3 性能验收
- [ ] 访问校验接口响应时间 < 100ms
- [ ] 支持1000并发访问
- [ ] 数据库查询有适当索引

### 8.4 安全验收
- [ ] JWT认证生效
- [ ] 权限验证完整
- [ ] 审计日志不可篡改
