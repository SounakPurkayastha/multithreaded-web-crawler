package webcrawler;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.JedisPooled;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {

        Properties props = new Properties();
        try(InputStream input = Main.class.getClassLoader().getResourceAsStream("config.properties")) {
            if(input == null)
                throw new IOException("Could not find config.properties");
            props.load(input);
        } catch(IOException e) {
            logger.error("Failed to load config.properties");
        }

        int threadCount = Integer.parseInt(props.getProperty("crawler.thread.count"));
        int maxPages = Integer.parseInt(props.getProperty("crawler.max.pages"));
        String seedUrl = props.getProperty("crawler.seed.url");
        String redisHost = props.getProperty("crawler.redis.host");
        int redisPort = Integer.parseInt(props.getProperty("crawler.redis.port"));
        String redisKey = props.getProperty("crawler.redis.key");
        Boolean cleanStart = Boolean.parseBoolean(props.getProperty("crawler.clean.start"));

        
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();        // uses two locks, one for put, other for take
                                                                                // can be initialized with fixed capacity to prevent Out 
                                                                                // of memory error
        AtomicInteger pagesCrawled = new AtomicInteger(0);
        AtomicLong totalBytes = new AtomicLong(0);
        AtomicLong totalTimeMillis = new AtomicLong(0);

        JedisPooled jedis = new JedisPooled(redisHost, redisPort);       // using pool because it is unsafe to share single connection among threads

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        Set<String> visited = ConcurrentHashMap.newKeySet();
        
        HttpClient client = HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .connectTimeout(Duration.ofSeconds(5))
                                .build();


        if(cleanStart) {
            jedis.del(redisKey);
            logger.info("Clean start, deleted Redis key:{}", redisKey);
        }

        queue.add(seedUrl);

        while(pagesCrawled.get() < maxPages) {    
            try{
                // String url = queue.take();                      // blocking call, waits forever if queue is empty
                String url = queue.poll(5, TimeUnit.SECONDS);      // waits for fixed time, if time runs out, returns false
                if(url != null)
                    executorService.execute(new CrawlTask(url, jedis, queue, pagesCrawled, maxPages, client, totalBytes, totalTimeMillis, redisKey));  // submits task to be executed
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

        logger.info("--- Crawl Summary ---");
        logger.info("Total Pages: {}", pagesCrawled.get());
        logger.info("Data Downloaded: {} KB", totalBytes.get() / 1024);
        if (pagesCrawled.get() > 0) {
            logger.info("Avg Time Per Page: {} ms", totalTimeMillis.get() / pagesCrawled.get());
        }

        System.out.println("Crawl complete");
    }

}
