package clusterManagement;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import java.util.Collections;
import java.util.List;

public class LeaderElection implements Watcher {

    private static final String ELECTION_NAMESPACE = "/election";
    private ZooKeeper zooKeeper;
    private String currentZnodeName;
    private OnElectionCallback onElectionCallback; // from constructor

    public LeaderElection(ZooKeeper zooKeeper, OnElectionCallback onElectionCallback) {
        this.onElectionCallback = onElectionCallback;
        this.zooKeeper = zooKeeper;
    }

    public void volunteerForLeadership() throws KeeperException, InterruptedException {
       String znodeFullPath = zooKeeper.create(ELECTION_NAMESPACE + "/c_", new byte[] {}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
       // this above znodeFullPath is well, the full path i.e /election/c_"something" whatever so extracting the c_"something" part by doing following

       this.currentZnodeName = znodeFullPath.replace(ELECTION_NAMESPACE + "/", ""); // i.e replacing target by replacement
       System.out.println("New worker node created at : " + currentZnodeName);
    }

    public void reElection() throws KeeperException, InterruptedException {

        String predecessorZnodeName = "";
        Stat predecessorZnodeStats = null;

        while (predecessorZnodeStats == null) {
            List<String> children = zooKeeper.getChildren(ELECTION_NAMESPACE, false);
            Collections.sort(children);
            String smallestChild = children.get(0);
            if (smallestChild.equals(currentZnodeName)) {
                System.out.println("I'm the leader");
                onElectionCallback.onLeaderElection();
                return;
            }
            System.out.println("I'm not the leader, " +  smallestChild +" is the leader");
            int currIndex = Collections.binarySearch(children, currentZnodeName);
            predecessorZnodeName = children.get(currIndex - 1);
            predecessorZnodeStats = zooKeeper.exists(ELECTION_NAMESPACE + "/" + predecessorZnodeName, this);
        }
        onElectionCallback.onWorkerElection();
        System.out.println("Watching Znode " + predecessorZnodeName);
    }

    @Override
    public void process(WatchedEvent watchedEvent) {
        switch (watchedEvent.getType()) {
            case NodeDeleted:
                try {
                    this.reElection();
                } catch (KeeperException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
        }
    }
}
