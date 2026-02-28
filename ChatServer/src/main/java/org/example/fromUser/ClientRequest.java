package org.example.fromUser;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.example.User;

public class ClientRequest {
    @SerializedName("from_user")
    private User fromUser;
    @SerializedName("request_type")
    private RequestType requestType;
    @SerializedName("request_body")
    private JsonObject requestBody;

    public ClientRequest(User fromUser, RequestType requestType, JsonObject requestBody) {
        this.fromUser = fromUser;
        this.requestType = requestType;
        this.requestBody = requestBody;
    }

    public User getFrom() {
        return fromUser;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public JsonObject getRequestBody() {
        return requestBody;
    }

    public enum RequestType{
        CREATE_CHAT, CREATE_GROUP_CHAT, SEND_TEXT_MESSAGE, CONNECT
    }
}
