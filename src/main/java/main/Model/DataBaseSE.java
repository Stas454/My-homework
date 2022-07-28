
package main.Model;
import java.sql.SQLException;

public class DataBaseSE {

    public DataBaseSE() throws SQLException {
        AccessToTheDB.getAccessToTheDB().executeOperation("SET FOREIGN_KEY_CHECKS=0");
        createTablePages();
        createTableLemmas();
        createTablesFields();
        createTableIndexes();
        createTableSites();
        AccessToTheDB.getAccessToTheDB().executeOperation("SET FOREIGN_KEY_CHECKS=1");
    }

    public void createTablePages() throws SQLException {
        AccessToTheDB.getAccessToTheDB().executeOperation("DROP TABLE IF EXISTS pages");
        AccessToTheDB.getAccessToTheDB().executeOperation(
                "CREATE TABLE pages (" +
                "pages_id INT NOT NULL AUTO_INCREMENT, " +
                "sites_id INT NOT NULL , " +
                "path TEXT NOT NULL, " +
                "code INT NOT NULL, " +
                "content MEDIUMTEXT NOT NULL, " +
                "PRIMARY KEY(pages_id), " +
                "FOREIGN KEY(sites_id) REFERENCES sites(sites_id))");
    }

    public void createTableLemmas() throws SQLException {
        AccessToTheDB.getAccessToTheDB().executeOperation("DROP TABLE IF EXISTS lemmas");
        AccessToTheDB.getAccessToTheDB().executeOperation(
                "CREATE TABLE lemmas (" +
                "lemmas_id INT NOT NULL, " +
                "sites_id INT NOT NULL, " +
                "lemma VARCHAR(255) NOT NULL, " +
                "frequency INT NOT NULL, " +
                "PRIMARY KEY(lemmas_id, sites_id), " +
                "FOREIGN KEY(sites_id) REFERENCES sites(sites_id))");
    }

    public void createTablesFields() throws SQLException {
        AccessToTheDB.getAccessToTheDB().executeOperation("DROP TABLE IF EXISTS fields");
        AccessToTheDB.getAccessToTheDB().executeOperation(
                "CREATE TABLE fields (" +
                "fields_id INT NOT NULL AUTO_INCREMENT, " +
                "name VARCHAR(255) NOT NULL, " +
                "selector VARCHAR(255) NOT NULL, " +
                "weight FLOAT NOT NULL, " +
                "PRIMARY KEY(fields_id))");
    }

    public void createTableIndexes() throws SQLException {
        AccessToTheDB.getAccessToTheDB().executeOperation("DROP TABLE IF EXISTS indexes");
        AccessToTheDB.getAccessToTheDB().executeOperation(
                "CREATE TABLE indexes (" +
                "indexes_id INT NOT NULL AUTO_INCREMENT, " +
                "pages_id INT NOT NULL, " +
                "lemmas_id INT NOT NULL, " +
                "sites_id INT NOT NULL," +
                "ranks FLOAT NOT NULL, " + //rank зарезервированное слово
                "PRIMARY KEY(indexes_id), " +
                "FOREIGN KEY(pages_id) REFERENCES pages(pages_id), " +
                "FOREIGN KEY(lemmas_id) REFERENCES lemmas(lemmas_id), " +
                "FOREIGN KEY(sites_id) REFERENCES lemmas(sites_id))"); //должны быть одинаковые названия
    }

    public void createTableSites() throws SQLException {
        AccessToTheDB.getAccessToTheDB().executeOperation("DROP TABLE IF EXISTS sites");
        AccessToTheDB.getAccessToTheDB().executeOperation(
                "CREATE TABLE sites (" +
                "sites_id INT NOT NULL AUTO_INCREMENT, " +
                "status ENUM('INDEXING', 'INDEXED', 'FAILED'), " +
                "status_time DATETIME, " +
                "last_error TEXT, " +
                "url VARCHAR(255), " +
                "name VARCHAR(255), " +
                "PRIMARY KEY(sites_id))");
    }

}
