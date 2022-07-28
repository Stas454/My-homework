
package main;
import main.Model.AccessToTheDB;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RecursiveAction;

public class TaskSiteWalking extends RecursiveAction {

    private final List<String> checkedUrlList;
    private volatile static Integer queryCounter = 0;
    private final List<TaskSiteWalking> subTasksList = new ArrayList<>();
    private String url;
    private final String urlRoot;
    private final int siteId;
    private int scanDepth;
    private final PreparedStatement pstInsertPage;

    TaskSiteWalking(String url, int siteId, int scanDepth, PreparedStatement pstInsertPage) {
        this.url = url;
        this.urlRoot = url;
        this.pstInsertPage = pstInsertPage;
        this.checkedUrlList = new ArrayList<>();
        this.siteId = siteId;
        this.scanDepth = scanDepth;
    }

    TaskSiteWalking(String url, String urlRoot, PreparedStatement pstInsertPage, List<String> checkedUrlList, int siteId, int scanDepth) {
        this.url = url;
        this.urlRoot = urlRoot;
        this.pstInsertPage = pstInsertPage;
        this.checkedUrlList = checkedUrlList;
        this.siteId = siteId;
        this.scanDepth = scanDepth;
    }

    @Override
    protected void compute() {

        if (scanDepth-- == 0) return;
        if (Objects.equals(StatusChangeInApp.isStopIndexing, true)) {
            return;
        }

        Connection connection = getWebConnection(url);
        if (connection == null) {
            try {
                StatusChangeInDB.changeErrorSiteStatus("Ошибка индексации: страница " + url +" не доступна", siteId);
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
            return;
        }

        Document doc;
        try {
            doc = connection.get();
        } catch (IOException e) {
            try {
                StatusChangeInDB.changeErrorSiteStatus("Ошибка индексации: страница " + url + " не доступна", siteId);
                pstInsertPage.setString(1, url);
                pstInsertPage.setString(2, "404");
                pstInsertPage.setString(3, "not available");
                pstInsertPage.setInt(4, siteId);
                pstInsertPage.addBatch();
            } catch (SQLException ex) {
                e.printStackTrace();
                throw new RuntimeException();
            }
            return;
        }

        Elements links = doc.select("a[href]");
        String statusCode = String.valueOf(connection.response().statusCode());
        String content = doc.html();

        synchronized (pstInsertPage) { //синхронизации метода было не достаточно
            saveData(statusCode, content);
            System.out.println(url); //////////////////////////////
        }

        for (Element link : links) {
            url = link.attr("abs:href");
            synchronized (checkedUrlList) {
                if (Objects.equals(url, urlRoot) || Objects.equals(url, urlRoot + "/") ||
                        checkedUrlList.contains(url) || !url.startsWith(urlRoot) || url.matches(".+#$")
                        || url.matches(".+\\.(jpg|png|jpeg|pdf)$")) continue;
                checkedUrlList.add(url);
            }

            TaskSiteWalking task = new TaskSiteWalking(url, urlRoot, pstInsertPage, checkedUrlList, siteId, scanDepth);
            subTasksList.add(task);
            task.fork();
        }

        for (TaskSiteWalking taskSiteWalking : subTasksList) taskSiteWalking.join();
    }

    synchronized Connection getWebConnection(String url) {

        try {
            Thread.sleep(5);
            return HttpConnection.connect(url)
                    .userAgent(AppSEController.userAg)
                    .referrer(AppSEController.ref);
        } catch(Exception e) { // IllegalArgumentException - https://dimonvideo.ru
            e.printStackTrace();
            return null;
        }
    }

    void saveData (String statusCode, String content) {

        try {
            pstInsertPage.setString(1, url);
            pstInsertPage.setString(2, statusCode);
            pstInsertPage.setString(3, content);
            pstInsertPage.setInt(4, siteId);
            pstInsertPage.addBatch();

            if (queryCounter++ > 100) {
                AccessToTheDB.getAccessToTheDB().executeOperation(pstInsertPage);
                pstInsertPage.clearParameters();
                queryCounter = 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
