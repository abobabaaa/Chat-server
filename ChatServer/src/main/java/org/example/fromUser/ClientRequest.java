package org.example.fromUser;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.example.User;

public class ClientRequest {
    @SerializedName("from_user")
    private User fromUser;
    @SerializedName("request_type")
    private Type type;
    @SerializedName("request_body")
    private JsonObject requestBody;

    public ClientRequest(User fromUser, Type type, JsonObject requestBody) {
        this.fromUser = fromUser;
        this.type = type;
        this.requestBody = requestBody;
    }

    public User getFrom() {
        return fromUser;
    }

    public Type getRequestType() {
        return type;
    }

    public JsonObject getRequestBody() {
        return requestBody;
    }

    public enum Type {
        CREATE_CHAT, CREATE_GROUP_CHAT, SEND_TEXT_MESSAGE,
        AUTHORIZE_AND_CONNECT, REGISTER_ACCOUNT, MESSAGE_RECEIVED, MESSAGE_VIEWED
    }
}
