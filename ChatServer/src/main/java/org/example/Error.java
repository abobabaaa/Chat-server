package org.example;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public enum Error {
    BAD_REQUEST(401, "Bad request"),
    USER_NOT_FOUND(402, "User not found"),
    DATABASE_ERROR(501,"Database error");

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
