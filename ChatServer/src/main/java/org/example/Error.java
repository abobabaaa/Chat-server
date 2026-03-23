package org.example;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public enum Error {
    SERVER_ERROR(301,"Server error"),
    BAD_REQUEST(401, "Bad request"),
    USER_NOT_FOUND(402, "User not found"),

    AUTH_USER_NOT_FOUND(201,"Target user for authorization not found"),
    AUTH_INCORRECT_PASSWORD(202,"Incorrect password"),

    CHAT_NOT_FOUND(403,"Chat not found"),
    DUPLICATED_CONNECTION(404,"Client with this ip is already connected"),
    DATABASE_ERROR(501,"Database error"),

    SELF_MESSAGE_VIEW(601,"Message cannot marked as viewed by its sender"),
    EMPTY_TEXT_MESSAGE(602,"Text message is empty");

    private int code;
    private String message;

    Error(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static class ErrorSerializer implements JsonSerializer<Error>{

        @Override
        public JsonElement serialize(Error src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("code", src.getCode());
            jsonObject.addProperty("message", src.getMessage());
            return jsonObject;
        }
    }
}
