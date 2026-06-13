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

## 5. 核心算法设计

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
