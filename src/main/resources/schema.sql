-- MySQL dump 10.13  Distrib 8.0.19, for Win64 (x86_64)
--
-- Host: 10.118.23.42    Database: jobflow
-- ------------------------------------------------------
-- Server version	8.0.33-v24-txsql-22.4.1-20230926

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
SET @MYSQLDUMP_TEMP_LOG_BIN = @@SESSION.SQL_LOG_BIN;
SET @@SESSION.SQL_LOG_BIN= 0;

--
-- GTID state at the beginning of the backup
--

SET @@GLOBAL.GTID_PURGED=/*!80000 '+'*/ 'ddc540f4-48b5-11ef-88ff-5254003cc63f:1-63584087';

--
-- Table structure for table `task_calendar`
--

DROP TABLE IF EXISTS `task_calendar`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_calendar` (
  `calendar_id` int NOT NULL AUTO_INCREMENT,
  `calendar_name` varchar(255) COLLATE utf8mb4_bin NOT NULL,
  `description` text COLLATE utf8mb4_bin,
  PRIMARY KEY (`calendar_id`),
  UNIQUE KEY `calendar_name` (`calendar_name`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `task_calendar`
--

LOCK TABLES `task_calendar` WRITE;
/*!40000 ALTER TABLE `task_calendar` DISABLE KEYS */;
INSERT INTO `task_calendar` VALUES (1,'NATIONAL_HOLIDAYS','节假日'),(2,'TRADE_FIRST_DAY','第一个星期一'),(3,'TRADE_DAY','交易日'),(4,'TRADE_LAST_DAY','一周最后一个交易日\n');
/*!40000 ALTER TABLE `task_calendar` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `task_calendar_day`
--

DROP TABLE IF EXISTS `task_calendar_day`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_calendar_day` (
  `day_id` int NOT NULL AUTO_INCREMENT,
  `calendar_id` int NOT NULL,
  `event_date` date NOT NULL,
  `is_working_day` tinyint(1) DEFAULT '1',
  `description` text COLLATE utf8mb4_bin,
  PRIMARY KEY (`day_id`),
  UNIQUE KEY `uk_calendar_date` (`calendar_id`,`event_date`),
  CONSTRAINT `task_calendar_day_ibfk_1` FOREIGN KEY (`calendar_id`) REFERENCES `task_calendar` (`calendar_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1205 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `task_calendar_day`
--

LOCK TABLES `task_calendar_day` WRITE;
/*!40000 ALTER TABLE `task_calendar_day` DISABLE KEYS */;
INSERT INTO `task_calendar_day` VALUES (1,1,'2024-01-01',0,'New Year\'s Day'),(2,1,'2024-12-25',0,'Christmas Day'),(514,1,'2025-01-01',1,NULL),(515,1,'2025-01-04',1,NULL),(516,1,'2025-01-05',1,NULL),(517,1,'2025-01-11',1,NULL),(518,1,'2025-01-12',1,NULL),(519,1,'2025-01-18',1,NULL),(520,1,'2025-01-19',1,NULL),(521,1,'2025-01-25',1,NULL),(522,1,'2025-01-26',1,NULL),(523,1,'2025-01-28',1,NULL),(524,1,'2025-01-29',1,NULL),(525,1,'2025-01-30',1,NULL),(526,1,'2025-01-31',1,NULL),(527,1,'2025-02-01',1,NULL),(528,1,'2025-02-02',1,NULL),(529,1,'2025-02-03',1,NULL),(530,1,'2025-02-04',1,NULL),(531,1,'2025-02-08',1,NULL),(532,1,'2025-02-09',1,NULL),(533,1,'2025-02-13',1,NULL),(534,1,'2025-02-15',1,NULL),(535,1,'2025-02-16',1,NULL),(536,1,'2025-02-22',1,NULL),(537,1,'2025-02-23',1,NULL),(538,1,'2025-03-01',1,NULL),(539,1,'2025-03-02',1,NULL),(540,1,'2025-03-08',1,NULL),(541,1,'2025-03-09',1,NULL),(542,1,'2025-03-15',1,NULL),(543,1,'2025-03-16',1,NULL),(544,1,'2025-03-22',1,NULL),(545,1,'2025-03-23',1,NULL),(546,1,'2025-03-29',1,NULL),(547,1,'2025-03-30',1,NULL),(548,1,'2025-04-04',1,NULL),(549,1,'2025-04-05',1,NULL),(550,1,'2025-04-06',1,NULL),(551,1,'2025-04-12',1,NULL),(552,1,'2025-04-13',1,NULL),(553,1,'2025-04-19',1,NULL),(554,1,'2025-04-20',1,NULL),(555,1,'2025-04-26',1,NULL),(556,1,'2025-04-27',1,NULL),(557,1,'2025-05-01',1,NULL),(558,1,'2025-05-02',1,NULL),(559,1,'2025-05-03',1,NULL),(560,1,'2025-05-04',1,NULL),(561,1,'2025-05-05',1,NULL),(562,1,'2025-05-10',1,NULL),(563,1,'2025-05-11',1,NULL),(564,1,'2025-05-17',1,NULL),(565,1,'2025-05-18',1,NULL),(566,1,'2025-05-24',1,NULL),(567,1,'2025-05-25',1,NULL),(568,1,'2025-05-31',1,NULL),(569,1,'2025-06-01',1,NULL),(570,1,'2025-06-02',1,NULL),(571,1,'2025-06-07',1,NULL),(572,1,'2025-06-08',1,NULL),(573,1,'2025-06-14',1,NULL),(574,1,'2025-06-15',1,NULL),(575,1,'2025-06-21',1,NULL),(576,1,'2025-06-22',1,NULL),(577,1,'2025-06-28',1,NULL),(578,1,'2025-06-29',1,NULL),(579,1,'2025-07-05',1,NULL),(580,1,'2025-07-06',1,NULL),(581,1,'2025-07-12',1,NULL),(582,1,'2025-07-13',1,NULL),(583,1,'2025-07-19',1,NULL),(584,1,'2025-07-20',1,NULL),(585,1,'2025-07-26',1,NULL),(586,1,'2025-07-27',1,NULL),(587,1,'2025-08-02',1,NULL),(588,1,'2025-08-03',1,NULL),(589,1,'2025-08-09',1,NULL),(590,1,'2025-08-10',1,NULL),(591,1,'2025-08-16',1,NULL),(592,1,'2025-08-17',1,NULL),(593,1,'2025-08-23',1,NULL),(594,1,'2025-08-24',1,NULL),(595,1,'2025-08-30',1,NULL),(596,1,'2025-08-31',1,NULL),(597,1,'2025-09-06',1,NULL),(598,1,'2025-09-07',1,NULL),(599,1,'2025-09-13',1,NULL),(600,1,'2025-09-14',1,NULL),(601,1,'2025-09-20',1,NULL),(602,1,'2025-09-21',1,NULL),(603,1,'2025-09-27',1,NULL),(604,1,'2025-09-28',1,NULL),(605,1,'2025-10-01',1,NULL),(606,1,'2025-10-02',1,NULL),(607,1,'2025-10-03',1,NULL),(608,1,'2025-10-04',1,NULL),(609,1,'2025-10-05',1,NULL),(610,1,'2025-10-06',1,NULL),(611,1,'2025-10-07',1,NULL),(612,1,'2025-10-08',1,NULL),(613,1,'2025-10-11',1,NULL),(614,1,'2025-10-12',1,NULL),(615,1,'2025-10-18',1,NULL),(616,1,'2025-10-19',1,NULL),(617,1,'2025-10-25',1,NULL),(618,1,'2025-10-26',1,NULL),(619,1,'2025-11-01',1,NULL),(620,1,'2025-11-02',1,NULL),(621,1,'2025-11-08',1,NULL),(622,1,'2025-11-09',1,NULL),(623,1,'2025-11-15',1,NULL),(624,1,'2025-11-16',1,NULL),(625,1,'2025-11-22',1,NULL),(626,1,'2025-11-23',1,NULL),(627,1,'2025-11-29',1,NULL),(628,1,'2025-11-30',1,NULL),(629,1,'2025-12-06',1,NULL),(630,1,'2025-12-07',1,NULL),(631,1,'2025-12-13',1,NULL),(632,1,'2025-12-14',1,NULL),(633,1,'2025-12-20',1,NULL),(634,1,'2025-12-21',1,NULL),(635,1,'2025-12-27',1,NULL),(636,1,'2025-12-28',1,NULL),(641,3,'2025-01-02',1,NULL),(642,3,'2025-01-03',1,NULL),(643,3,'2025-01-06',1,NULL),(644,3,'2025-01-07',1,NULL),(645,3,'2025-01-08',1,NULL),(646,3,'2025-01-09',1,NULL),(647,3,'2025-01-10',1,NULL),(648,3,'2025-01-13',1,NULL),(649,3,'2025-01-14',1,NULL),(650,3,'2025-01-15',1,NULL),(651,3,'2025-01-16',1,NULL),(652,3,'2025-01-17',1,NULL),(653,3,'2025-01-20',1,NULL),(654,3,'2025-01-21',1,NULL),(655,3,'2025-01-22',1,NULL),(656,3,'2025-01-23',1,NULL),(657,3,'2025-01-24',1,NULL),(658,3,'2025-01-27',1,NULL),(659,3,'2025-02-05',1,NULL),(660,3,'2025-02-06',1,NULL),(661,3,'2025-02-07',1,NULL),(662,3,'2025-02-10',1,NULL),(663,3,'2025-02-11',1,NULL),(664,3,'2025-02-12',1,NULL),(665,3,'2025-02-14',1,NULL),(666,3,'2025-02-17',1,NULL),(667,3,'2025-02-18',1,NULL),(668,3,'2025-02-19',1,NULL),(669,3,'2025-02-20',1,NULL),(670,3,'2025-02-21',1,NULL),(671,3,'2025-02-24',1,NULL),(672,3,'2025-02-25',1,NULL),(673,3,'2025-02-26',1,NULL),(674,3,'2025-02-27',1,NULL),(675,3,'2025-02-28',1,NULL),(676,3,'2025-03-03',1,NULL),(677,3,'2025-03-04',1,NULL),(678,3,'2025-03-05',1,NULL),(679,3,'2025-03-06',1,NULL),(680,3,'2025-03-07',1,NULL),(681,3,'2025-03-10',1,NULL),(682,3,'2025-03-11',1,NULL),(683,3,'2025-03-12',1,NULL),(684,3,'2025-03-13',1,NULL),(685,3,'2025-03-14',1,NULL),(686,3,'2025-03-17',1,NULL),(687,3,'2025-03-18',1,NULL),(688,3,'2025-03-19',1,NULL),(689,3,'2025-03-20',1,NULL),(690,3,'2025-03-21',1,NULL),(691,3,'2025-03-24',1,NULL),(692,3,'2025-03-25',1,NULL),(693,3,'2025-03-26',1,NULL),(694,3,'2025-03-27',1,NULL),(695,3,'2025-03-28',1,NULL),(696,3,'2025-03-31',1,NULL),(697,3,'2025-04-01',1,NULL),(698,3,'2025-04-02',1,NULL),(699,3,'2025-04-03',1,NULL),(700,3,'2025-04-07',1,NULL),(701,3,'2025-04-08',1,NULL),(702,3,'2025-04-09',1,NULL),(703,3,'2025-04-10',1,NULL),(704,3,'2025-04-11',1,NULL),(705,3,'2025-04-14',1,NULL),(706,3,'2025-04-15',1,NULL),(707,3,'2025-04-16',1,NULL),(708,3,'2025-04-17',1,NULL),(709,3,'2025-04-18',1,NULL),(710,3,'2025-04-21',1,NULL),(711,3,'2025-04-22',1,NULL),(712,3,'2025-04-23',1,NULL),(713,3,'2025-04-24',1,NULL),(714,3,'2025-04-25',1,NULL),(715,3,'2025-04-28',1,NULL),(716,3,'2025-04-29',1,NULL),(717,3,'2025-04-30',1,NULL),(718,3,'2025-05-06',1,NULL),(719,3,'2025-05-07',1,NULL),(720,3,'2025-05-08',1,NULL),(721,3,'2025-05-09',1,NULL),(722,3,'2025-05-12',1,NULL),(723,3,'2025-05-13',1,NULL),(724,3,'2025-05-14',1,NULL),(725,3,'2025-05-15',1,NULL),(726,3,'2025-05-16',1,NULL),(727,3,'2025-05-19',1,NULL),(728,3,'2025-05-20',1,NULL),(729,3,'2025-05-21',1,NULL),(730,3,'2025-05-22',1,NULL),(731,3,'2025-05-23',1,NULL),(732,3,'2025-05-26',1,NULL),(733,3,'2025-05-27',1,NULL),(734,3,'2025-05-28',1,NULL),(735,3,'2025-05-29',1,NULL),(736,3,'2025-05-30',1,NULL),(737,3,'2025-06-03',1,NULL),(738,3,'2025-06-04',1,NULL),(739,3,'2025-06-05',1,NULL),(740,3,'2025-06-06',1,NULL),(741,3,'2025-06-09',1,NULL),(742,3,'2025-06-10',1,NULL),(743,3,'2025-06-11',1,NULL),(744,3,'2025-06-12',1,NULL),(745,3,'2025-06-13',1,NULL),(746,3,'2025-06-16',1,NULL),(747,3,'2025-06-17',1,NULL),(748,3,'2025-06-18',1,NULL),(749,3,'2025-06-19',1,NULL),(750,3,'2025-06-20',1,NULL),(751,3,'2025-06-23',1,NULL),(752,3,'2025-06-24',1,NULL),(753,3,'2025-06-25',1,NULL),(754,3,'2025-06-26',1,NULL),(755,3,'2025-06-27',1,NULL),(756,3,'2025-06-30',1,NULL),(757,3,'2025-07-01',1,NULL),(758,3,'2025-07-02',1,NULL),(759,3,'2025-07-03',1,NULL),(760,3,'2025-07-04',1,NULL),(761,3,'2025-07-07',1,NULL),(762,3,'2025-07-08',1,NULL),(763,3,'2025-07-09',1,NULL),(764,3,'2025-07-10',1,NULL),(765,3,'2025-07-11',1,NULL),(766,3,'2025-07-14',1,NULL),(767,3,'2025-07-15',1,NULL),(768,3,'2025-07-16',1,NULL),(769,3,'2025-07-17',1,NULL),(770,3,'2025-07-18',1,NULL),(771,3,'2025-07-21',1,NULL),(772,3,'2025-07-22',1,NULL),(773,3,'2025-07-23',1,NULL),(774,3,'2025-07-24',1,NULL),(775,3,'2025-07-25',1,NULL),(776,3,'2025-07-28',1,NULL),(777,3,'2025-07-29',1,NULL),(778,3,'2025-07-30',1,NULL),(779,3,'2025-07-31',1,NULL),(780,3,'2025-08-01',1,NULL),(781,3,'2025-08-04',1,NULL),(782,3,'2025-08-05',1,NULL),(783,3,'2025-08-06',1,NULL),(784,3,'2025-08-07',1,NULL),(785,3,'2025-08-08',1,NULL),(786,3,'2025-08-11',1,NULL),(787,3,'2025-08-12',1,NULL),(788,3,'2025-08-13',1,NULL),(789,3,'2025-08-14',1,NULL),(790,3,'2025-08-15',1,NULL),(791,3,'2025-08-18',1,NULL),(792,3,'2025-08-19',1,NULL),(793,3,'2025-08-20',1,NULL),(794,3,'2025-08-21',1,NULL),(795,3,'2025-08-22',1,NULL),(796,3,'2025-08-25',1,NULL),(797,3,'2025-08-26',1,NULL),(798,3,'2025-08-27',1,NULL),(799,3,'2025-08-28',1,NULL),(800,3,'2025-08-29',1,NULL),(801,3,'2025-09-01',1,NULL),(802,3,'2025-09-02',1,NULL),(803,3,'2025-09-03',1,NULL),(804,3,'2025-09-04',1,NULL),(805,3,'2025-09-05',1,NULL),(806,3,'2025-09-08',1,NULL),(807,3,'2025-09-09',1,NULL),(808,3,'2025-09-10',1,NULL),(809,3,'2025-09-11',1,NULL),(810,3,'2025-09-12',1,NULL),(811,3,'2025-09-15',1,NULL),(812,3,'2025-09-16',1,NULL),(813,3,'2025-09-17',1,NULL),(814,3,'2025-09-18',1,NULL),(815,3,'2025-09-19',1,NULL),(816,3,'2025-09-22',1,NULL),(817,3,'2025-09-23',1,NULL),(818,3,'2025-09-24',1,NULL),(819,3,'2025-09-25',1,NULL),(820,3,'2025-09-26',1,NULL),(821,3,'2025-09-29',1,NULL),(822,3,'2025-09-30',1,NULL),(823,3,'2025-10-09',1,NULL),(824,3,'2025-10-10',1,NULL),(825,3,'2025-10-13',1,NULL),(826,3,'2025-10-14',1,NULL),(827,3,'2025-10-15',1,NULL),(828,3,'2025-10-16',1,NULL),(829,3,'2025-10-17',1,NULL),(830,3,'2025-10-20',1,NULL),(831,3,'2025-10-21',1,NULL),(832,3,'2025-10-22',1,NULL),(833,3,'2025-10-23',1,NULL),(834,3,'2025-10-24',1,NULL),(835,3,'2025-10-27',1,NULL),(836,3,'2025-10-28',1,NULL),(837,3,'2025-10-29',1,NULL),(838,3,'2025-10-30',1,NULL),(839,3,'2025-10-31',1,NULL),(840,3,'2025-11-03',1,NULL),(841,3,'2025-11-04',1,NULL),(842,3,'2025-11-05',1,NULL),(843,3,'2025-11-06',1,NULL),(844,3,'2025-11-07',1,NULL),(845,3,'2025-11-10',1,NULL),(846,3,'2025-11-11',1,NULL),(847,3,'2025-11-12',1,NULL),(848,3,'2025-11-13',1,NULL),(849,3,'2025-11-14',1,NULL),(850,3,'2025-11-17',1,NULL),(851,3,'2025-11-18',1,NULL),(852,3,'2025-11-19',1,NULL),(853,3,'2025-11-20',1,NULL),(854,3,'2025-11-21',1,NULL),(855,3,'2025-11-24',1,NULL),(856,3,'2025-11-25',1,NULL),(857,3,'2025-11-26',1,NULL),(858,3,'2025-11-27',1,NULL),(859,3,'2025-11-28',1,NULL),(860,3,'2025-12-01',1,NULL),(861,3,'2025-12-02',1,NULL),(862,3,'2025-12-03',1,NULL),(863,3,'2025-12-04',1,NULL),(864,3,'2025-12-05',1,NULL),(865,3,'2025-12-08',1,NULL),(866,3,'2025-12-09',1,NULL),(867,3,'2025-12-10',1,NULL),(868,3,'2025-12-11',1,NULL),(869,3,'2025-12-12',1,NULL),(870,3,'2025-12-15',1,NULL),(871,3,'2025-12-16',1,NULL),(872,3,'2025-12-17',1,NULL),(873,3,'2025-12-18',1,NULL),(874,3,'2025-12-19',1,NULL),(875,3,'2025-12-22',1,NULL),(876,3,'2025-12-23',1,NULL),(877,3,'2025-12-24',1,NULL),(878,3,'2025-12-25',1,NULL),(879,3,'2025-12-26',1,NULL),(880,3,'2025-12-29',1,NULL),(881,3,'2025-12-30',1,NULL),(882,3,'2025-12-31',1,NULL),(883,3,'2026-01-01',1,NULL),(1151,2,'2025-01-02',1,NULL),(1152,2,'2025-01-06',1,NULL),(1153,2,'2025-03-10',1,NULL),(1154,2,'2025-03-17',1,NULL),(1155,2,'2025-03-24',1,NULL),(1156,2,'2025-03-31',1,NULL),(1157,2,'2025-04-07',1,NULL),(1158,2,'2025-04-14',1,NULL),(1159,2,'2025-04-21',1,NULL),(1160,2,'2025-04-28',1,NULL),(1161,2,'2025-05-06',1,NULL),(1162,2,'2025-05-12',1,NULL),(1163,2,'2025-01-13',1,NULL),(1164,2,'2025-05-19',1,NULL),(1165,2,'2025-05-26',1,NULL),(1166,2,'2025-06-03',1,NULL),(1167,2,'2025-06-09',1,NULL),(1168,2,'2025-06-16',1,NULL),(1169,2,'2025-06-23',1,NULL),(1170,2,'2025-06-30',1,NULL),(1171,2,'2025-07-07',1,NULL),(1172,2,'2025-07-14',1,NULL),(1173,2,'2025-07-21',1,NULL),(1174,2,'2025-01-20',1,NULL),(1175,2,'2025-07-28',1,NULL),(1176,2,'2025-08-04',1,NULL),(1177,2,'2025-08-11',1,NULL),(1178,2,'2025-08-18',1,NULL),(1179,2,'2025-08-25',1,NULL),(1180,2,'2025-09-01',1,NULL),(1181,2,'2025-09-08',1,NULL),(1182,2,'2025-09-15',1,NULL),(1183,2,'2025-09-22',1,NULL),(1184,2,'2025-09-29',1,NULL),(1185,2,'2025-01-27',1,NULL),(1186,2,'2025-10-09',1,NULL),(1187,2,'2025-10-13',1,NULL),(1188,2,'2025-10-20',1,NULL),(1189,2,'2025-10-27',1,NULL),(1190,2,'2025-11-03',1,NULL),(1191,2,'2025-11-10',1,NULL),(1192,2,'2025-11-17',1,NULL),(1193,2,'2025-11-24',1,NULL),(1194,2,'2025-12-01',1,NULL),(1195,2,'2025-12-08',1,NULL),(1196,2,'2025-02-05',1,NULL),(1197,2,'2025-12-15',1,NULL),(1198,2,'2025-12-22',1,NULL),(1199,2,'2025-12-29',1,NULL),(1200,2,'2025-02-10',1,NULL),(1201,2,'2025-02-17',1,NULL),(1202,2,'2025-02-24',1,NULL),(1203,2,'2025-03-03',1,NULL),(1204,2,'2026-01-01',1,NULL);
/*!40000 ALTER TABLE `task_calendar_day` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `task_config`
--

DROP TABLE IF EXISTS `task_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_config` (
  `task_id` int NOT NULL AUTO_INCREMENT,
  `task_name` varchar(255) COLLATE utf8mb4_bin NOT NULL,
  `task_group` varchar(255) COLLATE utf8mb4_bin NOT NULL,
  `cron_expression` varchar(255) COLLATE utf8mb4_bin NOT NULL,
  `task_type` int NOT NULL COMMENT '0: Bean task, 1: (Legacy/Unused), 2: HTTP task, 4: Shell script task, 10: Workflow task',
  `bean_name` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `method_name` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `parameters` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,
  `task_calendar_group` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `task_exclude_times` text COLLATE utf8mb4_bin,
  `start_date` datetime DEFAULT NULL,
  `end_date` datetime DEFAULT NULL,
  `execute_timeout_seconds` int DEFAULT '0',
  `notify_success_user_ids` varchar(1024) COLLATE utf8mb4_bin DEFAULT NULL,
  `notify_failed_user_ids` varchar(1024) COLLATE utf8mb4_bin DEFAULT NULL,
  `description` text COLLATE utf8mb4_bin,
  `is_active` tinyint(1) DEFAULT '1',
  `execution_mode` varchar(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'BROADCAST' COMMENT 'Execution mode: BROADCAST or CLUSTER',
  `max_retry_attempts` int DEFAULT '0' COMMENT 'Maximum number of retry attempts upon failure (0 means no retries)',
  `retry_interval_seconds` int DEFAULT '30' COMMENT 'Interval in seconds between retry attempts',
  `retry_Interval_multiplier` int DEFAULT '1' COMMENT 'Interval in seconds between retry attempts',
  `workflow_nodes` text COLLATE utf8mb4_bin,
  `workflow_edges` text COLLATE utf8mb4_bin,
  `global_parameters` text COLLATE utf8mb4_bin,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_task_group_name` (`task_group`,`task_name`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `task_config`
--

LOCK TABLES `task_config` WRITE;
/*!40000 ALTER TABLE `task_config` DISABLE KEYS */;
INSERT INTO `task_config` VALUES (1,'MySampleSuccessTask','DEFAULT','0 0/10 * * * ?',0,'mySampleTask','executeSuccess','{\n  \"message\": \"Hello from scheduler!\",\n  \"value\": 123\n}',NULL,NULL,NULL,NULL,0,'1,2','1,2','A sample task that should succeed.',0,'BROADCAST',0,30,1,NULL,NULL,NULL,'2025-06-10 02:40:35','2025-10-16 00:47:24'),(2,'MySampleFailedTask','DEFAULT','0 0/6 * * * ?',0,'mySampleTask','executeFailed','{\n  \"error\": \"Simulated failure\"\n}',NULL,NULL,'2023-01-01 00:01:00','2025-10-31 23:04:00',30,'1','1','A sample task that is expected to fail.',1,'BROADCAST',3,60,1,NULL,NULL,NULL,'2025-06-10 02:40:35','2025-10-16 05:07:41'),(3,'MyClusteredTask','DEFAULT','0 0/12 * * *  ?',0,'mySampleTask','simpleExecute','{}',NULL,NULL,NULL,NULL,0,NULL,NULL,'A sample task that runs in CLUSTER mode.',0,'CLUSTER',0,30,1,NULL,NULL,NULL,'2025-06-10 02:40:35','2025-10-16 00:49:34'),(4,'MyInactiveTask','DEFAULT','0 0 0 1 1 ?',0,'mySampleTask','executeSuccess','{\"message\":\"This should not run\", \"value\": 0}',NULL,NULL,NULL,NULL,0,NULL,NULL,'An inactive sample task.',0,'BROADCAST',0,30,1,NULL,NULL,NULL,'2025-06-10 02:40:35','2025-09-30 02:17:20'),(6,'SampleHttpTask','DEFAULT','0 0 2 * * ?',2,NULL,NULL,'{\"url\":\"https://jsonplaceholder.typicode.com/todos/1\",\"method\":\"GET\",\"headers\":{\"X-Custom\":\"Test\"},\"body\":null,\"connectTimeout\":5000,\"readTimeout\":10000,\"retryCount\":0,\"successCode\":\"200\"}',NULL,NULL,'2025-06-24 10:31:00',NULL,0,'1','1','A sample HTTP GET task.',0,'BROADCAST',2,45,1,NULL,NULL,NULL,'2025-06-10 02:40:35','2025-10-16 00:47:24'),(7,'SampleShellScriptByPath','DEFAULT','0 0 3 * * ?',4,NULL,NULL,'{\"script\":\"/opt/scripts/my_script.sh\",\"isInlineScript\":false,\"arguments\":[\"param1\",\"param2\"],\"workingDirectory\":\"/opt/scripts\"}',NULL,NULL,NULL,NULL,0,'1','1','A sample path-based Shell script task.',0,'BROADCAST',0,30,1,NULL,NULL,NULL,'2025-06-10 02:40:35','2025-10-16 00:47:24'),(8,'SampleInlineShellScript','DEFAULT','0 0 4 * * ?',4,NULL,NULL,'{\"script\":\"#!/bin/bash\\necho \\\"Hello from Inline Shell Task! Argument: $1\\\"; date\\necho PWD is $(pwd)\\necho USER is $(whoami)\",\"isInlineScript\":true,\"arguments\":[\"InlineArg\"],\"workingDirectory\":\"/tmp\"}',NULL,NULL,NULL,NULL,0,'1','1','A sample inline Shell script task.',0,'BROADCAST',0,30,1,NULL,NULL,NULL,'2025-06-10 02:40:35','2025-10-16 00:47:24'),(15,'简单任务1','DEFAULT','',0,'mySampleTask','executeSuccess','{\n\"message\":\"简单任务1\"\n}',NULL,NULL,NULL,NULL,0,NULL,NULL,'',1,'BROADCAST',0,30,1,NULL,NULL,NULL,'2025-09-30 07:27:45','2025-09-30 07:29:57'),(16,'简单任务2','DEFAULT','',0,'mySampleTask','simpleExecute','{\n  \"name\": \"简单爱1\"\n}',NULL,NULL,NULL,NULL,0,NULL,NULL,'',1,'BROADCAST',0,30,1,NULL,NULL,NULL,'2025-09-30 07:32:12','2025-10-14 03:35:58'),(17,'简单工作流1','DEFAULT','0 0/5 * * * ?',10,NULL,NULL,NULL,NULL,NULL,'2025-09-30 07:38:00','2025-11-01 07:40:00',0,'1,2,3','1,2,3','',1,'CLUSTER',0,30,1,'[{\"id\":\"process-1\",\"nodeId\":\"process-1\",\"nodeName\":\"Node 1 (process)\",\"parameters\":{\"message\":\"我是爸爸\"},\"taskConfigId\":15,\"type\":\"process\",\"x\":50.0,\"y\":50.0},{\"id\":\"process-2\",\"nodeId\":\"process-2\",\"nodeName\":\"Node 2 (process)\",\"parameters\":{\"message\":\"我是妈妈\"},\"taskConfigId\":15,\"type\":\"process\",\"x\":51.48504205962479,\"y\":217.780896210464},{\"id\":\"process-3\",\"nodeId\":\"process-3\",\"nodeName\":\"Node 3 (process)\",\"parameters\":{\"id\":99,\"message\":\"我是我NODE3\"},\"taskConfigId\":16,\"type\":\"process\",\"x\":474.4437812262587,\"y\":121.76170862013817},{\"id\":\"process-4\",\"nodeId\":\"process-4\",\"nodeName\":\"Node 4 (process)\",\"parameters\":{\"message\":\"我是最后一个节点\"},\"taskConfigId\":16,\"type\":\"process\",\"x\":813.01377366257,\"y\":123.77757464570271},{\"id\":\"process-5\",\"nodeId\":\"process-5\",\"nodeName\":\"Node 5 (process)\",\"parameters\":{\"message\":\"BYEBYE\"},\"taskConfigId\":15,\"type\":\"process\",\"x\":911.1314258793946,\"y\":356.35235148920776}]','[{\"condition\":\"\",\"expression\":\"\",\"fromNodeId\":\"process-1\",\"id\":\"edge-1\",\"priority\":1,\"toNodeId\":\"process-3\"},{\"condition\":\"\",\"expression\":\"\",\"fromNodeId\":\"process-2\",\"id\":\"edge-2\",\"priority\":1,\"toNodeId\":\"process-3\"},{\"condition\":\"\",\"expression\":\"\",\"fromNodeId\":\"process-3\",\"id\":\"edge-3\",\"priority\":1,\"toNodeId\":\"process-4\"},{\"condition\":\"\",\"expression\":\"\",\"fromNodeId\":\"process-4\",\"id\":\"edge-4\",\"priority\":1,\"toNodeId\":\"process-5\"}]',NULL,'2025-09-30 07:35:13','2025-10-16 02:35:12');
/*!40000 ALTER TABLE `task_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `task_execute_log`
--

DROP TABLE IF EXISTS `task_execute_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_execute_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` int NOT NULL,
  `workflow_id` int DEFAULT NULL,
  `workflow_instance_id` bigint DEFAULT NULL,
  `workflow_node_id` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `instance_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `parent_log_id` int DEFAULT NULL,
  `start_time` timestamp NOT NULL,
  `end_time` timestamp NULL DEFAULT NULL,
  `state` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL,
  `task_pattern` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL,
  `rtn_msg` varchar(2000) COLLATE utf8mb4_bin DEFAULT NULL,
  `attempt_number` int  NULL,
  `ex_msg` text COLLATE utf8mb4_bin,
  `parameters` varchar(4000) COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`log_id`),
  UNIQUE KEY `uk_instance_node` (`workflow_instance_id`,`workflow_node_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_instance_id` (`workflow_instance_id`),
  KEY `idx_parent_execute_no` (`parent_log_id`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `task_execute_log`
--

LOCK TABLES `task_execute_log` WRITE;
/*!40000 ALTER TABLE `task_execute_log` DISABLE KEYS */;
INSERT INTO `task_execute_log` VALUES (1,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',47,'2025-10-17 02:24:30','2025-10-17 02:24:30','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(2,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',48,'2025-10-17 02:24:34','2025-10-17 02:24:33','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(3,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',49,'2025-10-17 02:24:40','2025-10-17 02:24:39','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(4,17,NULL,4,NULL,'DESKTOP-AS6KVST:10.124.199.16',NULL,'2025-10-17 02:25:00','2025-10-17 02:25:04','FAILED','WORKFLOW_PARENT','Node process-4 failed. [Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: Simulated failure: 100]',NULL,NULL),(5,15,17,4,'process-1','DESKTOP-AS6KVST:10.124.199.16',4,'2025-10-17 02:25:01','2025-10-17 02:25:01','SUCCESS','WORKFLOW_STEP','{message=我是爸爸, status=success}',NULL,'{\"message\":\"我是爸爸\"}'),(6,15,17,4,'process-2','DESKTOP-AS6KVST:10.124.199.16',4,'2025-10-17 02:25:01','2025-10-17 02:25:02','SUCCESS','WORKFLOW_STEP','{message=我是妈妈, status=success}',NULL,'{\"message\":\"我是妈妈\"}'),(7,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',1,'2025-10-17 02:25:01','2025-10-17 02:25:01','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(8,16,17,4,'process-3','DESKTOP-AS6KVST:10.124.199.16',4,'2025-10-17 02:25:03','2025-10-17 02:25:03','SUCCESS','WORKFLOW_STEP','{name=simpleExecute, id=99, message=我是我NODE3, status=success}',NULL,'{\"id\":99,\"message\":\"我是我NODE3\"}'),(9,16,17,4,'process-4','DESKTOP-AS6KVST:10.124.199.16',4,'2025-10-17 02:25:04','2025-10-17 02:25:03','FAILED','WORKFLOW_STEP',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: Simulated failure: 100','{\"message\":\"我是最后一个节点\"}'),(10,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',2,'2025-10-17 02:25:04','2025-10-17 02:25:04','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(11,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',3,'2025-10-17 02:25:10','2025-10-17 02:25:10','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(12,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',7,'2025-10-17 02:25:32','2025-10-17 02:25:31','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(13,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',10,'2025-10-17 02:25:35','2025-10-17 02:25:34','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(14,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',11,'2025-10-17 02:25:41','2025-10-17 02:25:40','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(15,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',12,'2025-10-17 02:26:02','2025-10-17 02:26:01','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(16,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',13,'2025-10-17 02:26:05','2025-10-17 02:26:04','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(17,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',14,'2025-10-17 02:26:11','2025-10-17 02:26:10','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(18,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',15,'2025-10-17 02:26:32','2025-10-17 02:26:31','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(19,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',16,'2025-10-17 02:26:36','2025-10-17 02:26:35','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(20,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',17,'2025-10-17 02:26:41','2025-10-17 02:26:41','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(21,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',18,'2025-10-17 02:27:02','2025-10-17 02:27:02','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(22,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',19,'2025-10-17 02:27:06','2025-10-17 02:27:05','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(23,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',20,'2025-10-17 02:27:12','2025-10-17 02:27:11','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(24,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',21,'2025-10-17 02:27:33','2025-10-17 02:27:33','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(25,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',22,'2025-10-17 02:27:37','2025-10-17 02:27:37','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(26,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',23,'2025-10-17 02:27:42','2025-10-17 02:27:42','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(27,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',24,'2025-10-17 02:28:04','2025-10-17 02:28:03','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(28,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',25,'2025-10-17 02:28:08','2025-10-17 02:28:07','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(29,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',26,'2025-10-17 02:28:13','2025-10-17 02:28:13','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(30,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',27,'2025-10-17 02:28:34','2025-10-17 02:28:34','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(31,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',28,'2025-10-17 02:28:38','2025-10-17 02:28:37','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(32,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',29,'2025-10-17 02:28:44','2025-10-17 02:28:44','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(33,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',30,'2025-10-17 02:29:05','2025-10-17 02:29:04','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(34,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',31,'2025-10-17 02:29:08','2025-10-17 02:29:08','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}'),(35,2,NULL,NULL,NULL,'DESKTOP-AS6KVST:10.124.199.16',32,'2025-10-17 02:29:15','2025-10-17 02:29:14','FAILED','RETRY',NULL,'Bean execution failed: Error executing task method [mySampleTask]; nested exception is java.lang.IllegalArgumentException: [Simulated failure: Simulated failure]','{\n  \"error\": \"Simulated failure\"\n}');
/*!40000 ALTER TABLE `task_execute_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `task_lock`
--

DROP TABLE IF EXISTS `task_lock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_lock` (
  `lock_name` varchar(255) COLLATE utf8mb4_bin NOT NULL,
  `owner_instance_id` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `lock_acquired_time` timestamp NULL DEFAULT NULL,
  `least_duration_seconds` int DEFAULT NULL,
  `version` int DEFAULT NULL,
  PRIMARY KEY (`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `task_lock`
--

LOCK TABLES `task_lock` WRITE;
/*!40000 ALTER TABLE `task_lock` DISABLE KEYS */;
INSERT INTO `task_lock` VALUES ('GLOBAL_SCHEDULER_LOCK',NULL,NULL,60000,0),('task-execution-17',NULL,NULL,NULL,5143),('task_lock_id_12',NULL,NULL,NULL,164),('task_lock_id_14',NULL,NULL,NULL,2102),('task_lock_id_17',NULL,NULL,NULL,3188),('task_lock_id_3',NULL,NULL,NULL,164694);
/*!40000 ALTER TABLE `task_lock` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `task_user`
--

DROP TABLE IF EXISTS `task_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_user` (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_bin NOT NULL,
  `email` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `webhook_address` varchar(1024) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Webhook URL for user notifications. Can be a single URL or a JSON array of URLs.',
  `notification_preferences_json` text COLLATE utf8mb4_bin COMMENT 'User notification preferences as JSON',
  `is_admin` tinyint(1) DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `task_user`
--

LOCK TABLES `task_user` WRITE;
/*!40000 ALTER TABLE `task_user` DISABLE KEYS */;
INSERT INTO `task_user` VALUES (1,'admin','1eb1afa20dc454d6ef3b6dc6abcbd7dca7e519b698fdf073f4625ded09d74807','admin@example.com','',NULL,1,'2025-06-10 02:40:35'),(2,'user1','1eb1afa20dc454d6ef3b6dc6abcbd7dca7e519b698fdf073f4625ded09d74807','user1@example.com','',NULL,0,'2025-06-10 02:40:35'),(3,'admin02','d9b4326b3c0958824fdfdaf3045a5a1eee0280c6d20a71bac98730b632594482','admin02@163.com','',NULL,1,'2025-06-23 09:20:28');
/*!40000 ALTER TABLE `task_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `task_workflow_node_state`
--

DROP TABLE IF EXISTS `task_workflow_node_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_workflow_node_state` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `workflow_instance_id` bigint NOT NULL,
  `node_id` varchar(255) COLLATE utf8mb4_bin NOT NULL,
  `pending_parents` int NOT NULL,
  `status` varchar(50) COLLATE utf8mb4_bin NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instance_node` (`workflow_instance_id`,`node_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `task_workflow_node_state`
--

LOCK TABLES `task_workflow_node_state` WRITE;
/*!40000 ALTER TABLE `task_workflow_node_state` DISABLE KEYS */;
INSERT INTO `task_workflow_node_state` VALUES (1,4,'process-1',0,'SUCCESS'),(2,4,'process-2',0,'SUCCESS'),(3,4,'process-3',0,'SUCCESS'),(4,4,'process-4',0,'FAILED'),(5,4,'process-5',1,'PENDING');
/*!40000 ALTER TABLE `task_workflow_node_state` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'jobflow'
--
SET @@SESSION.SQL_LOG_BIN = @MYSQLDUMP_TEMP_LOG_BIN;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-10-17 10:29:33
