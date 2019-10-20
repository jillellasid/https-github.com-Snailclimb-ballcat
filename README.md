# BallCat

## 前言

`BallCat` 致力于简化开发流程，开箱即用，只需专注于业务开发，避免重复劳作


## 简介
基于`SpringBoot` + `Ant Design Vue`的前后端分离应用。  
目前支持代码生成，前后台用户分离，权限控制，定时任务，访问日志，操作日志，异常日志，统一异常处理，XSS过滤，SQL防注入等功能


## 结构

- 后端：

```
ballcat
|-- ballcat-admin   -- 后台管理项目
|-- ballcat-api     -- 前台接口，业务接口
|-- ballcat-codegen    -- 代码生成器
|-- ballcat-common      -- 公用模块
|   |-- ballcat-common-conf  -- web公用配置
|   |-- ballcat-common-core  -- 核心的工具类
|   |-- ballcat-common-job   -- 公用的job配置
|   |-- ballcat-common-log   -- 公用的日志依赖
|   `-- ballcat-common-modules  -- 业务模块
|-- ballcat-job      -- 任务调度中心（xxl-job-admin）
`-- doc              -- 初始化数据库脚本
```

- 前端：

```
.
|-- public   -- 依赖的静态资源存放
`-- src           
    |-- api      -- 和服务端交互的请求方法
    |-- assets   --  本地静态资源
    |-- ballcat   --  项目定制css和常量
    |-- components  -- 通用组件
    |-- config     -- 框架配置
    |-- core       -- 项目引导, 全局配置初始化，依赖包引入等
    |-- layouts    -- 布局
    |-- locales    -- 国际化
    |-- mixins     -- 增删改查页面的抽取模板
    |-- router     -- 路由相关
    |-- store      -- 数据存储相关
    |-- utils      -- 工具类
    |-- views      -- 页面
    |-- App.Vue    -- Vue 模板入口
    |-- main.js    -- Vue 入口js
    `-- permission.js   -- 路由守卫 权限控制
    
```

## 依赖

- 后端

依赖 |	版本 | 官网
--- | --- | ---
Spring Boot|	2.1.8.RELEASE| https://spring.io/projects/spring-boot#learn
Spring Security OAuth2|	2.3.6.RELEASE| https://spring.io/projects/spring-security-oauth
Mybatis Plus|	3.2.0|  https://mp.baomidou.com/
XXL-JOB| 2.1.0 | http://www.xuxueli.com/xxl-job
Hutool|	5.0.1| https://www.hutool.cn/


- 前端

依赖 |	版本 | 官网
--- | --- | ---
Vue| 2.6.10 | https://cn.vuejs.org/
Ant Design Vue|	1.4.1| https://www.antdv.com
Ant Design Vue Pro|	2.0.2| https://pro.loacg.com/


# 快速开始

开始之前，请先确保您已经配置好以下环境

名称  |  版本 |  
---| --- 
JDK	| 1.8	| 
MySQL	| 5.7.8 +	
Redis	| 3.2 +| 
node	| 10.0 +	
npm	| 6.0 +	| 

**另：请在您的开发工具中安装好 `Lombok` 插件** 

## 代码下载

- 后端：
> git clone https://github.com/Hccake/ballcat.git


- 前端：
> git clone https://github.com/Hccake/ballcat-ui-vue.git


## 数据库配置

版本： mysql5.7.8+  
默认字符集：utf8mb4  
默认排序规则：utf8mb4_general_ci  

- 按下面顺序依次执行/docs目录下的数据库脚本
```sql
# 建库语句
scheme.sql   
# 核心库
2ballcat.sql  
# 定时任务依赖库（可选）
ballcat_job.sql  
```

## 配置本地hosts

建议使用 switchHost 软件管理hosts配置!  

也可直接修改本地host文件:  
windows系统下host文件位于
`C:\Windows\System32\drivers\etc\hosts`


**新增如下host:**
```
127.0.0.1 ballcat-mysql
127.0.0.1 ballcat-redis
127.0.0.1 ballcat-job
127.0.0.1 ballcat-admin
```
其中`127.0.0.1`按需替换成开发环境ip

## 项目配置修改

- `ballcat-admin`项目下的`src\main\resources\application-dev.yml`

    修改数据库账号密码，以及redis密码，若未配置redis密码，则直接留空
    
    ```yaml
    spring:
      datasource:
        url: jdbc:mysql://ballcat-mysql:3306/ballcat?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
        username: root
        password: '123456'
      redis:
        host: ballcat-redis
        password: ''
        port: 6379
    ```  

**请尽量使用host域名形式来配置链接地址，而非直接使用ip**

## 启动项目

- 后端

直接执行`ballcat-admin`项目下的`AdminApplication`类的main函数即可。  
更多启动项目的方法，请自行查阅spring-boot的多种启动方式

- 前端

打开命令行进入项目根目录
或 在ide提供的命令行工具中执行如下语句

```
# 安装依赖
yarn install
# 启动服务
yarn run serve
```
or
```
# 安装依赖
npm install
# 启动服务
npm run serve
```

## 访问项目

默认前端项目路径：[http://localhost:8000/](http://localhost:8000/)

默认用户名密码：admin/a123456


