package webcrawler;

import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class CrawlTask implements Runnable {

    private final String url;
    private final Set<String> visited;
    private final LinkedBlockingQueue<String> queue;
    private final AtomicInteger pagesCrawled;

    public CrawlTask(String url, Set<String> visited, LinkedBlockingQueue<String> queue, AtomicInteger pagesCrawled) {
        this.url = url;
        this.visited = visited;
        this.queue = queue;
        this.pagesCrawled = pagesCrawled;
    }

    public void run() {
        if(visited.add(url)) {
            queue.add(url + "/link-" + pagesCrawled.get());
            pagesCrawled.incrementAndGet();
            System.out.println(Thread.currentThread().getName() + " is crawling " + url);
            try {
                Thread.sleep(500);
            } catch(InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(Thread.currentThread().getName() + " completed crawling " + url);
        }
    }

}