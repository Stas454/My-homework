
package main;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserText {

    org.apache.lucene.morphology.LuceneMorphology luceneMorphRu;
    org.apache.lucene.morphology.LuceneMorphology luceneMorphEn;

    ParserText() throws IOException {
        this.luceneMorphRu = new RussianLuceneMorphology();
        this.luceneMorphEn = new EnglishLuceneMorphology();
    }

    HashMap<String, Float> getLemmasAndRank(String text, Float weight) {

        List<String> textWordsList = new ArrayList<>();
        Pattern wordsWithMoreThan1Letters = Pattern.compile("[a-zA-zа-яА-Я]{2,}+", Pattern.UNICODE_CHARACTER_CLASS);
        Matcher matcherWords = wordsWithMoreThan1Letters.matcher(text);
        matcherWords.results().forEach(a -> textWordsList.add(a.group()));

        HashMap<String, Float> lemmasAndRank = new HashMap<>();
        textWordsList.stream().map(a -> getLemma(a)).forEach(b -> {
            if (Objects.equals(b, "notAccepted")) return;
            if (lemmasAndRank.containsKey(b)) {
                lemmasAndRank.put(b, lemmasAndRank.get(b) + weight);
            } else {
                lemmasAndRank.put(b, weight);
            }});
        return lemmasAndRank;
    }

    HashSet<String> getAllFormsAccToQuery(String text, HashMap<String, Float> lemmasAndRankFromQuery) {

        List<String> textWordsList = new ArrayList<>();
        Pattern wordsWithMoreThan1Letters = Pattern.compile("[a-zA-zа-яА-Я]{2,}+", Pattern.UNICODE_CHARACTER_CLASS);
        Matcher matcherWords = wordsWithMoreThan1Letters.matcher(text);
        matcherWords.results().forEach(a -> textWordsList.add(a.group()));

        HashSet<String> wordsForms = new HashSet<>();
        for (String word : textWordsList) {
            String lemma = getLemma(word);
            if (Objects.equals(lemma, "notAccepted")) continue;
            if (lemmasAndRankFromQuery.containsKey(lemma)) wordsForms.add(word);
        }
        return wordsForms;
    }

    String getLemma(String word) {

        String lowerCaseWord = word.toLowerCase();
        List<String> wordBaseForm;
        List<String> morphInfo;
        Pattern patternMorphInfo = Pattern.compile("(\\S+\\s)(\\w+)(.*)", Pattern.UNICODE_CHARACTER_CLASS);
        Matcher matcherMorphInfo;

        if (luceneMorphRu.checkString(lowerCaseWord)) {
            morphInfo = luceneMorphRu.getMorphInfo(lowerCaseWord);
            matcherMorphInfo = patternMorphInfo.matcher(morphInfo.toString());

            if (matcherMorphInfo.find()) {
                if (matcherMorphInfo.group(2).matches("(A|С|П|Н|Г|ИНФИНИТИВ|ПРИЧАСТИЕ|КР_ПРИЛ|ДЕЕПРИЧАСТИЕ)")) {
                    wordBaseForm = luceneMorphRu.getNormalForms(lowerCaseWord);
                    return wordBaseForm.get(0);
                }
            }
        }

        if (luceneMorphEn.checkString(lowerCaseWord)) {
            morphInfo = luceneMorphEn.getMorphInfo(lowerCaseWord);
            matcherMorphInfo = patternMorphInfo.matcher(morphInfo.toString());

            if (matcherMorphInfo.find()) {
                if (matcherMorphInfo.group(2).matches("(VERB|NOUN|ADJECTIVE|ADVERB)")) {
                    wordBaseForm = luceneMorphEn.getNormalForms(lowerCaseWord);
                    return wordBaseForm.get(0);
                }
            }
        }
        return "notAccepted";
    }

}
