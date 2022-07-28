
package main;
import main.Exceptions.IsIndexingException;

public class StatusChangeInApp {

    static volatile boolean isStopIndexing = false;
    static volatile boolean isIndexing = false;
    static volatile boolean isDbReady = false;

    static synchronized void toIsIndexingTrue() throws IsIndexingException, InterruptedException {

        if (StatusChangeInApp.isIndexing) throw new IsIndexingException("Индексация уже запущена");
        StatusChangeInApp.isStopIndexing = false;
        StatusChangeInApp.isIndexing = true;
        Thread.sleep(1000); //время на завершение запущенных поисков
    }

    static synchronized void toIsStopIndexingTrue() throws IsIndexingException {

        if (!StatusChangeInApp.isIndexing) throw new IsIndexingException("Индексация не запущена");
        StatusChangeInApp.isStopIndexing = true;
    }

}
