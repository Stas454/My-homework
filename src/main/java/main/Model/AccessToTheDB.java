
package main.Model;
import main.AppSEController;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

//на случай если предется ограничивать доступ к БД из разных потоков
public class AccessToTheDB {

    private static final AccessToTheDB accessToTheDB = new AccessToTheDB();

    private AccessToTheDB () {
    }

    public ResultSet executeSelection (PreparedStatement preparedStatement) throws SQLException {
        return preparedStatement.executeQuery();
    }

    public ResultSet executeSelection (String query) throws SQLException {
        return AppSEController.dbConnection.createStatement().executeQuery(query);
    }

    public void executeOperation (PreparedStatement preparedStatement) throws SQLException {
        preparedStatement.executeBatch();
    }

    public void executeOperation(String query) throws SQLException {
        AppSEController.dbConnection.createStatement().execute(query);
    }

    public static AccessToTheDB getAccessToTheDB() {
        return accessToTheDB;
    }

}

