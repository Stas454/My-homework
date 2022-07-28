
package main;
import main.Exceptions.HttpConnEx;
import main.Model.AccessToTheDB;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

public class TaskSiteIndexing implements Runnable {

    private final String url;
    private final String indexingType;
    private int siteId;
    private final int scanDepth;
    private HashMap<String, LemmaData> lemmasDBCopy;
    private List<Integer> pagesIdList = new ArrayList<>();
    private final CountDownLatch cdl;

    TaskSiteIndexing(String url, String indexingType, int scanDepth, CountDownLatch cdl) {

        this.url = url;
        this.indexingType = indexingType;
        this.scanDepth = scanDepth;
        this.cdl = cdl;
    }

    @Override
    public void run() {

        try {

            try {
                initialization();
            } catch (HttpConnEx e) {
                return;
            }

            //walking
            try (PreparedStatement pstInsertPage = AppSEController.dbConnection.prepareStatement("INSERT INTO pages(" +
                    "path, code, content, sites_id) VALUES(?, ?, ?, ?)")) {
                ForkJoinPool fjp = new ForkJoinPool();
                fjp.invoke(new TaskSiteWalking(url, siteId, scanDepth, pstInsertPage));
                if (StatusChangeInApp.isStopIndexing) {
                    StatusChangeInDB.changeSiteStatus(Status.FAILED, siteId);
                    cdl.countDown();
                    if (cdl.getCount() == 0) {
                        AccessToTheDB.getAccessToTheDB().executeOperation("SET FOREIGN_KEY_CHECKS=1");
                        StatusChangeInApp.isIndexing = false;
                        StatusChangeInApp.isStopIndexing = false;
                    }
                    return;
                }

                AccessToTheDB.getAccessToTheDB().executeOperation(pstInsertPage);
            }

            updatePagesIdList();
            ParserSitePages parserSitePages = new ParserSitePages(siteId, lemmasDBCopy, pagesIdList);
            parserSitePages.extractFromPagesToTheDB();

            if (StatusChangeInApp.isStopIndexing) {
                StatusChangeInDB.changeSiteStatus(Status.FAILED, siteId);
            } else StatusChangeInDB.changeSiteStatus(Status.INDEXED, siteId);

            cdl.countDown();
            if (cdl.getCount() == 0) {
                AccessToTheDB.getAccessToTheDB().executeOperation("SET FOREIGN_KEY_CHECKS=1");
                StatusChangeInApp.isIndexing = false;
                StatusChangeInApp.isStopIndexing = false;
            }

        } catch (Exception e) {
            try {
                StatusChangeInDB.changeSiteStatus(Status.FAILED, siteId);
            } catch (SQLException ignored) {}
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    void initialization() throws HttpConnEx, SQLException {

        //выбор типа инициализации
        switch (indexingType) {
            case "site indexing or reindexing" -> {
                try (PreparedStatement pstSelectSiteId = AppSEController.dbConnection.prepareStatement("SELECT sites_id FROM sites WHERE url = ?")) {
                    pstSelectSiteId.setString(1, url);
                    try (ResultSet rsSelectSiteId = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectSiteId)) {
                        if (rsSelectSiteId.next()) {
                            siteId = rsSelectSiteId.getInt(1);
                        } else siteId = 0;
                    }
                }
                if (siteId == 0) initWhenIndexing();
                else initWhenSiteReindexing();
            }
            case "one-page indexing or reindexing" -> {
                try (PreparedStatement pstSelectSiteId = AppSEController.dbConnection.prepareStatement("SELECT sites_id FROM pages WHERE path = ?")) {
                    pstSelectSiteId.setString(1, url);
                    try (ResultSet rsSelectSiteId = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectSiteId)) {
                        if (rsSelectSiteId.next()) siteId = rsSelectSiteId.getInt(1);
                        else siteId = 0;
                    }
                }
                if (siteId == 0) initWhenIndexing();
                else initWhenPageReindexing();
            }
        }
    }

