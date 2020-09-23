package networking;

public interface OnRequestCallback {
    byte [] handleRequest(byte [] requestPayload); // takes an http request body and prepares, returns the response body
    String getEndpoint(); // returns the path to http request end point
}
