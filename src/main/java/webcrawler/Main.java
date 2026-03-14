package webcrawler;

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


        queue.add("www.metal-archives.net");

        while(pagesCrawled.get() < 10) {
            try{
                // String url = queue.take();   blocking call, waits forever if queue is empty
                String url = queue.poll(5, TimeUnit.SECONDS);
                if(url != null)
                    executorService.execute(new CrawlTask(url, visited, queue, pagesCrawled));
            } catch(InterruptedException e) {
                e.printStackTrace();
            }
        }

        executorService.shutdown(); // Stop accepting new tasks, but let the workers finish the ones currently in the queue
       
        try {
            executorService.awaitTermination(10, TimeUnit.SECONDS); // I will wait here for up to 10 seconds for the workers to finish. If they aren't done by then, I'm moving on.
        } catch(InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Crawl complete");
    }

}
