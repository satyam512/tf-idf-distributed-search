package networking;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.Executors;

public class WebServer {

    private static final String STATUS_ENDPOINT = "/status";

    private HttpServer httpServer;  // see this there's a create method to create the we server listening at a port
    private final int port;

    private final OnRequestCallback onRequestCallback;

    public WebServer(int port, OnRequestCallback onRequestCallback) {
        this.port = port;
        this.onRequestCallback = onRequestCallback;
    }

    public void startServer() {
        try {
            this.httpServer = HttpServer.create(new InetSocketAddress(this.port), 0); // backlog is basically how many requests can be kept in queue in case all allocated threads are busy. 0 sets it to system default.
        } catch (IOException e) {
            e.printStackTrace();
        }
        // creates an Http server which would be listening to this given Socket Address(here on localhost, and given port)
        // now it needs contexts, i.e. URL which the server will serve it's contents

        httpServer.createContext(STATUS_ENDPOINT, this::getRequestHandler); // will need a handler to handle requests to this context, which will take an exchange object
        httpServer.createContext(onRequestCallback.getEndpoint(), this::postRequestHandler);  // getting the endpoint from search worker node

        httpServer.setExecutor(Executors.newFixedThreadPool(8)); // now the number of available threads to handle multiple requests at the same time
        httpServer.start(); // finally we start the created server
    }

    private void getRequestHandler(HttpExchange exchange) throws IOException { // will get a GET request and send the status as a response , both req, res are in this exchange object created by our server
        if(!exchange.getRequestMethod().equalsIgnoreCase("get")){
            exchange.close();
            return;
        }

        String responseMessage = "Server is alive";
        sendResponse(responseMessage.getBytes(), exchange);
    }


    private void postRequestHandler(HttpExchange exchange) throws IOException { // will send the task output in
        if (!exchange.getRequestMethod().equalsIgnoreCase("post")) {
            exchange.close();
            return;
        }

        Headers headers = exchange.getRequestHeaders();
//        if (headers.containsKey("X-test") && headers.get("X-test").get(0).equalsIgnoreCase("true")) {
//            String response = "123\n";
//            sendResponse(response.getBytes(), exchange);
//            return;
//        } // can break stuff like format things so avoid this

        boolean inDebugMode = false;

        if (headers.containsKey("X-Debug") && headers.get("X-Debug").get(0).equalsIgnoreCase("true")) {
            inDebugMode = true;
        }

        long startTime = System.nanoTime();

        byte [] responseBody = onRequestCallback.handleRequest(exchange.getRequestBody().readAllBytes()); // basically now any business logic can be hooked in so that we can process the incoming request payload
        long endTime = System.nanoTime();

        if(inDebugMode){
            long diff = endTime - startTime;
            String debugMessage = String.format("Operation took %s ns ", diff);
            exchange.getResponseHeaders().put("X-Debug-Info", Arrays.asList(debugMessage));
        }
        sendResponse(responseBody, exchange);
    }

    private void sendResponse(byte[] responseMessage , HttpExchange exchange) throws IOException {
        //System.out.println("Sending response" + responseMessage.length);
        exchange.sendResponseHeaders(200, responseMessage.length);

        OutputStream outputStream = exchange.getResponseBody();
        outputStream.write(responseMessage);
        outputStream.flush();
        outputStream.close();
    }

    public void stop() {
        httpServer.stop(7);
    }
}
