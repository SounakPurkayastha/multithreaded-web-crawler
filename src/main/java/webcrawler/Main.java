package webcrawler;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    public static void main(String[] args) {
        
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        AtomicInteger pagesCrawled = new AtomicInteger(0);
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        Set<String> visited = ConcurrentHashMap.newKeySet();

        // HttpClient.Redirect.NEVER    -->   (Default) Does not follow redirects. You must handle them manually.
        // HttpClient.Redirect.ALWAYS   -->   Always follows redirects, even from HTTPS to HTTP (less secure).
        // HttpClient.Redirect.NORMAL   -->   Follows redirects unless they go from HTTPS to HTTP.
        HttpClient client = HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .connectTimeout(Duration.ofSeconds(5))
                                .build();


        queue.add("https://en.wikipedia.org/wiki/Java_(programming_language)");

        while(pagesCrawled.get() < 10) {    
            // System.out.println(pagesCrawled);    // only crawl 10 pages
            try{
                // String url = queue.take();   blocking call, waits forever if queue is empty
                String url = queue.poll(5, TimeUnit.SECONDS);
                if(url != null)
                    executorService.execute(new CrawlTask(url, visited, queue, pagesCrawled, client));  // submits task to be executed
            } catch(InterruptedException e) {
                e.printStackTrace();
            }
        }

        executorService.shutdown(); // Stop accepting new tasks, but let the workers finish the ones currently in the queue
       
        try {
            if(!executorService.awaitTermination(60, TimeUnit.SECONDS)) {  // I will wait here for up to 60 seconds for the workers
                executorService.shutdownNow();                             //  to finish. If they aren't done by then, I'm moving on.
            }                                                                         
        } catch(InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Crawl complete");
    }

}
