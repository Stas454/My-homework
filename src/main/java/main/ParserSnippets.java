
package main;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserSnippets {

    static HashMap<Integer, String> getFormattedPagesIdAndSnippets(
            HashMap<Integer, String> pagesIdAndSnippets, HashMap<String, Float> lemmasAndRankFromQuery) throws IOException {

        ParserText parserText = new ParserText();
        HashMap<Integer, String> formattedPagesIdAndSnippets = new HashMap<>();
        HashSet<String> wordsForms;
        String formattedSnippet;

        for (Map.Entry<Integer, String> entryPagesIdAndSnippets : pagesIdAndSnippets.entrySet()) {
            wordsForms = parserText.getAllFormsAccToQuery(entryPagesIdAndSnippets.getValue(), lemmasAndRankFromQuery);
            formattedSnippet = entryPagesIdAndSnippets.getValue();
            for (String s : wordsForms) {
                Pattern p = Pattern.compile("(" + s + ")([>\\s,.:!?;\"-]|$)");
                Matcher m = p.matcher(formattedSnippet);
                if (m.find()) formattedSnippet = m.replaceAll("<b> " + s + " </b>");
            }
            formattedPagesIdAndSnippets.put(entryPagesIdAndSnippets.getKey(), formattedSnippet);
        }
        return formattedPagesIdAndSnippets;
    }

}
