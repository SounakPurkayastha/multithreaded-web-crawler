package webcrawler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.JedisPooled;

public class CrawlTask implements Runnable {

    private final String url;
    private final JedisPooled jedis;
    private final LinkedBlockingQueue<String> queue;
    private final AtomicInteger pagesCrawled;
    private final AtomicLong totalBytes;
    private final AtomicLong totalTimeMillis;
    private final int maxPages;
    private final String redisKey;
    private final HttpClient client;

    private static final Logger logger = LoggerFactory.getLogger(CrawlTask.class);

    public CrawlTask(String url, JedisPooled jedis, LinkedBlockingQueue<String> queue, AtomicInteger pagesCrawled, int maxPages, HttpClient client, AtomicLong totalBytes, AtomicLong totalTimeMillis, String redisKey) {
        this.url = url;
        this.jedis = jedis;
        this.queue = queue;
        this.pagesCrawled = pagesCrawled;
        this.client = client;
        this.totalBytes = totalBytes;
        this.totalTimeMillis = totalTimeMillis;
        this.redisKey = redisKey;
        this.maxPages = maxPages;
    }

    @Override
    public void run() {
        if(pagesCrawled.get() >= maxPages)
            return;
        if(jedis.sadd(redisKey, url) == 1) {  // adds element to set if not already present and returns true else false
            try {
                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create(url))
                                        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")  
                                        .timeout(Duration.ofSeconds(10)) 
                                        .build();

                logger.info("Fetching url:{}",url);

                long startReq = System.currentTimeMillis();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                long endReq = System.currentTimeMillis();

                if(response.statusCode() == 200) {

                    int currentCount = pagesCrawled.incrementAndGet();

                    if(currentCount > maxPages) {
                        pagesCrawled.decrementAndGet();     // roll back increment
                        jedis.srem(redisKey, url);
                        return;
                    }

                    String body = response.body();
                    // totalBytes.addAndGet(body.getBytes().length);       // creates new byte array
                    totalBytes.addAndGet(body.length());
                    totalTimeMillis.addAndGet(endReq - startReq);
                    Document doc = Jsoup.parse(body);
                    Elements links = doc.select("a[href]");

                    for(Element link : links) {

                        if(pagesCrawled.get() >= maxPages)
                            return;

                        String discoveredUrl = link.attr("abs:href");
                        if(!discoveredUrl.isBlank()) {
                            // queue.add(discoveredUrl);

                            // queue.put(discoveredUrl);   // If the queue is full, the thread pauses until space becomes available. 
                                                        // Consumes 0 CPU cycles while paused in Blocked queue.
                                                        // Any thread that takes from the queue signals to it to wake up.
                                                        // Might wait forever

                            queue.offer(discoveredUrl, 5, TimeUnit.SECONDS);     // if queue is full wait 5 seconds
                                                                                    // if time runs out return false
                        }
                    }

                    // pagesCrawled.incrementAndGet();  // not really checking here

                    logger.info("Crawled {}", url);   // blocking operation
                }
                else {
                    logger.error("Skipped {} Status Code:{}", url, response.statusCode());
                }

            } catch(Exception e) {
                e.printStackTrace();
            } 
        } else {
            logger.debug("Redis skip: Already visited {}", url);
        }
    }

}