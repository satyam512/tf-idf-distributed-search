package networking;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class WebClient {
    private HttpClient httpClient;

    public WebClient() {
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(); // building a new Http client, keeping other properties same
    }

    public CompletableFuture<byte[]> sendTask(String url, byte []requestPayload) {
        // first we build the request.
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestPayload))
                .uri(URI.create(url))
                .build(); // now this is a POST request
        // then we send the request asynchronously and extracts the response body in byte array, then deserialize the body
        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray()) //
                .thenApply(HttpResponse::body);
    }
}


