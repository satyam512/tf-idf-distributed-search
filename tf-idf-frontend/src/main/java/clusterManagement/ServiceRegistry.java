package clusterManagement;


/**
 * need to change the REGISTRY_ZNODE to parameter value passed in constructor i.e. serviceRegistryZnode
 * this is required to be able to make multiple service registries, based on the registryPathName
 */
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ServiceRegistry implements Watcher {

    public static final String WORKERS_REGISTRY_ZNODE = "/workers_service_registry";
    public static final String COORDINATORS_REGISTRY_ZNODE = "/coordinators_service_registry";
    private final ZooKeeper zooKeeper;
    private String currentZnodeName; // basically full path of current znode

    private List<String> listOfAddresses; // now this is supposed to unmodifiable
    private final String serviceRegistryZnode;
    private final Random random;

    public ServiceRegistry(ZooKeeper zooKeeper, String serviceRegistryZnode) throws KeeperException, InterruptedException {
        this.zooKeeper = zooKeeper;
        this.serviceRegistryZnode = serviceRegistryZnode;
        random = new Random();
        if(zooKeeper.exists(this.serviceRegistryZnode, false) == null) {
            zooKeeper.create(this.serviceRegistryZnode, new byte[] {}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
    }

    public void registerForUpdates() {
        try {
            this.updateAddresses();
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public void registerToCluster(String metadata) throws KeeperException, InterruptedException {
        if(this.currentZnodeName!=null) {
            System.out.println("Already registered to service registry");
            return;
        }
        this.currentZnodeName = zooKeeper.create(serviceRegistryZnode + "/n_", metadata.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        System.out.println("Registered to service registry as " + currentZnodeName);
    }

    public void unRegisterFromCluster() {
        try {
            if(currentZnodeName!=null && zooKeeper.exists(currentZnodeName, false)!=null)
                zooKeeper.delete(currentZnodeName, -1);
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public synchronized List<String> getAllServiceAddresses () throws KeeperException, InterruptedException {
        if(this.listOfAddresses==null)
            this.updateAddresses();
        return this.listOfAddresses;
    }
    public synchronized void updateAddresses() throws KeeperException, InterruptedException {
        List<String> allAddresses = new ArrayList<>();
        List<String> workerNodes = zooKeeper.getChildren(serviceRegistryZnode, this);
        // with names of all cluster nodes, now to get the metadata from their corresponding zk nodes
        for(String  name : workerNodes) {
            Stat child = zooKeeper.exists(serviceRegistryZnode + "/" + name, false);
            if(child == null)
                continue;

            byte[] addressBytes = zooKeeper.getData(serviceRegistryZnode + "/" + name, false, child);
            String address = new String(addressBytes);
            allAddresses.add(address);
            // it should not be communicating with itself right ?? // doesn't matter ?? It does, that's why un-registration for Leader node
        }
        this.listOfAddresses = Collections.unmodifiableList(allAddresses);
    }

    public synchronized String getRandomServiceAddress() throws KeeperException, InterruptedException {
        if (listOfAddresses == null) {
            updateAddresses();
        }
        if (!listOfAddresses.isEmpty()) {
            int randomIndex = random.nextInt(listOfAddresses.size());
            return listOfAddresses.get(randomIndex);
        } else {
            return null;
        }
    }
    @Override
    public void process(WatchedEvent watchedEvent) {
        switch (watchedEvent.getType()) {
            case NodeChildrenChanged:
                try {
                    updateAddresses();  // since these are one time triggers so we need to keep  Re:registering them
                } catch (KeeperException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
        }
    }
}
