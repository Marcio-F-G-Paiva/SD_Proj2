package sd2526.trab.impl.replication;

import java.io.Serializable;

/**
 * Operation for state machine replication
 */
public class Operation implements Serializable {

    public enum Type {
        POST_USER,
        UPDATE_USER,
        DELETE_USER,
        POST_MESSAGE,
        DELETE_MESSAGE
    }

    private Type type;
    private Object data;
    private long timestamp;

    public Operation(Type type, Object data) {
        this.type = type;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public Type getType() {
        return type;
    }

    public Object getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
