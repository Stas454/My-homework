
package main;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import main.Exceptions.AdminException;
import main.Exceptions.IsIndexingException;
import main.Exceptions.UserException;
import main.Exceptions.YamlDataException;
import main.Model.AccessToTheDB;
import main.Model.DataBaseSE;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class AppSEController
{

    public static Connection dbConnection;
    private final int SCAN_DEPTH = 100;
    private final String APPLICATION_PATCH = "application.yaml";
    public static String userAg;
    public static String ref;

    @GetMapping ("/startIndexing")
    public ResponseEntity<ObjectNode> indexing() {

        try {
            dbCheckAndSetup();
            StatusChangeInApp.toIsIndexingTrue();
            List<YamlSiteData> sitesForIndexing = getYamlData().sites;
            if (sitesForIndexing == null) throw new YamlDataException();
            getUserAgent();

            HashSet<String> sitesUrl = new HashSet<>();
            sitesForIndexing.forEach(a -> {
                String formattedUrl = "";
                Pattern patternUrlFormat = Pattern.compile("(.+)[^/]", Pattern.UNICODE_CHARACTER_CLASS);
                Matcher matcherUrlFormat = patternUrlFormat.matcher(a.url);
                if(matcherUrlFormat.find()) formattedUrl = matcherUrlFormat.group(0);
                sitesUrl.add(formattedUrl);});

            new DataBaseSE();

            AccessToTheDB.getAccessToTheDB().executeOperation("SET FOREIGN_KEY_CHECKS=0");
            CountDownLatch cdl = new CountDownLatch(sitesForIndexing.size());
            int cores = Runtime.getRuntime().availableProcessors();
            int pools = Math.min(cores, 6);
            ExecutorService executor = Executors.newFixedThreadPool(pools);
            for (String url : sitesUrl) {
                executor.execute(new TaskSiteIndexing(url, "site indexing or reindexing", SCAN_DEPTH, cdl));
            }

            return ResponsesOfApp.getSuccessResponse();

        } catch (IOException e) {
            e.printStackTrace();
            StatusChangeInApp.isIndexing = false;
            StatusChangeInApp.isStopIndexing = false;
            return ResponsesOfApp.getErrorResponse("Ошибка соединения с yaml", HttpStatus.NOT_FOUND);
        } catch (SQLException e ) {
            e.printStackTrace();
            StatusChangeInApp.isIndexing = false;
            StatusChangeInApp.isStopIndexing = false;
            return ResponsesOfApp.getErrorResponse("Ошибка работы с базой данных", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (YamlDataException e) {
            e.printStackTrace();
            StatusChangeInApp.isIndexing = false;
            StatusChangeInApp.isStopIndexing = false;
            return ResponsesOfApp.getErrorResponse("Файл application.yaml не содержит данных", HttpStatus.BAD_REQUEST);
        } catch (IsIndexingException e) {
            e.printStackTrace();
            return ResponsesOfApp.getErrorResponse(e.message, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            StatusChangeInApp.isIndexing = false;
            StatusChangeInApp.isStopIndexing = false;
            try {
                AccessToTheDB.getAccessToTheDB().executeOperation("UPDATE sites SET status = 'FAILED' WHERE status = 'INDEXING'");
            } catch (SQLException ignored) {}
            return ResponsesOfApp.getErrorResponse("Other exception", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping ("/stopIndexing")
    public ResponseEntity<ObjectNode> stopIndexing() {

        try {
            dbCheckAndSetup();
            StatusChangeInApp.toIsStopIndexingTrue();
            return ResponsesOfApp.getSuccessResponse();

        } catch (IOException e) {
            e.printStackTrace();
            return ResponsesOfApp.getErrorResponse("Ошибка соединения с yaml", HttpStatus.NOT_FOUND);
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponsesOfApp.getErrorResponse("Ошибка работы с базой данных", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (IsIndexingException e) {
            e.printStackTrace();
            return ResponsesOfApp.getErrorResponse(e.message, HttpStatus.OK);
        }
    }

    @PostMapping ("/indexPage")
    @ResponseBody
    public ResponseEntity<ObjectNode> indexPage(@RequestParam String url) {

        try {
            dbCheckAndSetup();
            StatusChangeInApp.toIsIndexingTrue();
            if (Objects.equals(url, "")) throw new AdminException("Введен пустой запрос");
            getUserAgent();

            PreparedStatement pstSelectPagesId = AppSEController.dbConnection.prepareStatement("SELECT pages_id FROM pages WHERE path = ?");
            pstSelectPagesId.setString(1, url);
            pstSelectPagesId.addBatch();
            ResultSet rsPagesId = AccessToTheDB.getAccessToTheDB().executeSelection(pstSelectPagesId);
            int i = 0;
            while (rsPagesId.next()) i++;
            if (i == 0) throw new AdminException("Данной страницы нет в базе данных");
            if (i != 1) throw new AdminException("В базе данных несколько одинаковых страниц, проверьте файл yaml");
            rsPagesId.close();
            pstSelectPagesId.close();

            AccessToTheDB.getAccessToTheDB().executeOperation("SET FOREIGN_KEY_CHECKS=0");
            CountDownLatch cdl = new CountDownLatch(1);
            new Thread(new main.TaskSiteIndexing(url, "one-page indexing or reindexing", 1, cdl)).start();

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode successResponse = mapper.createObjectNode();
            successResponse.put("result", true);
            return new ResponseEntity<>(successResponse, HttpStatus.OK);

        } catch (IOException e) {
            e.printStackTrace();
            StatusChangeInApp.isIndexing = false;
            StatusChangeInApp.isStopIndexing = false;
            return ResponsesOfApp.getErrorResponse("Ошибка соединения с yaml", HttpStatus.NOT_FOUND);
        } catch (SQLException e ) {
            e.printStackTrace();
            StatusChangeInApp.isIndexing = false;
            StatusChangeInApp.isStopIndexing = false;
            return ResponsesOfApp.getErrorResponse("Ошибка работы с базой данных", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (AdminException e) {
            e.printStackTrace();
            StatusChangeInApp.isIndexing = false;
            StatusChangeInApp.isStopIndexing = false;
            return ResponsesOfApp.getErrorResponse(e.message, HttpStatus.OK);
        } catch (IsIndexingException e) {
            return ResponsesOfApp.getErrorResponse(e.message, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            StatusChangeInApp.isIndexing = false;
            StatusChangeInApp.isStopIndexing = false;
            try {
                AccessToTheDB.getAccessToTheDB().executeOperation("UPDATE sites SET status = 'FAILED' WHERE status = 'INDEXING'");
            } catch (SQLException ignored) {}
            return ResponsesOfApp.getErrorResponse("Other exception", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping ("/statistics")
    public ResponseEntity<ObjectNode> getStatistics() {

        try {
            dbCheckAndSetup();
            StatisticsSystem statisticsSystem = new StatisticsSystem();
            return new ResponseEntity<>(statisticsSystem.getObjNodeStatistic(), HttpStatus.OK);

        } catch (IOException e) {
            e.printStackTrace();
            return ResponsesOfApp.getErrorResponse("Ошибка соединения с yaml", HttpStatus.NOT_FOUND);
        } catch (SQLException e ) {
            e.printStackTrace();
            return ResponsesOfApp.getErrorResponse("Ошибка работы с базой данных", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping ("/search")
    public ResponseEntity<ObjectNode> search(@RequestParam String query, @RequestParam (required = false) String site,
                                             @RequestParam (required = false) Integer offset,
                                             @RequestParam (required = false) Integer limit) {

        try {
            dbCheckAndSetup();
            if (StatusChangeInApp.isIndexing) throw new UserException("Идет переиндексация сайта");

            List<String> siteUrlList = new ArrayList<>();
            if (site == null) siteUrlList.add("all");
            else siteUrlList.add(site);

            SearchSystem searchSystem = new SearchSystem(offset, limit);
            return new ResponseEntity<>(searchSystem.search(siteUrlList, query), HttpStatus.OK);

        } catch (IOException e) {
            e.printStackTrace();
            return ResponsesOfApp.getErrorResponse("Ошибка соединения с yaml", HttpStatus.NOT_FOUND);
        } catch (SQLException e ) {
            e.printStackTrace();
            return ResponsesOfApp.getErrorResponse("Ошибка работы с базой данных", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (UserException e) {
            e.printStackTrace();
            return ResponsesOfApp.getErrorResponse(e.message, HttpStatus.OK);
        }

    }

    YamlData getYamlData() throws IOException {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setVisibility(VisibilityChecker.Std.defaultInstance().withFieldVisibility(JsonAutoDetect.Visibility.ANY));
        return mapper.readValue(new File(APPLICATION_PATCH), YamlData.class);
    }

    void getDbConnection() throws IOException, SQLException {

        YamlData yamlData = getYamlData();
        dbConnection = DriverManager.getConnection(yamlData.url, yamlData.username, yamlData.password);
    }

    void getUserAgent() throws IOException {

        if (userAg == null || ref == null) {
            userAg = getYamlData().userAg;
            ref = getYamlData().ref;
            if (userAg == null || ref == null) {
                userAg = "";
                ref = "";
            }
        }
    }

    synchronized  void dbCheckAndSetup() throws SQLException, IOException {

        if (StatusChangeInApp.isDbReady) return;

        if (dbConnection == null) getDbConnection();
        DatabaseMetaData databaseMetaData = dbConnection.getMetaData();
        ResultSet rsIsTableFields = databaseMetaData.getTables(null, null, "fields", null);
        ResultSet rsIsTablePages = databaseMetaData.getTables(null, null, "pages", null);
        ResultSet rsIsTableLemmas = databaseMetaData.getTables(null, null, "lemmas", null);
        ResultSet rsIsTableIndexes = databaseMetaData.getTables(null, null, "indexes", null);
        ResultSet rsIsTableSites = databaseMetaData.getTables(null, null, "sites", null);
        if (!rsIsTableFields.next() || !rsIsTablePages.next() || !rsIsTableLemmas.next() ||
                !rsIsTableIndexes.next() || !rsIsTableSites.next()) new DataBaseSE();

        AccessToTheDB.getAccessToTheDB().executeOperation("UPDATE sites SET status = 'FAILED' WHERE status = 'INDEXING'");

        StatusChangeInApp.isDbReady = true;
    }

}