    void initWhenIndexing() throws HttpConnEx, SQLException {

        //RAM copy
        lemmasDBCopy = new HashMap<>();

        //getTitle
        Connection connection;
        Document doc;
        try {
            connection = HttpConnection.connect(url); // IllegalArgumentException
            doc = connection.get(); // UnknownHostException
        } catch (Exception e) {
            siteId = StatusChangeInDB.putSiteToDb("unknown", "Ошибка соединения", Status.FAILED.toString(), url);
            cdl.countDown();
            if (cdl.getCount() == 0) {
                AccessToTheDB.getAccessToTheDB().executeOperation("SET FOREIGN_KEY_CHECKS=1");
                StatusChangeInApp.isIndexing = false;
                StatusChangeInApp.isStopIndexing = false;
            }
            throw new HttpConnEx("Ошибка соединения");
        }

        Element title = doc.select("title").first();
        String siteTitle = "";
        if (title != null) siteTitle = title.text();

        siteId = StatusChangeInDB.putSiteToDb(siteTitle, "NULL", Status.INDEXING.toString(), url);
    }

    void initWhenSiteReindexing() throws SQLException {

        //status
        StatusChangeInDB.changeSiteStatus(Status.INDEXING, siteId);

        //db update
        AccessToTheDB.getAccessToTheDB().executeOperation("DELETE FROM pages WHERE sites_id = " + siteId);
        AccessToTheDB.getAccessToTheDB().executeOperation("DELETE FROM lemmas WHERE sites_id = " + siteId);
        AccessToTheDB.getAccessToTheDB().executeOperation("DELETE FROM indexes WHERE sites_id = " + siteId);

        //RAM copy
        lemmasDBCopy = new HashMap<>();
    }

    void initWhenPageReindexing() throws SQLException {

        //status
        StatusChangeInDB.changeSiteStatus(Status.INDEXING, siteId);

        //db update
        updatePagesIdList();
        int pageId = pagesIdList.get(0);

        try (PreparedStatement pstUpdateLemmas = AppSEController.dbConnection.prepareStatement("UPDATE lemmas SET frequency = frequency - 1 " +
                "WHERE lemmas_id IN (SELECT lemmas_id FROM indexes WHERE pages_id = ?)")) {
            pstUpdateLemmas.setString(1, Integer.toString(pageId));
            pstUpdateLemmas.addBatch();
            AccessToTheDB.getAccessToTheDB().executeOperation(pstUpdateLemmas);
        }
        AccessToTheDB.getAccessToTheDB().executeOperation("DELETE from lemmas WHERE frequency = 0");

        try (PreparedStatement pstDeleteIndexes = AppSEController.dbConnection.prepareStatement("DELETE FROM indexes WHERE pages_id = ?")) {
            pstDeleteIndexes.setString(1, Integer.toString(pageId));
            pstDeleteIndexes.addBatch();
            AccessToTheDB.getAccessToTheDB().executeOperation(pstDeleteIndexes);
        }

        try (PreparedStatement pstDeletePages = AppSEController.dbConnection.prepareStatement("DELETE FROM pages WHERE pages_id = ?")) {
            pstDeletePages.setString(1, Integer.toString(pageId));
            pstDeletePages.addBatch();
            AccessToTheDB.getAccessToTheDB().executeOperation(pstDeletePages);
        }

        //RAM copy
        lemmasDBCopy = new HashMap<>();
        try (PreparedStatement pstSelectLemmas = AppSEController.dbConnection.prepareStatement(
                "SELECT lemma, lemmas_id, frequency FROM lemmas WHERE sites_id = ?")) {
            pstSelectLemmas.setString(1, Integer.toString(siteId));
            pstSelectLemmas.addBatch();
            ResultSet rsLemmasData = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectLemmas);
            while (rsLemmasData.next()) {
                lemmasDBCopy.put(rsLemmasData.getString(1), new LemmaData(rsLemmasData.getInt(2), rsLemmasData.getInt(3)));
            }
        }
    }

    void updatePagesIdList() throws SQLException {

        pagesIdList = new ArrayList<>();

        switch (indexingType) {
            case "site indexing or reindexing":
                try (ResultSet rsPagesId = AccessToTheDB.getAccessToTheDB().executeSelection(
                        "SELECT pages_id FROM pages WHERE sites_id = " + siteId)) {
                    while (rsPagesId.next()) pagesIdList.add(rsPagesId.getInt(1));
                }
                break;
            case "one-page indexing or reindexing":
                try (PreparedStatement pstPageId = AppSEController.dbConnection.prepareStatement(
                        "SELECT pages_id FROM pages WHERE path = ?")) {
                    pstPageId.setString(1, url);
                    pstPageId.addBatch();
                    ResultSet rsPageId = AccessToTheDB.getAccessToTheDB().executeSelection(pstPageId);
                    if (rsPageId.next()) pagesIdList.add(rsPageId.getInt(1));
                }
        }
    }

}
