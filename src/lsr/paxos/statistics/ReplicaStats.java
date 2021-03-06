package lsr.paxos.statistics;

import java.io.IOException;
import java.util.HashMap;

import lsr.common.ProcessDescriptor;

public class ReplicaStats {
    /** Singleton */
    private static ReplicaStats instance;

    public static ReplicaStats initialize(int n, int localID) throws IOException {
        // assert instance == null : "Already initialized";
        if (ProcessDescriptor.getInstance().benchmarkRun) {
            instance = new ReplicaStatsFull(n, localID);
        } else {
            instance = new ReplicaStats();
        }
        return instance;
    }

    public static ReplicaStats getInstance() {
        return instance;
    }

    /* Stub implementation. For non-benchmark runs */
    public void consensusStart(int cid, int size, int k, int alpha) {
    }

    public void retransmit(int cid) {
    }

    public void consensusEnd(int cid) {
    }

    public void advanceView(int newView) {
    }
}

/*
 * Full implementation.
 */
final class ReplicaStatsFull extends ReplicaStats {

    static final class Instance implements Comparable<Instance> {
        public final long start;
        public final int cid;
        /** Number of requests ordered on this instance/batch */
        public final int nRequests;
        public final int valueSize;
        /** Number of instances active at the time this instance was started */
        public final int alpha;

        public long end;
        public int retransmit = 0;

        public Instance(int cid, long firstStart, int valueSize, int nRequests, int alpha) {
            this.cid = cid;
            this.start = firstStart;
            this.nRequests = nRequests;
            this.valueSize = valueSize;
            this.alpha = alpha;
        }

        public int compareTo(Instance o) {
            long thisVal = this.start;
            long anotherVal = o.start;
            return (thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Instance)) {
                return false;
            }
            Instance o = (Instance) obj;
            return this.cid == o.cid;
        }

        @Override
        public int hashCode() {
            return cid;
        }

        public long getDuration() {
            return end - start;
        }

        public static String getHeader() {
            return "Start\tDuration\t#Req\tSize\tRetransmits\tAlpha";
        }

        public String toString() {
            return start / 1000 + "\t" + getDuration() / 1000 + "\t" + nRequests + "\t" +
                   valueSize + "\t" + retransmit + "\t" + alpha;
        }
    }

    private final int n;
    private final int localID;
    private final PerformanceLogger pLogger;
    private final HashMap<Integer, Instance> instances = new HashMap<Integer, Instance>();

    // Current view of each process
    private int view = -1;
    boolean firstLog = true;

    ReplicaStatsFull(int n, int localID) throws IOException {
        this.n = n;
        this.localID = localID;
        pLogger = PerformanceLogger.getLogger("replica-" + localID);
        pLogger.log("% Consensus\t" + Instance.getHeader() + "\n");
    }

    public void consensusStart(int cid, int size, int k, int alpha) {
        // Ignore logs from non-leader
        assert isLeader() : "Not leader. cid: " + cid;

        assert !instances.containsKey(cid) : "Instance not null: " + instances.get(cid);
        Instance cInstance = new Instance(cid, System.nanoTime(), size, k, alpha);
        instances.put(cid, cInstance);
    }

    public void retransmit(int cid) {
        assert isLeader() : "Not leader. cid: " + cid;

        Instance instance = instances.get(cid);
        instance.retransmit++;
    }

    public void consensusEnd(int cid) {
        // Ignore log if process is not leader.
        if (!isLeader()) {
            return;
        }
        Instance cInstance = instances.remove(cid);
        if (cInstance == null) {
            // Can occur in view change if this process is the leader that
            // decides
            // an instance that was started by a previous leader.
            // Ignore this instance, as it is not possible to accurately measure
            // the instance
            // time using clocks from two processes.
            return;
        }

        cInstance.end = System.nanoTime();
        // Write to log
        writeInstance(cid, cInstance);
    }

    public void advanceView(int newView) {
        this.view = newView;
        for (Integer cid : instances.keySet()) {
            Instance cInstance = instances.get(cid);
            cInstance.end = -1;
            writeInstance(cid, cInstance);
        }
        instances.clear();
    }

    private void writeInstance(int cId, Instance cInstance) {
        pLogger.log(cId + "\t" + cInstance + "\n");
    }

    /**
     * True if the process considers itself the leader
     * 
     * @return
     */
    private boolean isLeader() {
        return view % n == localID;
    }
}
