-- MySQL dump 10.13  Distrib 8.0.29, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: engine
-- ------------------------------------------------------
-- Server version	8.0.26

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `sites`
--

DROP TABLE IF EXISTS `sites`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sites` (
  `sites_id` int NOT NULL AUTO_INCREMENT,
  `status` enum('INDEXING','INDEXED','FAILED') DEFAULT NULL,
  `status_time` datetime DEFAULT NULL,
  `last_error` text,
  `url` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`sites_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sites`
--

LOCK TABLES `sites` WRITE;
/*!40000 ALTER TABLE `sites` DISABLE KEYS */;
INSERT INTO `sites` VALUES (1,'INDEXED','2022-07-27 21:50:41','NULL','http://radimov.ru','radimov.ru is for sale'),(2,'INDEXED','2022-07-27 22:11:32','Ошибка индексации: страница https://www.svetlovka.ru/upload/resize_cache/iblock/af0/100_50_1/af00db177b0deea4ead47267c80e431c.JPG не доступна','https://www.svetlovka.ru','Центральная городская молодежная библиотека им. М. А. Светлова'),(3,'INDEXED','2022-07-27 22:27:31','NULL','http://www.playback.ru','Интернет-магазин PlayBack.ru'),(4,'INDEXED','2022-07-27 22:35:25','NULL','https://et-cetera.ru/mobile','Театр «Et Cetera»'),(5,'FAILED','2022-07-27 21:50:41','Ошибка соединения','https://volocheck.life','unknown'),(6,'INDEXED','2022-07-27 22:49:39','Ошибка индексации: страница https://ipfran.ru/identity/account/login?returnUrl=%2Finstitute%2Fstructure%2F387150429 не доступна','https://ipfran.ru','ИПФ РАН'),(7,'INDEXED','2022-07-27 22:11:53','Ошибка индексации: страница https://www.lutherancathedral.ru/app/download/8717441994/%D0%9F%D1%80%D0%BE%D0%B3%D1%80%D0%B0%D0%BC%D0%BC%D0%B0_%D0%9C%D0%B5%D1%86%D0%B3%D0%B5%D1%80_final.pdf?t=1494453481 не доступна','https://www.lutherancathedral.ru','Собор Петра и Павла - Собор Петра и Павла'),(8,'INDEXED','2022-07-27 21:51:36','Ошибка индексации: страница https://nikoartgallery.com/upload/resize_cache/iblock/f25/1000_1000_1bf185c3d4b189bc00fc7d535f1887290/f25622be02e09734908ab2b61a08b499.JPG не доступна','https://nikoartgallery.com','Галерея искусств Niko');
/*!40000 ALTER TABLE `sites` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2022-07-27 23:11:05
