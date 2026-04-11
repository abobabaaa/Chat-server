package org.example;

import com.google.gson.annotations.SerializedName;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.concurrent.Callable;

public final class Auth2FARequest implements Callable<Auth2FARequest.Status> {
    private final int id;
    private final InetAddress ip;
    @SerializedName("target_user_id")
    private final int targetUserID;
    @SerializedName("created_at")
    private final Timestamp createdAt;
    @SerializedName("status")
    private volatile Status auth2FAStatus;
    private Error error;

    {
        error = null;
    }

    public Auth2FARequest(
            int id,
            InetAddress ip,
            int targetUserID,
            Timestamp createdAt) {
        this.id = id;
        this.ip = ip;
        this.targetUserID = targetUserID;
        this.createdAt = createdAt;
        auth2FAStatus = Status.WAITING_FOR_CONFIRMATION;
    }

    public int getId() {
        return id;
    }

    public InetAddress getIp() {
        return ip;
    }

    public int getTargetUserID() {
        return targetUserID;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public Error getError() {
        return error;
    }

    public void setErrorStatus(Error error){
        auth2FAStatus = Status.ERROR;
        this.error = error;
    }

    /**
     * This method is used to notify client, that is waiting for response to 2FA request
     * @return {@link Status} instance:
     * <p>{@code Status.ACCESS_GRANTED} if 2FA request was approved from authorized device</p>
     * <p>{@code Status.ACCESS_DENIED} otherwise</p>
     * @throws InterruptedException if initiator's thread is being interrupted
     */
    @Override
    public synchronized Status call() throws InterruptedException {
        System.out.println("initiator thread is waiting...");
        while (auth2FAStatus == Status.WAITING_FOR_CONFIRMATION){
            wait();
        }
        System.out.println("initiator thread awakened!");
        return auth2FAStatus;
    }

    /**
     * This method is used to take result of 2FA from thread of already authorized user
     * @throws InterruptedException if authorized user's thread is being interrupted
     */
    public synchronized void handle2FAResponse(boolean approve) throws InterruptedException{
        auth2FAStatus = approve? Status.ACCESS_GRANTED : Status.ACCESS_DENIED;
        notify();
    }

    public enum Status{
        WAITING_FOR_CONFIRMATION, ACCESS_GRANTED, ACCESS_DENIED, ERROR
    }
}
