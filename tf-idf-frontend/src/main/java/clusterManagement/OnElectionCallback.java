package clusterManagement;

public interface OnElectionCallback {
    void onLeaderElection();
    void onWorkerElection();
}
