import clusterManagement.OnElectionCallback;
import clusterManagement.ServiceRegistry;
import networking.WebClient;
import networking.WebServer;
import org.apache.zookeeper.KeeperException;
import search.SearchCoordinator;
import search.SearchWorker;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class OnElectionAction implements OnElectionCallback {

    private final ServiceRegistry workerServiceRegistry;
    private final ServiceRegistry coordinatorServiceRegistry;
    private final int port;
    private WebServer webServer;
    public OnElectionAction(int port, ServiceRegistry workerServiceRegistry, ServiceRegistry coordinatorServiceRegistry) {
        this.workerServiceRegistry = workerServiceRegistry;
        this.port = port;
        this.coordinatorServiceRegistry = coordinatorServiceRegistry;
    }

    @Override
    public void onLeaderElection() {
        // un registerFromServiceRegistry
        // now registerForUpdates // remember about " it should not be communicating with itself right ?? " clearly registering for updates after unregistering from service registry
        workerServiceRegistry.unRegisterFromCluster();
        workerServiceRegistry.registerForUpdates();
        SearchCoordinator searchCoordinator = new SearchCoordinator(workerServiceRegistry, new WebClient()); // Actually when we get a request from front-end server, this leader node must become a client and request for results from worker nodes, therefore we need to implement an http client in this coordinator node

        if (webServer != null) {
            webServer.stop();
        }

        webServer = new WebServer(port, searchCoordinator);
        webServer.startServer();

        try {
            String currentServerAddress = String.format("http://%s:%d%s", InetAddress.getLocalHost().getCanonicalHostName(), port, searchCoordinator.getEndpoint());
            coordinatorServiceRegistry.registerToCluster(currentServerAddress);

        } catch (KeeperException | InterruptedException | UnknownHostException e) {
            e.printStackTrace();
            return;
        }

    }

    @Override
    public void onWorkerElection() {

        SearchWorker searchWorker = new SearchWorker();
        if (webServer == null) {
            webServer = new WebServer(port, searchWorker);
            webServer.startServer();
        }
        //register to workerServiceRegistry // remember a worker node doesn't need to communicate with anyone except leader
        try {
            String currentServerAddress = String.format("http://%s:%d%s", InetAddress.getLocalHost().getCanonicalHostName(), port, searchWorker.getEndpoint());
            workerServiceRegistry.registerToCluster(currentServerAddress);

        } catch (KeeperException | InterruptedException | UnknownHostException e) {
            e.printStackTrace();
            return;
        }
    }
}
