-- DDL for a lightweight task scheduler system

-- 任务日历
DROP TABLE IF EXISTS `task_calendar`;
CREATE TABLE `task_calendar` (
                                 `id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                 `day` int NOT NULL COMMENT '日期yyyyMMdd',
                                 `calendar_group` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '日历分组',
                                 `remark` varchar(64) COLLATE utf8mb4_bin NOT NULL,
                                 `create_date` datetime DEFAULT NULL,
                                 `create_by` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
                                 `update_date` datetime DEFAULT NULL,
                                 `update_by` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_day_group` (`day`,`calendar_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='任务日历';


-- 任务配置
DROP TABLE IF EXISTS `task_config`;
CREATE TABLE `task_config` (
                               `id` int NOT NULL AUTO_INCREMENT COMMENT '任务ID',
                               `task_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '任务分组 每个Java只会加载一个分组的任务',
                               `task_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '任务名称',
                               `task_cron` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '任务CRON表达式',
                               `task_type` int DEFAULT '0' COMMENT '任务类型 0 一般Bean任务 1  工作流任务',
                               `bean_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Bean任务名称',
                               `method_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '方法名称',
                               `bean_parameters` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT 'Bean任务参数 JSON格式',
                               `task_status` int DEFAULT '0' COMMENT '任务状态 0 启用 1 禁用',
                               `start_date` datetime DEFAULT NULL COMMENT '任务生效开始日期',
                               `end_date` datetime DEFAULT NULL COMMENT '任务生效结束日期',
                               `task_calendar_group` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '任务日历分组 关联task_calendar表',
                               `task_exclude_times` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '排除执行时间段 格式 HH:mm-HH:mm,HH:mm-HH:mm',
                               `task_lock_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '任务分布式锁名称',
                               `task_lock_most_seconds` int DEFAULT '0' COMMENT '任务锁最大持有秒数 0 表示不限制',
                               `execute_timeout_seconds` int DEFAULT '0' COMMENT '任务执行超时秒数 0 表示不限制',
                               `execute_persistence` int DEFAULT '0' COMMENT '执行日志是否持久化 0 持久化 1 不持久化',
                               `remark` varchar(256) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
                               `notify_success_user_ids` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '任务成功通知用户ID列表，多个用逗号分隔',
                               `notify_failed_user_ids` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '任务失败通知用户ID列表，多个用逗号分隔',
                               `workflow_nodes` json DEFAULT NULL COMMENT '工作流节点配置 JSON ,来着task_type=0的实例',
                               `workflow_edges` json DEFAULT NULL COMMENT '工作流边配置 JSON',
                               `create_date` datetime DEFAULT NULL,
                               `create_by` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
                               `update_date` datetime DEFAULT NULL,
                               `update_by` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_task_group_name` (`task_group`,`task_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='任务配置';


-- 任务执行日志
DROP TABLE IF EXISTS `task_execute_log`;
CREATE TABLE `task_execute_log` (
                                    `id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                    `task_id` int NOT NULL COMMENT '任务ID',
                                    `task_name` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '任务名称',
                                    `execute_no` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '执行编号',
                                    `task_pattern` varchar(20) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '任务执行方式: MANUAL(手动触发), NORMAL(定时触发), WORKFLOW_STEP(工作流子任务)',
                                    `parent_execute_no` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '父执行编号 (用于工作流子任务)',
                                    `state` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT '执行状态',
                                    `execute_by` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '执行机器实例名称',
                                    `rtn_msg` varchar(4000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '返回信息',
                                    `ex_msg` varchar(4000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '异常信息',
                                    `start_time` datetime DEFAULT NULL COMMENT '开始时间',
                                    `end_time` datetime DEFAULT NULL COMMENT '结束时间',
                                    PRIMARY KEY (`id`),
                                    KEY `idx_task_id` (`task_id`),
                                    KEY `idx_execute_no` (`execute_no`),
                                    KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='任务执行日志';


-- 任务锁
DROP TABLE IF EXISTS `task_lock`;
CREATE TABLE `task_lock` (
                             `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '锁名称',
                             `lock_until` datetime DEFAULT NULL COMMENT '锁有效截止时间',
                             `locked_at` datetime DEFAULT NULL COMMENT '获取锁时间',
                             `locked_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '获取锁的机器实例名称',
                             PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='任务分布式锁';

-- 系统用户表
DROP TABLE IF EXISTS `task_user`;
CREATE TABLE `task_user` (
                            `id` int NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                            `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL UNIQUE COMMENT '用户名',
                            `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '密码 (建议存储哈希值)',
                            `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '邮箱',
                            `phone_number` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '手机号码',
                            `webhook_address` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Webhook地址，可存储JSON数组',
                            `status` int DEFAULT '0' COMMENT '用户状态 0:启用 1:禁用',
                            `create_date` datetime DEFAULT NULL,
                            `create_by` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
                            `update_date` datetime DEFAULT NULL,
                            `update_by` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
                            PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='系统用户表';


-- 初始化一些示例数据 (可选)
INSERT INTO `task_config` (`task_group`, `task_name`, `task_cron`, `task_type`, `bean_name`, `method_name`, `bean_parameters`, `task_status`, `remark`, `create_date`, `create_by`, `update_date`, `update_by`, `notify_success_user_ids`, `notify_failed_user_ids`) VALUES
                                                                                                                                                                                                                                                                         ('default', '示例Bean任务-成功', '0/10 * * * * ?', 0, 'mySampleTask', 'executeSuccess', '{"message": "Hello from Bean Task", "value": 123}', 0, '这是一个每10秒执行一次的成功示例Bean任务', NOW(), 'system', NOW(), 'system', '1', NULL), -- 成功通知用户ID 1 (admin)
                                                                                                                                                                                                                                                                         ('default', '示例Bean任务-失败', '0/15 * * * * ?', 0, 'mySampleTask', 'executeFailed', '{"error": "Simulated error"}', 0, '这是一个每15秒执行一次的失败示例Bean任务', NOW(), 'system', NOW(), 'system', NULL, '1,2'), -- 失败通知用户ID 1和2
                                                                                                                                                                                                                                                                         ('default', '示例工作流任务', '0 0/1 * * * ?', 1, NULL, NULL, NULL, 0, '这是一个每分钟执行一次的工作流任务', NOW(), 'system', NOW(), 'system', '2', '1'); -- 成功通知用户ID 2, 失败通知用户ID 1

-- 插入一些日历数据 (示例：2025年12月25日为圣诞节，属于 holidays 日历组)
INSERT INTO `task_calendar` (`day`, `calendar_group`, `remark`, `create_date`) VALUES
                                                                                   (20251225, 'holidays', '圣诞节', NOW()),
                                                                                   (20250101, 'holidays', '元旦', NOW());

-- 插入一些用户数据
INSERT INTO `task_user` (`username`, `password`, `email`, `phone_number`, `webhook_address`, `status`, `create_date`, `create_by`) VALUES
                                                                                                                                      ('admin', 'admin123', 'admin@example.com', '13800000000', '["http://localhost:8080/mock-webhook-admin"]', 0, NOW(), 'system'),
                                                                                                                                      ('user1', 'user123', 'user1@example.com', '13911112222', '["http://localhost:8080/mock-webhook-user1"]', 0, NOW(), 'system');

