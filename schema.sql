CREATE DATABASE IF NOT EXISTS `wishes_sender` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `wishes_sender`;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `connections`;
CREATE TABLE `connections` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `name` varchar(100) NOT NULL,
  `relation` varchar(20) NOT NULL,
  `phone` varchar(30) DEFAULT NULL,
  `email` varchar(150) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `gender` varchar(10) DEFAULT NULL,
  `religion` varchar(20) DEFAULT NULL,
  `favourite_dish` varchar(100) DEFAULT NULL,
  `interests` varchar(255) DEFAULT NULL,
  `gift_min` int NOT NULL DEFAULT '0',
  `gift_max` int NOT NULL DEFAULT '5000',
  `notes` text,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_conn_user_name` (`user_id`,`name`),
  KEY `idx_conn_user` (`user_id`),
  KEY `idx_conn_name` (`name`),
  CONSTRAINT `connections_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_conn_gender` CHECK (((`gender` is null) or (`gender` in (_utf8mb4'Female',_utf8mb4'Male',_utf8mb4'Other')))),
  CONSTRAINT `chk_conn_gift_min` CHECK ((`gift_min` >= 0)),
  CONSTRAINT `chk_conn_gift_range` CHECK ((`gift_max` >= `gift_min`)),
  CONSTRAINT `chk_conn_relation` CHECK ((`relation` in (_utf8mb4'Family',_utf8mb4'Friend',_utf8mb4'Partner',_utf8mb4'Colleague',_utf8mb4'Client',_utf8mb4'Customer'))),
  CONSTRAINT `chk_conn_religion` CHECK (((`religion` is null) or (`religion` in (_utf8mb4'Islam',_utf8mb4'Hinduism',_utf8mb4'Christian',_utf8mb4'Buddhism',_utf8mb4'Other'))))
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP TABLE IF EXISTS `event_types`;
CREATE TABLE `event_types` (
  `code` varchar(20) NOT NULL,
  `label` varchar(50) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP TABLE IF EXISTS `events`;
CREATE TABLE `events` (
  `id` int NOT NULL AUTO_INCREMENT,
  `connection_id` int NOT NULL,
  `title` varchar(100) NOT NULL,
  `event_type` varchar(20) NOT NULL,
  `event_date` date NOT NULL,
  `yearly` tinyint(1) NOT NULL DEFAULT '1',
  `description` varchar(255) DEFAULT NULL,
  `birthday_of` int GENERATED ALWAYS AS (if((`event_type` = _utf8mb4'BIRTHDAY'),`connection_id`,NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_one_birthday` (`birthday_of`),
  KEY `connection_id` (`connection_id`),
  KEY `idx_event_date` (`event_date`),
  KEY `fk_events_type` (`event_type`),
  CONSTRAINT `events_ibfk_1` FOREIGN KEY (`connection_id`) REFERENCES `connections` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_events_type` FOREIGN KEY (`event_type`) REFERENCES `event_types` (`code`),
  CONSTRAINT `chk_events_yearly` CHECK ((`yearly` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP TABLE IF EXISTS `festivals`;
CREATE TABLE `festivals` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `festival_date` date NOT NULL,
  `religion` varchar(20) NOT NULL DEFAULT 'All',
  `region` varchar(50) NOT NULL DEFAULT 'Bangladesh',
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_fest_religion` CHECK ((`religion` in (_utf8mb4'All',_utf8mb4'Islam',_utf8mb4'Hinduism',_utf8mb4'Christian',_utf8mb4'Buddhism')))
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP TABLE IF EXISTS `gifts`;
CREATE TABLE `gifts` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `category` varchar(50) NOT NULL,
  `price` int NOT NULL,
  `event_type` varchar(20) DEFAULT NULL,
  `tag` varchar(50) NOT NULL,
  `reason` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_gifts_type` (`event_type`),
  CONSTRAINT `fk_gifts_type` FOREIGN KEY (`event_type`) REFERENCES `event_types` (`code`),
  CONSTRAINT `chk_gift_category` CHECK ((`category` in (_utf8mb4'Books',_utf8mb4'Electronics',_utf8mb4'Home',_utf8mb4'Food',_utf8mb4'Flowers',_utf8mb4'Clothing',_utf8mb4'Experience',_utf8mb4'Travel',_utf8mb4'Art',_utf8mb4'Sports',_utf8mb4'Office',_utf8mb4'Other'))),
  CONSTRAINT `chk_gift_price` CHECK ((`price` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP TABLE IF EXISTS `notifications`;
CREATE TABLE `notifications` (
  `id` int NOT NULL AUTO_INCREMENT,
  `event_id` int NOT NULL,
  `for_date` date NOT NULL,
  `days_before` int NOT NULL,
  `message` varchar(255) NOT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification` (`event_id`,`for_date`,`days_before`),
  CONSTRAINT `notifications_ibfk_2` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_notif_days` CHECK ((`days_before` between 0 and 30)),
  CONSTRAINT `chk_notif_read` CHECK ((`is_read` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=398 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `email` varchar(150) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role` varchar(10) NOT NULL DEFAULT 'USER',
  `ai_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `share_details` tinyint(1) NOT NULL DEFAULT '1',
  `remind_days_before` int NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`),
  CONSTRAINT `chk_users_ai` CHECK ((`ai_enabled` in (0,1))),
  CONSTRAINT `chk_users_remind` CHECK ((`remind_days_before` between 0 and 30)),
  CONSTRAINT `chk_users_role` CHECK ((`role` in (_utf8mb4'USER',_utf8mb4'ADMIN'))),
  CONSTRAINT `chk_users_share` CHECK ((`share_details` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP TABLE IF EXISTS `wish_templates`;
CREATE TABLE `wish_templates` (
  `id` int NOT NULL AUTO_INCREMENT,
  `event_type` varchar(20) NOT NULL,
  `tone` varchar(20) NOT NULL,
  `text` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_tpl_type` (`event_type`),
  CONSTRAINT `fk_tpl_type` FOREIGN KEY (`event_type`) REFERENCES `event_types` (`code`),
  CONSTRAINT `chk_tpl_tone` CHECK ((`tone` in (_utf8mb4'Formal',_utf8mb4'Emotional',_utf8mb4'Funny',_utf8mb4'Romantic')))
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP TABLE IF EXISTS `wishes`;
CREATE TABLE `wishes` (
  `id` int NOT NULL AUTO_INCREMENT,
  `connection_id` int NOT NULL,
  `event_id` int DEFAULT NULL,
  `tone` varchar(20) DEFAULT NULL,
  `language` varchar(20) DEFAULT NULL,
  `text` text NOT NULL,
  `source` varchar(10) NOT NULL,
  `status` varchar(10) NOT NULL DEFAULT 'SAVED',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `connection_id` (`connection_id`),
  KEY `event_id` (`event_id`),
  CONSTRAINT `wishes_ibfk_2` FOREIGN KEY (`connection_id`) REFERENCES `connections` (`id`) ON DELETE CASCADE,
  CONSTRAINT `wishes_ibfk_3` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_wish_language` CHECK (((`language` is null) or (`language` in (_utf8mb4'English',_utf8mb4'Bangla')))),
  CONSTRAINT `chk_wish_source` CHECK ((`source` in (_utf8mb4'AI',_utf8mb4'TEMPLATE'))),
  CONSTRAINT `chk_wish_status` CHECK ((`status` in (_utf8mb4'SAVED',_utf8mb4'SENT'))),
  CONSTRAINT `chk_wish_tone` CHECK (((`tone` is null) or (`tone` in (_utf8mb4'Formal',_utf8mb4'Emotional',_utf8mb4'Funny',_utf8mb4'Romantic'))))
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP VIEW IF EXISTS `v_connections_by_relation`;
CREATE VIEW `v_connections_by_relation` AS select `connections`.`user_id` AS `user_id`,`connections`.`relation` AS `relation`,count(0) AS `total` from `connections` group by `connections`.`user_id`,`connections`.`relation`;

DROP VIEW IF EXISTS `v_event_next`;
CREATE VIEW `v_event_next` AS select `t`.`id` AS `id`,`t`.`connection_id` AS `connection_id`,`t`.`title` AS `title`,`t`.`event_type` AS `event_type`,`t`.`event_date` AS `event_date`,`t`.`yearly` AS `yearly`,`t`.`description` AS `description`,`t`.`user_id` AS `user_id`,`t`.`connection_name` AS `connection_name`,`t`.`relation` AS `relation`,`t`.`next_date` AS `next_date`,(to_days(`t`.`next_date`) - to_days(curdate())) AS `days_left` from (select `e`.`id` AS `id`,`e`.`connection_id` AS `connection_id`,`e`.`title` AS `title`,`e`.`event_type` AS `event_type`,`e`.`event_date` AS `event_date`,`e`.`yearly` AS `yearly`,`e`.`description` AS `description`,`c`.`user_id` AS `user_id`,`c`.`name` AS `connection_name`,`c`.`relation` AS `relation`,(case when (`e`.`yearly` = 1) then (`e`.`event_date` + interval ((year(curdate()) - year(`e`.`event_date`)) + if((date_format(`e`.`event_date`,'%m%d') < date_format(curdate(),'%m%d')),1,0)) year) else `e`.`event_date` end) AS `next_date` from (`events` `e` join `connections` `c` on((`c`.`id` = `e`.`connection_id`)))) `t`;

DROP VIEW IF EXISTS `v_events_per_month`;
CREATE VIEW `v_events_per_month` AS select `c`.`user_id` AS `user_id`,month(`e`.`event_date`) AS `month_no`,count(0) AS `total` from (`events` `e` join `connections` `c` on((`c`.`id` = `e`.`connection_id`))) group by `c`.`user_id`,month(`e`.`event_date`);

DROP VIEW IF EXISTS `v_wishes_by_event_type`;
CREATE VIEW `v_wishes_by_event_type` AS select `c`.`user_id` AS `user_id`,coalesce(`e`.`event_type`,'GENERAL') AS `event_type`,count(0) AS `total` from ((`wishes` `w` join `connections` `c` on((`c`.`id` = `w`.`connection_id`))) left join `events` `e` on((`e`.`id` = `w`.`event_id`))) group by `c`.`user_id`,coalesce(`e`.`event_type`,'GENERAL');

SET FOREIGN_KEY_CHECKS = 1;
