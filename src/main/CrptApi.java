import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CrptApi {

    private final int requestLimit;
    private final TimeUnit timeUnit;
    private final AtomicInteger requestCount;
    private final ReentrantLock lock;
    private final ScheduledExecutorService scheduler;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.requestCount = new AtomicInteger(0);
        this.lock = new ReentrantLock(true);
        this.scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::resetRequestCount, 0, 1, timeUnit);
    }

    private void resetRequestCount() {
        requestCount.set(0);
    }

    public void createDocument(Object document, String signature) throws Exception {
        lock.lock();
        try {
            while (requestCount.get() >= requestLimit) {
                lock.unlock();
                Thread.sleep(timeUnit.toMillis(1));
                lock.lock();
            }
            requestCount.incrementAndGet();
        } finally {
            lock.unlock();
        }

        HttpClient client = HttpClient.newHttpClient();

        ObjectMapper mapper = new ObjectMapper();
        String jsonDocument = mapper.writeValueAsString(document);

        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("document", jsonDocument);
        requestBody.put("signature", signature);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(System.out::println)
                .join();
    }

    public static void main(String[] args) throws Exception {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 5);

        ObjectNode document = new ObjectMapper().createObjectNode();
        ObjectNode description = document.putObject("description");
        description.put("participantInn", "string");
        document.put("doc_id", "string");
        document.put("doc_status", "string");

        ObjectNode product = document.putArray("products").addObject();
        product.put("certificate_document", "string");
        product.put("certificate_document_date", "2020-01-23");

        String signature = "example-signature";
        api.createDocument(document, signature);
    }
}