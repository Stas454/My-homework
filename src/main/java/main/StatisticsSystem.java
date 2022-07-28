
package main;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Model.AccessToTheDB;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class StatisticsSystem {

    ObjectNode getObjNodeStatistic() throws SQLException {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode statisticsResult = mapper.createObjectNode();
        ObjectNode statistics = mapper.createObjectNode();
        ObjectNode total = getObjNodeTotal();
        statistics.set("total", total);
        ArrayNode arrayNode = getArrayNodeDetailed();
        statistics.set("detailed", arrayNode);
        statisticsResult.put("result", true);
        statisticsResult.set("statistics", statistics);
        return statisticsResult;
    }

    ObjectNode getObjNodeTotal() throws SQLException {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode total = mapper.createObjectNode();
        int sites = 0;
        int pages = 0;
        int lemmas = 0;

        try (PreparedStatement pstSelectSitesIdCount = AppSEController.dbConnection.prepareStatement("SELECT COUNT(*) sites_id FROM sites");
             PreparedStatement pstSelectPagesIdCount = AppSEController.dbConnection.prepareStatement("SELECT COUNT(*) pages_id FROM pages");
             PreparedStatement pstSelectLemmasIdCount = AppSEController.dbConnection.prepareStatement("SELECT COUNT(*) lemmas_id FROM lemmas");
             ResultSet rsSitesIdCount = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectSitesIdCount);
             ResultSet rsPagesIdCount = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectPagesIdCount);
             ResultSet rsLemmasIdCount = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectLemmasIdCount)) {
            if (rsSitesIdCount.next()) sites = rsSitesIdCount.getInt(1);
            if (rsPagesIdCount.next()) pages = rsPagesIdCount.getInt(1);
            if (rsLemmasIdCount.next()) lemmas = rsLemmasIdCount.getInt(1);
        }

        total.put("sites", sites);
        total.put("pages", pages);
        total.put("lemmas", lemmas);
        total.put("isIndexing", StatusChangeInApp.isIndexing);

        return total;
    }

    ArrayNode getArrayNodeDetailed() throws SQLException {

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNodeDetailed = mapper.createArrayNode();
        String url = "";
        String name = "";
        String siteStatus = "";
        SimpleDateFormat formatter= new SimpleDateFormat("yyy/MM/dd HH:mm:ss");
        Date statusTime = null;
        String error = "";
        int pagesCount = 0;
        int lemmasCount = 0;
        List<Integer> sitesIdList = new ArrayList<>();

        try (PreparedStatement pstSelectSitesId = AppSEController.dbConnection.prepareStatement("SELECT sites_id FROM sites");
        PreparedStatement pstSitesData = AppSEController.dbConnection.prepareStatement(
                "SELECT url, name, status, status_time, last_error FROM sites WHERE sites_id = ?");
        PreparedStatement pstSelectPagesCount = AppSEController.dbConnection.prepareStatement(
                "SELECT COUNT(*) pages_id FROM pages WHERE sites_id = ?");
        PreparedStatement pstSelectLemmasCount = AppSEController.dbConnection.prepareStatement(
                "SELECT COUNT(*) lemmas_id FROM lemmas WHERE sites_id = ?");
        ResultSet rsSitesId = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectSitesId)) {

            while (rsSitesId.next()) sitesIdList.add(rsSitesId.getInt(1));

            for (int siteId : sitesIdList) {
                pstSitesData.setString(1, Integer.toString(siteId));
                pstSelectPagesCount.setString(1, Integer.toString(siteId));
                pstSelectLemmasCount.setString(1, Integer.toString(siteId));
                pstSitesData.addBatch();
                pstSelectPagesCount.addBatch();
                pstSelectLemmasCount.addBatch();
                ResultSet rsSitesData = AccessToTheDB.getAccessToTheDB().executeSelection(pstSitesData);
                ResultSet rsPagesCount = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectPagesCount);
                ResultSet rsLemmasCount = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectLemmasCount);

                if (rsSitesData.next()) {
                    url = rsSitesData.getString(1);
                    name = rsSitesData.getString(2);
                    siteStatus = rsSitesData.getString(3);
                    statusTime = rsSitesData.getTimestamp(4);
                    error = rsSitesData.getString(5);
                }
                if (rsPagesCount.next()) pagesCount = rsPagesCount.getInt(1);
                if (rsLemmasCount.next()) lemmasCount = rsLemmasCount.getInt(1);

                ObjectNode detailed = mapper.createObjectNode();
                detailed.put("url", url);
                detailed.put("name", name);
                detailed.put("status", siteStatus);
                detailed.put("statusTime", formatter.format(statusTime));
                detailed.put("error", error);
                detailed.put("pages", pagesCount);
                detailed.put("lemmas", lemmasCount);
                arrayNodeDetailed.add(detailed);
            }
        }
       return arrayNodeDetailed;
    }

}
