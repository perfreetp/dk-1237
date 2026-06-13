# 数据权限后端服务 (DataPermissionService)

集团型组织数据权限后端服务，为企业提供跨公司、跨部门的数据访问边界控制能力。

## 功能特性

### 核心功能
- **组织范围授权**：支持总部查看下级、区域互不可见、项目成员临时可见等规则
- **岗位权限模板**：定义不同岗位的数据访问权限模板
- **敏感字段分级**：5级敏感度分类（公开、内部、敏感、机密、绝密）
- **数据脱敏开关**：支持掩码、哈希、加密、隐藏等多种脱敏方式
- **访问校验API**：业务系统调用获取数据访问权限决策
- **授权盘点和审计追踪**：完整的权限变更记录和访问日志

### 定时任务
- **授权到期提醒**：提前7天、3天、1天发送到期提醒
- **离职自动回收**：自动撤销离职用户的临时授权
- **异常下载预警**：检测异常数据访问行为

## 技术栈

- Java 17
- Spring Boot 3.2.5
- MyBatis-Plus 3.5.6
- MySQL 8.0
- Redis (可选)
- JWT Token

## 项目结构

```
data-permission-service/
├── src/main/java/com/example/datapermission/
│   ├── config/           # 配置类
│   ├── controller/       # 控制器层
│   ├── service/          # 业务逻辑层
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

## 快速开始

### 1. 环境要求
- JDK 17+
- MySQL 8.0+
- Maven 3.8+

### 2. 数据库初始化

```bash
mysql -u root -p < schema.sql
```

### 3. 修改配置

编辑 `src/main/resources/application.yml`，修改数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/data_permission
    username: root
    password: your_password
```

### 4. 编译运行

```bash
mvn clean install
mvn spring-boot:run
```

服务启动后访问：`http://localhost:8080/api/`

## API文档

### 认证接口

#### 登录
```http
POST /api/v1/auth/login
Content-Type: application/json

{
    "username": "admin",
    "password": "123456"
}
```

### 组织管理

#### 获取组织列表
```http
GET /api/v1/organization/list
Authorization: Bearer <token>
```

#### 创建组织
```http
POST /api/v1/organization
Authorization: Bearer <token>
Content-Type: application/json

{
    "orgCode": "NEW_ORG",
    "orgName": "新组织",
    "orgType": "DEPT",
    "parentId": 1,
    "hierarchyLevel": 2
}
```

### 权限模板管理

#### 创建权限模板
```http
POST /api/v1/permission-template
Authorization: Bearer <token>
Content-Type: application/json

{
    "templateCode": "MANAGER_READ",
    "templateName": "经理查看模板",
    "description": "适用于经理岗位的数据查看权限",
    "details": [
        {
            "resourceId": 1,
            "operationType": "READ",
            "fieldLevelMap": {"name":1,"phone":3,"salary":5}
        }
    ]
}
```

### 敏感字段管理

#### 批量创建敏感字段
```http
POST /api/v1/sensitive-field/batch
Authorization: Bearer <token>
Content-Type: application/json

{
    "resourceId": 1,
    "fields": [
        {"fieldName": "phone", "sensitivityLevel": 3, "desensitizationType": "MASK", "maskPattern": "前3后4"},
        {"fieldName": "salary", "sensitivityLevel": 5, "desensitizationType": "MASK", "maskPattern": "只显示万"}
    ]
}
```

### 访问校验（核心API）

#### 权限校验
```http
POST /api/v1/access/check
Authorization: Bearer <token>
Content-Type: application/json

{
    "userId": 123,
    "resourceCode": "employee_table",
    "operationType": "READ",
    "queryConditions": {"orgId": 456},
    "requestedFields": ["name", "salary", "department"]
}
```

#### 响应示例
```json
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
        "maskedFields": [],
        "deniedReason": "部分字段无访问权限",
        "applyUrl": "/permission/apply?resourceCode=employee_table&fields=salary",
        "queryFilters": [
            {"field": "org_id", "operator": "IN", "value": [456, 457, 458]}
        ],
        "executionTime": 45
    }
}
```

### 审计追踪

#### 查询权限变更日志
```http
GET /api/v1/audit/permission-change/page?pageNum=1&pageSize=10
Authorization: Bearer <token>
```

#### 查询访问日志
```http
GET /api/v1/audit/access-log/page?userId=123&accessDecision=DENY
Authorization: Bearer <token>
```

## 配置说明

### JWT配置
```yaml
data-permission:
  jwt:
    secret: your-256-bit-secret-key
    expiration: 86400000  # 毫秒
```

### 脱敏配置
```yaml
data-permission:
  desensitization:
    enabled: true
    default-mask-char: "*"
    mask-rules:
      phone: "前3后4"
      email: "前2后@"
```

### 预警配置
```yaml
data-permission:
  alert:
    download-threshold: 100  # 单日下载超过此数触发预警
    access-frequency-threshold: 500
```

## 测试

```bash
mvn test
```

## License

MIT License
