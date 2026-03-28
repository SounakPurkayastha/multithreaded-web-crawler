package webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import redis.clients.jedis.JedisPooled;

@ExtendWith(MockitoExtension.class)         // tells JUnit to inititalize mockito. Without it all @Mock objects would be null.
public class CrawlTaskTest {
        @Mock private JedisPooled mockJedis;    // empty objects
        @Mock private HttpClient mockClient;
        @Mock private HttpResponse<String> mockResponse;

        private LinkedBlockingQueue<String> queue;
        private AtomicInteger pagesCrawled;
        private AtomicLong totalBytes;
        private AtomicLong totalTime;

        @BeforeEach          // method runs before every single test, ensures the next test starts with a "clean slate.", prevents tests from "leaking" into each other
        void setUp() {
            queue = new LinkedBlockingQueue<>();
            pagesCrawled = new AtomicInteger(0);
            totalBytes = new AtomicLong(0);
            totalTime = new AtomicLong(0);
        }

        @Test               // signal that tells the build tool (Maven) and the IDE (IntelliJ/Eclipse) that a specific method is a runnable unit test.
        void shouldExtractLinksAndIncrementCounter() throws IOException, InterruptedException {

            String url = "https://test.com";
            String html = "<html><body><a href='https://news.com'>News</a></body></html>";

            when(mockJedis.sadd(anyString(), eq(url))).thenReturn(1L);          // because sadd returns long
            when(mockResponse.statusCode()).thenReturn(200);
            when(mockResponse.body()).thenReturn(html);
            when(mockClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

            CrawlTask task = new CrawlTask(url, mockJedis, queue, pagesCrawled, 10, mockClient, totalBytes, totalTime, "test-key");

            task.run();

            assertEquals(1, pagesCrawled.get(), "Should have incremented crawl count");
            assertEquals(1, queue.size(), "Should have discovered one new URL");
            assertEquals("https://news.com", queue.poll(), "Should have extracted correct link");
            verify(mockJedis).sadd(anyString(), eq(url));   // It ensures that your application actually attempted to call the sadd command
                                                            // By using eq(url), the test ensures that the exact value stored in your url variable was the one sent to Redis
                                                            // Checks the Process (Did the code actually try to save to Redis?).
    }

    @Test
    void shouldHandle404ErrorWithoutIncrementCounter() throws IOException, InterruptedException {

        String url = "https://www.test.com";

        when(mockJedis.sadd(anyString(), eq(url))).thenReturn(1L);
        when(mockClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(404);
        

        CrawlTask task = new CrawlTask(url, mockJedis, queue, pagesCrawled, 10, mockClient, totalBytes, totalTime, "test-key");

        task.run();

        assertEquals(0, pagesCrawled.get());
        assertEquals(0, queue.size());
        verify(mockJedis).sadd(anyString(), eq(url));
    }

    @Test
    void shouldHandleNetworkTimeoutGracefully() throws Exception {

        String url = "https://www.test.com";

        when(mockJedis.sadd(anyString(), eq(url))).thenReturn(1L);
        when(mockClient.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("Connection Timeout"));

        CrawlTask task = new CrawlTask(url, mockJedis, queue, pagesCrawled, 10, mockClient, totalBytes, totalTime, "test-key");
        
        task.run();

        assertEquals(0, pagesCrawled.get());
        assertTrue(queue.isEmpty());
        verify(mockJedis).sadd(anyString(), eq(url));
    }
}
