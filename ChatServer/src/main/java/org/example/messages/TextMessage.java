package org.example.messages;

import com.google.gson.*;
import org.example.Chat;
import org.example.Server;
import org.example.User;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class TextMessage extends Message{

    private String text;

    public TextMessage(int chatID, User fromUser, String text) {
        super(chatID, fromUser);
        this.text = text;
    }

    public TextMessage(int messageID, Chat chat, User fromUser, LocalDateTime date, String text) {
        super(messageID, chat, fromUser, date);
        this.text = text;
    }

    public TextMessage(Chat chat, User fromUser, String text) {
        super(chat, fromUser);
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static class Serializer implements JsonSerializer<TextMessage>{

        @Override
        public JsonElement serialize(TextMessage src, Type typeOfSrc, JsonSerializationContext context) {
            Gson gson = new Gson();
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("message_id", src.getMessageID());
            JsonObject fromUser = gson.fromJson(gson.toJson(src.getFromUser()),JsonObject.class);
            jsonObject.add("from_user",fromUser);
            jsonObject.addProperty("chat_id",src.getChatID());

            LocalDateTime datetime = src.getDate();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Server.SERVER_DATETIME_FORMAT);
            String formattedDatetime = formatter.format(datetime);
            jsonObject.addProperty("date",formattedDatetime);

            jsonObject.addProperty("text",src.getText());
            jsonObject.addProperty("delivered",src.isDelivered());
            return jsonObject;
        }
    }
}
