
package main;
import main.Model.AccessToTheDB;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;

public class StatusChangeInDB {

    //fill in sites db for the first time
    static int putSiteToDb(String siteTitle, String errorName, String status, String url) throws SQLException {

        int siteId = 0;
        try (PreparedStatement pstInsertUrl = AppSEController.dbConnection.prepareStatement("INSERT INTO sites(status, status_time," +
                "last_error, url, name) VALUES(?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = new Date(System.currentTimeMillis());
            pstInsertUrl.setString(1, status);
            pstInsertUrl.setString(2, formatter.format(date));
            pstInsertUrl.setString(3, errorName);
            pstInsertUrl.setString(4, url);
            pstInsertUrl.setString(5, siteTitle);
            pstInsertUrl.addBatch();
            AccessToTheDB.getAccessToTheDB().executeOperation(pstInsertUrl);

            try (ResultSet rsGeneratedKey = pstInsertUrl.getGeneratedKeys()) {
                if (rsGeneratedKey.next()) siteId = rsGeneratedKey.getInt(1);
            }
        }
        return siteId;
    }

    static synchronized void changeSiteStatus(Status status, int siteId) throws SQLException {

        try (PreparedStatement pstInsertStatus = AppSEController.dbConnection.prepareStatement(
                "UPDATE sites SET status = ?, status_time = ? WHERE sites_id = ?")) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = new Date(System.currentTimeMillis());
            pstInsertStatus.setString(1, status.toString());
            pstInsertStatus.setString(2, formatter.format(date));
            pstInsertStatus.setString(3, Integer.toString(siteId));
            pstInsertStatus.addBatch();
            AccessToTheDB.getAccessToTheDB().executeOperation(pstInsertStatus);
        }
    }

    static synchronized void changeErrorSiteStatus(String errorDescription, int siteId) throws SQLException {

        try (PreparedStatement pstInsertStatus = AppSEController.dbConnection.prepareStatement(
                "UPDATE sites SET last_error = ?, status_time = ? WHERE sites_id = ?")) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = new Date(System.currentTimeMillis());
            pstInsertStatus.setString(1, errorDescription);
            pstInsertStatus.setString(2, formatter.format(date));
            pstInsertStatus.setString(3, Integer.toString(siteId));
            pstInsertStatus.addBatch();
            AccessToTheDB.getAccessToTheDB().executeOperation(pstInsertStatus);
        }
    }

}
