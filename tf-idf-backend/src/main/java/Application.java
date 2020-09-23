import clusterManagement.LeaderElection;
import clusterManagement.ServiceRegistry;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;

public class Application implements Watcher {
    private static final int DEFAULT_PORT = 8080;
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final int SESSION_TIMEOUT = 3000;

    private ZooKeeper zooKeeper;

    // clusterManagement.LeaderElection is only to get the leader znode from zooKeeper, service Discovery part will be used to establish communication with this leader
    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        int currPort = args.length == 0 ? DEFAULT_PORT : Integer.parseInt(args[0]);
        Application application = new Application();
        application.connectToZookeeper();

        ServiceRegistry workersServiceRegistry = new ServiceRegistry(application.getZooKeeper(), ServiceRegistry.WORKERS_REGISTRY_ZNODE);
        ServiceRegistry coordinatorServiceRegistry = new ServiceRegistry(application.getZooKeeper(), ServiceRegistry.COORDINATORS_REGISTRY_ZNODE);

        OnElectionAction onElectionCallAction = new OnElectionAction(currPort, workersServiceRegistry, coordinatorServiceRegistry);

        LeaderElection leaderElection = new LeaderElection(application.getZooKeeper(), onElectionCallAction);

        leaderElection.volunteerForLeadership();
        leaderElection.reElection();

        application.run();
        application.close();
    }
    public void connectToZookeeper() throws IOException {
        this.zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
    }

    public void run() throws InterruptedException {
        synchronized (zooKeeper) {
            zooKeeper.wait();
        }
    }

    public void close() throws InterruptedException {
        zooKeeper.close();
    }

    @Override
    public void process(WatchedEvent watchedEvent) {
        switch (watchedEvent.getType()) {
            case None:
                if(watchedEvent.getState()== Event.KeeperState.SyncConnected) {
                    System.out.println("Synchronized and connected to Zookeeper server");
                }
                else{ // if (watchedEvent.getState() == Event.KeeperState.Disconnected) {
                    synchronized (zooKeeper) { // some other thread might be using zookeeper client API somewhere
                        zooKeeper.notifyAll();  // on disconnection it will wake up all threads sleeping on zookeeper client object
                    }
                }
                break;
        }
    }

    public ZooKeeper getZooKeeper() {
        return zooKeeper;
    }

}
