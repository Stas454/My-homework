
package main;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Model.AccessToTheDB;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class SearchSystem {

    private final int MAX_RESULT_LIMIT = 20;
    private final int OFFSET_LIMIT = 0;
    private final int offset;
    private final int limit;

    SearchSystem (Integer offset, Integer limit) {
        if (limit == null) this.limit = MAX_RESULT_LIMIT;
        else this.limit = limit;
        if (offset == null) this.offset = OFFSET_LIMIT;
        else this.offset = offset;
    }

    ObjectNode search (List<String> siteUrlList, String searchQuery) throws SQLException, IOException {

        List<Integer> siteIdList = getSiteIdList(siteUrlList);

        if (siteIdList.size() == 0) return ResponsesOfApp.errorResponse("Сайт отсутствует в базе данных");
        if (Objects.equals(searchQuery, "")) return ResponsesOfApp.errorResponse("Задан пустой поисковый запрос");

        ParserText parserText = new ParserText();
        HashMap<String, Float> lemmasFromQuery = parserText.getLemmasAndRank(searchQuery, 1f);
        HashMap<Integer, Float> allSitesPagesIdAndRelevance = getAllSitesPagesIdAndRelevance(lemmasFromQuery, siteIdList);
        List<Integer> sortedSelectedPagesId = sortSelectedPagesId(allSitesPagesIdAndRelevance);

        if (limit < sortedSelectedPagesId.size()) sortedSelectedPagesId = sortedSelectedPagesId.subList(0, limit);
        if (offset < sortedSelectedPagesId.size()) sortedSelectedPagesId = sortedSelectedPagesId.subList(offset, sortedSelectedPagesId.size());

        return getSearchResultInJson(sortedSelectedPagesId, allSitesPagesIdAndRelevance, lemmasFromQuery);
    }

    List<Integer> getSiteIdList(List<String> siteUrlList) throws SQLException {

        List<Integer> siteIdList = new ArrayList<>();
        ResultSet rsSelectSiteId = null;
        if (Objects.equals(siteUrlList.get(0), "all")) {
            rsSelectSiteId = AccessToTheDB.getAccessToTheDB().executeSelection("SELECT sites_id FROM sites");
            while (rsSelectSiteId.next()) siteIdList.add(rsSelectSiteId.getInt(1));
        } else {
            PreparedStatement pstSelectSiteId = AppSEController.dbConnection.prepareStatement("SELECT sites_id FROM sites WHERE url = ?");
            for (String url : siteUrlList) {
                pstSelectSiteId.setString(1, url);
                pstSelectSiteId.addBatch();
                rsSelectSiteId = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectSiteId);
                if (rsSelectSiteId.next()) siteIdList.add(rsSelectSiteId.getInt(1));
            }
        }
        if (rsSelectSiteId != null) rsSelectSiteId.close();

        return siteIdList;
    }

    HashMap<Integer, Float> getAllSitesPagesIdAndRelevance(HashMap<String, Float> lemmasFromQuery, List<Integer> siteIdList) throws SQLException {

        HashMap<Integer, Float> allSitesPagesIdAndRelevance = new HashMap<>();
        for (int siteId : siteIdList) {
            List<String> sortedLemmas = getLemmasByFrequency(lemmasFromQuery, siteId);
            List<Integer> pagesIdMatchingQuery = selectPagesId(sortedLemmas, siteId);
            HashMap<Integer, Float> pagesIdAndRelevance = getPagesIdAndRelevance(pagesIdMatchingQuery, sortedLemmas, siteId); ////////////////
            allSitesPagesIdAndRelevance.putAll(pagesIdAndRelevance);
        }
        return allSitesPagesIdAndRelevance;
    }

    List<String> getLemmasByFrequency(HashMap<String, Float> lemmasFromQuery, int siteId) throws SQLException {

        List<String> SortedLemmas = new ArrayList<>();
        HashMap<String, Integer> lemmasAndFrequency = new HashMap<>();

        try (PreparedStatement pstSelectLemmas = AppSEController.dbConnection.prepareStatement(
                "SELECT lemma, frequency FROM lemmas WHERE lemma = ? AND sites_id = ?")) {
            ResultSet rsLemmasWithFrequency = null;
            for (Map.Entry<String, Float> l : lemmasFromQuery.entrySet()) {
                pstSelectLemmas.setString(1, l.getKey());
                pstSelectLemmas.setString(2, Integer.toString(siteId));
                pstSelectLemmas.addBatch();
                rsLemmasWithFrequency = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectLemmas);
                if (!rsLemmasWithFrequency.next()) {
                    lemmasAndFrequency.put(l.getKey(), 0);
                } else {
                    do {
                        lemmasAndFrequency.put(rsLemmasWithFrequency.getString(1), rsLemmasWithFrequency.getInt(2));
                    } while (rsLemmasWithFrequency.next());
                }
            }
            if (rsLemmasWithFrequency != null) rsLemmasWithFrequency.close();
        }

        lemmasAndFrequency.entrySet().stream().sorted((Comparator.comparing(e -> e.getValue()))).forEach(e -> SortedLemmas.add(e.getKey()));
        return SortedLemmas;
    }

    List<Integer> selectPagesId(List<String> sortedLemmas, int siteId) {

        if (sortedLemmas.size() == 0) return new ArrayList<>();

        List<Integer> intersectingSites = new ArrayList<>();
        List<Integer> sites;

        try (PreparedStatement pstSelectPagesId = AppSEController.dbConnection.prepareStatement(
                "SELECT indexes.pages_id FROM indexes JOIN lemmas " +
                "ON indexes.lemmas_id = lemmas.lemmas_id AND indexes.sites_id = lemmas.sites_id " +
                "WHERE lemmas.lemma = ? AND lemmas.sites_id = ?")) {
            pstSelectPagesId.setString(1, sortedLemmas.get(0));
            pstSelectPagesId.setString(2, Integer.toString(siteId));
            ResultSet rsPagesId;
            rsPagesId = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectPagesId);
            while (rsPagesId.next()) intersectingSites.add(rsPagesId.getInt(1));
            for (String lemma : sortedLemmas) {
                sites = new ArrayList<>();
                pstSelectPagesId.setString(1, lemma);
                rsPagesId = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectPagesId);
                while (rsPagesId.next()) sites.add(rsPagesId.getInt(1));
                intersectingSites.retainAll(sites);
                if (!intersectingSites.isEmpty()) continue;
                return new ArrayList<>();
            }
            rsPagesId.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return intersectingSites;
    }

    HashMap<Integer, Float> getPagesIdAndRelevance(List<Integer> pagesIdMatchingQuery, List<String> sortedLemmas, int siteId) throws SQLException {

        ////////////////
        HashMap<Integer, Float> pagesIdAndRelevance = new HashMap<>();
        if (pagesIdMatchingQuery.size() == 0) return pagesIdAndRelevance;

        HashMap<String, Integer> lemmaAndLemmaId = new HashMap<>();
        try (PreparedStatement pstSelectLemmasId = AppSEController.dbConnection.prepareStatement(
                "SELECT lemmas_id from lemmas WHERE lemma = ? AND sites_id = ?")) {
            for (String lemma : sortedLemmas) {
                pstSelectLemmasId.setString(1, lemma);
                pstSelectLemmasId.setString(2, Integer.toString(siteId));
                pstSelectLemmasId.addBatch();
                ResultSet rsLemmasId = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectLemmasId);

                if(rsLemmasId.next()) {
                    lemmaAndLemmaId.put(lemma, rsLemmasId.getInt(1));
                }
            }
        }

        ResultSet rsRanks = null;
        try (PreparedStatement pstSelectRank = AppSEController.dbConnection.prepareStatement(
                "SELECT ranks FROM indexes WHERE pages_id = ? AND lemmas_id = ? AND sites_id = ?")) {
            for (int pageId : pagesIdMatchingQuery) {
                pagesIdAndRelevance.put(pageId, 0f);
                for (String lemma : sortedLemmas) {
                    pstSelectRank.setString(1, Integer.toString(pageId));
                    pstSelectRank.setString(2, Integer.toString(lemmaAndLemmaId.get(lemma)));
                    pstSelectRank.setString(3, Integer.toString(siteId));
                    pstSelectRank.addBatch();

                    rsRanks = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectRank);
                    while (rsRanks.next()) {
                        float rank = rsRanks.getFloat(1);
                        pagesIdAndRelevance.replace(pageId, pagesIdAndRelevance.get(pageId) + rank);
                    }
                }
            }
            if (rsRanks != null) rsRanks.close();
        }

        return pagesIdAndRelevance;
    }

    List<Integer> sortSelectedPagesId(HashMap<Integer, Float> pagesIdAndRank) {

        List<Integer> sortedSelectedPagesId = new ArrayList<>();
        pagesIdAndRank.entrySet().stream().sorted((Comparator.comparing(e -> - e.getValue()))).forEach(e -> sortedSelectedPagesId.add(e.getKey()));
        return sortedSelectedPagesId;
    }

    ObjectNode getSearchResultInJson(List<Integer> sortedSelectedPagesId, HashMap<Integer, Float> allSitesPagesIdAndRelevance,
                                     HashMap<String, Float> lemmasFromQuery) throws SQLException, IOException {

        SearchSystemAddData searchSystemAddData = new SearchSystemAddData();
        HashMap<Integer, String> pagesIdAndTitle = searchSystemAddData.getPagesTitles(sortedSelectedPagesId);
        HashMap<Integer, PageRootAndUri> pagesIdAndRootWithUri = searchSystemAddData.getPagesRootAndUri(sortedSelectedPagesId);
        HashMap<Integer, String> pagesIdAndSnippets = searchSystemAddData.getPagesSnippets(sortedSelectedPagesId, lemmasFromQuery);
        HashMap<Integer, String> siteTitles = searchSystemAddData.getSiteTitle(sortedSelectedPagesId);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNodeData = mapper.createArrayNode();
        for (int pageId : sortedSelectedPagesId) {
            ObjectNode searchData = mapper.createObjectNode();
            searchData.put("site", pagesIdAndRootWithUri.get(pageId).root);
            searchData.put("siteName", siteTitles.get(pageId));
            searchData.put("uri", pagesIdAndRootWithUri.get(pageId).uri);
            searchData.put("title", pagesIdAndTitle.get(pageId));
            searchData.put("snippet", pagesIdAndSnippets.get(pageId));
            searchData.put("relevance", allSitesPagesIdAndRelevance.get(pageId));
            arrayNodeData.add(searchData);
        }

        ObjectNode searchResult = mapper.createObjectNode();
        searchResult.set("data", arrayNodeData);
        searchResult.put("result", true);
        searchResult.put("count", sortedSelectedPagesId.size());
        return searchResult;
    }

}
