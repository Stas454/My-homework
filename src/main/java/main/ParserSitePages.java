
package main;
import main.Model.AccessToTheDB;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ParserSitePages {

    private final HashMap <String, LemmaData> lemmasDBCopy;
    private final HashMap <String, LemmaData> lemmasNew = new HashMap<>();
    private final HashMap <String, LemmaData> lemmasRepeated = new HashMap<>();
    private ParserText parserText;
    private int queryCounter = 0;
    private final int siteId;
    private int idLemmaMax;
    private final List<Integer> pagesIdList;
    private int i;
    private final int BUFFER_SIZE = 10_000;
    private final float WEIGHT_BODY = 0.8f;
    private final float WEIGHT_TITLE = 1f;

    public ParserSitePages(int siteId, HashMap <String, LemmaData> lemmasDBCopy, List<Integer> pagesIdList) {
        this.siteId = siteId;
        this.lemmasDBCopy = lemmasDBCopy;
        this.pagesIdList = pagesIdList;
    }

    void extractFromPagesToTheDB() throws Exception {

        if (!lemmasDBCopy.isEmpty()) idLemmaMax = Collections.max(lemmasDBCopy.values()).idLemma;
        else idLemmaMax = 0;
        HashMap<String, Float> lemmasAndRank;
        parserText = new ParserText();

        try (PreparedStatement pstInsertIndex = AppSEController.dbConnection.prepareStatement(
                     "INSERT INTO indexes(pages_id, lemmas_id, sites_id, ranks) VALUES(?, ?, ?, ?)");
             PreparedStatement pstInsertNewLemmas = AppSEController.dbConnection.prepareStatement(
                     "INSERT INTO lemmas(lemmas_id, sites_id, lemma, frequency) VALUES(?, ?, ?, ?)");
             PreparedStatement pstInsertRepeatedLemmas = AppSEController.dbConnection.prepareStatement(
                     "UPDATE lemmas SET frequency = ? WHERE lemmas_id = ? AND sites_id = ?")) {

            //fill in RAM: lemmasDBCopy, lemmasNew, lemmasRepeated
            for (int pageId : pagesIdList) {
                lemmasAndRank = getLemmasAndRank(pageId);
                lemmasAndRank.forEach((k, v) -> {
                    if (lemmasNew.containsKey(k)) {
                        lemmasNew.get(k).quantityPagesWithLemma++;
                    } else if (lemmasRepeated.containsKey(k)) {
                        lemmasRepeated.get(k).quantityPagesWithLemma++;
                    } else if (lemmasDBCopy.containsKey(k)) {
                        lemmasRepeated.put(k, new LemmaData(lemmasDBCopy.get(k).idLemma, ++lemmasDBCopy.get(k).quantityPagesWithLemma));
                    } else {
                        lemmasNew.put(k, new LemmaData(++idLemmaMax, 1));
                    }
                });

                prepareStmForIndexesDB(lemmasAndRank, pageId, pstInsertIndex);
                saveDataToTheDB(lemmasAndRank.size(), pstInsertIndex, pstInsertNewLemmas, pstInsertRepeatedLemmas);

                if (StatusChangeInApp.isStopIndexing) {
                    return;
                }
            }

            //flush
            AccessToTheDB.getAccessToTheDB().executeOperation(pstInsertIndex);
            prepareStmForLemmasDB(pstInsertNewLemmas, pstInsertRepeatedLemmas);
            AccessToTheDB.getAccessToTheDB().executeOperation(pstInsertNewLemmas);
            AccessToTheDB.getAccessToTheDB().executeOperation(pstInsertRepeatedLemmas);
        }
    }

    HashMap<String, Float> getLemmasAndRank(int pageId) throws SQLException {

        Document doc;
        try (ResultSet rsContent = AccessToTheDB.getAccessToTheDB().executeSelection(
                "SELECT content FROM pages WHERE pages_id = " + pageId)) {
            if (rsContent.next()) {
                doc = Jsoup.parse(rsContent.getString(1));
            }
            else return new HashMap<>();
        }
        HashMap<String, Float> lemmasAndRankBody = parserText.getLemmasAndRank(doc.body().text(), WEIGHT_BODY);
        HashMap<String, Float> lemmasAndRankTitle = parserText.getLemmasAndRank(doc.select("title").text(), WEIGHT_TITLE);

        lemmasAndRankTitle.forEach((k, v) -> {if (lemmasAndRankBody.containsKey(k)) {
            lemmasAndRankBody.put(k, v + lemmasAndRankBody.get(k));
        } else {
            lemmasAndRankBody.put(k, v);
        }});
        return lemmasAndRankBody;
    }

    void prepareStmForIndexesDB(HashMap<String, Float> lemmasAndRank, int pageId, PreparedStatement pstInsertIndex) throws RuntimeException {

        lemmasAndRank.forEach((k, v) -> {
            try {
                pstInsertIndex.setString(1, Integer.toString(pageId));
                pstInsertIndex.setString(2, Integer.toString(lemmasNew.containsKey(k) ? lemmasNew.get(k).idLemma : lemmasRepeated.get(k).idLemma));
                pstInsertIndex.setString(3, Integer.toString(siteId));
                pstInsertIndex.setString(4, Float.toString(v));
                pstInsertIndex.addBatch();
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }

    void prepareStmForLemmasDB(PreparedStatement pstInsertNewLemmas, PreparedStatement pstInsertRepeatedLemmas) throws RuntimeException {

        lemmasNew.forEach((k,v) -> {
            try {
                pstInsertNewLemmas.setString(1, Integer.toString(lemmasNew.get(k).idLemma));
                pstInsertNewLemmas.setString(2, Integer.toString(siteId));
                pstInsertNewLemmas.setString(3, k);
                pstInsertNewLemmas.setString(4, Integer.toString(lemmasNew.get(k).quantityPagesWithLemma));
                pstInsertNewLemmas.addBatch();
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }});

        lemmasRepeated.forEach((k, v) -> {
            try {
                pstInsertRepeatedLemmas.setString(1, Integer.toString(lemmasRepeated.get(k).quantityPagesWithLemma));
                pstInsertRepeatedLemmas.setString(2, Integer.toString(lemmasRepeated.get(k).idLemma));
                pstInsertRepeatedLemmas.setString(3, Integer.toString(siteId));
                pstInsertRepeatedLemmas.addBatch();
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });

        lemmasDBCopy.putAll(lemmasNew);
        lemmasDBCopy.putAll(lemmasRepeated);
        lemmasNew.clear();
        lemmasRepeated.clear();
    }

    void saveDataToTheDB(int size, PreparedStatement pstInsertIndex, PreparedStatement pstInsertNewLemmas,
                         PreparedStatement pstInsertRepeatedLemmas) throws SQLException {

        queryCounter = queryCounter + size;
        if (queryCounter > BUFFER_SIZE) {
            System.out.println(i++); ////////////////////////////

            prepareStmForLemmasDB(pstInsertNewLemmas, pstInsertRepeatedLemmas);

            AccessToTheDB.getAccessToTheDB().executeOperation(pstInsertIndex);
            pstInsertIndex.clearBatch();
            AccessToTheDB.getAccessToTheDB().executeOperation(pstInsertNewLemmas);
            pstInsertNewLemmas.clearBatch();
            AccessToTheDB.getAccessToTheDB().executeOperation(pstInsertRepeatedLemmas);
            pstInsertRepeatedLemmas.clearBatch();

            lemmasDBCopy.putAll(lemmasNew);
            lemmasDBCopy.putAll(lemmasRepeated);
            lemmasNew.clear();
            lemmasRepeated.clear();
            queryCounter = 0;

            StatusChangeInDB.changeSiteStatus(Status.INDEXING, siteId);
        }
    }
}
