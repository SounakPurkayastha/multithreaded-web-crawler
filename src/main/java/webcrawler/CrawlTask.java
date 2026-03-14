package webcrawler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class CrawlTask implements Runnable {

    private final String url;
    private final Set<String> visited;
    private final LinkedBlockingQueue<String> queue;
    private final AtomicInteger pagesCrawled;
    private final HttpClient client;

    public CrawlTask(String url, Set<String> visited, LinkedBlockingQueue<String> queue, AtomicInteger pagesCrawled, HttpClient client) {
        this.url = url;
        this.visited = visited;
        this.queue = queue;
        this.pagesCrawled = pagesCrawled;
        this.client = client;
    }

    public void run() {
        if(pagesCrawled.get() >= 10)
            return;
        if(visited.add(url)) {  // adds element to set if not already present and returns true else false
            try {
                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create(url))
                                        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36") // request rejected without proper User-Agent 
                                        .timeout(Duration.ofSeconds(10)) // read timeout, different from connect timeout
                                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if(response.statusCode() == 200) {
                    Document doc = Jsoup.parse(response.body());
                    Elements links = doc.select("a[href]");

                    for(Element link : links) {
                        String discoveredUrl = link.attr("abs:href");
                        if(!discoveredUrl.isBlank() && pagesCrawled.get() < 10) {
                            // queue.add(discoveredUrl);
                            queue.put(discoveredUrl);   // blocks if queue is bounded and full
                            // System.out.println(discoveredUrl + " added to queue.");
                        }
                    }

                    pagesCrawled.incrementAndGet();
                    System.out.println("Crawled " + url);
                }
                else {
                    System.out.println("Skipped " + url + " Status Code:" + response.statusCode());
                }

            } catch(Exception e) {
                e.printStackTrace();
            } 
        }
    }

}