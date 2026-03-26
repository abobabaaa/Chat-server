package org.example.toUser;

import com.google.gson.annotations.SerializedName;
import org.example.User;

import java.net.InetAddress;

public class ServerRequest {
    @SerializedName("to_ip")
    private InetAddress toIp;
    @SerializedName("to_user")
    private User toUser;
    @SerializedName("request_type")
    private Type requestType;
    @SerializedName(value = "request_body")
    private Object requestBody;

    public ServerRequest(InetAddress toIp, User toUser, Type requestType, Object requestBody) {
        this.toIp = toIp;
        this.toUser = toUser;
        this.requestType = requestType;
        this.requestBody = requestBody;
    }

    public InetAddress getToIp() {
        return toIp;
    }

    public User getToUser() {
        return toUser;
    }

    public Type getRequestType() {
        return requestType;
    }

    public Object getRequestBody() {
        return requestBody;
    }

    public enum Type{
        ERROR, SUCCESSFUL_REGISTRATION, SUCCESSFUL_AUTHORIZATION, AUTHORIZATION_FAILED, REGISTRATION_FAILED,
        CHAT_CREATED, RECEIVE_MESSAGE, MESSAGE_DELIVERED, MESSAGE_VIEWED
    }
}
