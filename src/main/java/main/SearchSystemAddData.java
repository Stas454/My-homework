
package main;
import main.Model.AccessToTheDB;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchSystemAddData {

    HashMap<Integer, String> getPagesSnippets(List<Integer> sortedSelectedPagesId, HashMap<String, Float> lemmasAndRankFromQuery) throws IOException, SQLException {

        class TextUnit {
            String textUnit;
            HashMap<String, Float> textUnitLemmas;
            TextUnit(String textUnit, HashMap<String, Float> textUnitLemmas) {
                this.textUnit = textUnit;
                this.textUnitLemmas = textUnitLemmas;
            }
        }
        List<TextUnit> textUnitDataList = new ArrayList<>();
        ParserText parserText = new ParserText();
        HashMap<Integer, String> pagesIdAndSnippets = new HashMap<>();

        for (int pageId : sortedSelectedPagesId) {
            textUnitDataList.clear();
            String content = "";

            //content
            try (ResultSet rsContent = AccessToTheDB.getAccessToTheDB().executeSelection("SELECT content FROM pages WHERE pages_id = " + pageId)) {
                if (rsContent.next()) content = rsContent.getString(1);
            }

            //text elements data list
            Pattern patternSnippet = Pattern.compile(">?([a-zA-zа-яА-Я\\s\\d-,.;:?!]+)<?", Pattern.UNICODE_CHARACTER_CLASS);
            Matcher matcherSnippet = patternSnippet.matcher(content);
            ArrayList<String> textUnitsWithoutDuplicates = new ArrayList<>();
            matcherSnippet.results().forEach(a -> {
                if (!textUnitsWithoutDuplicates.contains(a.group(1).trim())) textUnitsWithoutDuplicates.add(a.group(1).trim());});
            textUnitsWithoutDuplicates.forEach(a -> textUnitDataList.add(new TextUnit(a, parserText.getLemmasAndRank(a, 1f))));

            //compare with query lemmas
            List<String> lemmasList = new ArrayList<>();
            lemmasAndRankFromQuery.forEach((key, value) -> lemmasList.add(key));
            for (TextUnit t : textUnitDataList) {
                for (String lemma : lemmasList) {
                    if (t.textUnitLemmas.containsKey(lemma)) {
                        if (pagesIdAndSnippets.containsKey(pageId)) {
                            pagesIdAndSnippets.replace(pageId, pagesIdAndSnippets.get(pageId) + " ...<br>" + t.textUnit);
                        } else pagesIdAndSnippets.put(pageId, t.textUnit);
                    }
                }
            }

        }

        return ParserSnippets.getFormattedPagesIdAndSnippets(pagesIdAndSnippets, lemmasAndRankFromQuery);
    }

    HashMap<Integer, String> getPagesTitles(List<Integer> sortedSelectedPagesId) throws SQLException {

        HashMap<Integer, String> pagesIdAndTitle = new HashMap<>();
        String content = "";
        for (int pageId : sortedSelectedPagesId) {
            try (ResultSet rsContent = AccessToTheDB.getAccessToTheDB().executeSelection("SELECT content FROM pages WHERE pages_id = " + pageId)) {
                if (rsContent.next()) {
                    content = rsContent.getString(1);
                }
            }
            Document doc = Jsoup.parse(content);
            Elements titles = doc.select("title");
            for (Element title : titles) {
                pagesIdAndTitle.put(pageId, title.text());
            }
        }
        return pagesIdAndTitle;
    }

    HashMap<Integer, PageRootAndUri> getPagesRootAndUri(List<Integer> sortedSelectedPagesId) throws SQLException {

        HashMap<Integer, PageRootAndUri> pagesIdAndRootWithUri = new HashMap<>();
        String url = "";
        String root = "";
        String uri;
        for (int pageId : sortedSelectedPagesId) {
            try (ResultSet rsUrl = AccessToTheDB.getAccessToTheDB().executeSelection("SELECT path FROM pages WHERE pages_id = " + pageId);
                 ResultSet rsRoot = AccessToTheDB.getAccessToTheDB().executeSelection("SELECT url FROM sites WHERE sites_id = " +
                         "(SELECT sites_id FROM pages WHERE pages_id =" + pageId + ")")) {
                if (rsUrl.next() && rsRoot.next()) {
                    url = rsUrl.getString(1);
                    root = rsRoot.getString(1);
                }
                uri = url.replaceAll("^" + root, "");
                pagesIdAndRootWithUri.put(pageId, new PageRootAndUri(root, uri));
            }

        }
        return pagesIdAndRootWithUri;
    }

    HashMap<Integer, String> getSiteTitle(List<Integer> sortedSelectedPagesId) {

        HashMap<Integer, String> pagesIdAndSiteTitle = new HashMap<>();
        String siteTitle = "";

        try (PreparedStatement pstSiteTitle = AppSEController.dbConnection.prepareStatement("SELECT name FROM sites WHERE sites_id = " +
                "(SELECT sites_id FROM pages WHERE pages_id = ?)")) {
            for (int pageId : sortedSelectedPagesId) {
                pstSiteTitle.setString(1, Integer.toString(pageId));
                pstSiteTitle.addBatch();
                ResultSet rsSiteTitle = AccessToTheDB.getAccessToTheDB().executeSelection(pstSiteTitle);
                if (rsSiteTitle.next()) {
                    siteTitle = rsSiteTitle.getString(1);
                }
                pagesIdAndSiteTitle.put(pageId, siteTitle);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return pagesIdAndSiteTitle;
    }

}

