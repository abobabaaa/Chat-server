package org.example.messages;

import com.google.gson.annotations.SerializedName;
import org.example.Chat;
import org.example.User;

import java.time.LocalDateTime;

public sealed class Message permits TextMessage {
    @SerializedName("message_id")
    private int messageID;

    private transient Chat chat;

    @SerializedName("from_user")
    private User fromUser;

    @SerializedName("chat_id")
    private int chatID;

    private LocalDateTime date;

    @SerializedName("delivered")
    private boolean isDelivered;

    {
        messageID = 0;
        isDelivered = false;
    }

    //For constructing message object that will be sent to clients from server
    public Message(int messageID, Chat chat, User fromUser, LocalDateTime date) {
        this.messageID = messageID;
        this.chat = chat;
        this.fromUser = fromUser;
        this.date = date;
        this.chatID = chat.getChatID();
    }

    //For constructing message object received from client
    public Message(int chatID, User fromUser) {
        this.chatID = chatID;
        this.fromUser = fromUser;
    }

    public Message(Chat chat, User fromUser) {
        this.chat = chat;
        this.chatID = chat.getChatID();
        this.fromUser = fromUser;
    }

    public int getMessageID() {
        return messageID;
    }

    public void setMessageID(int messageID) {
        this.messageID = messageID;
    }

    public Chat getChat() {
        return chat;
    }

    public User getFromUser() {
        return fromUser;
    }

    public int getChatID() {
        return chatID;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public boolean isDelivered() {
        return isDelivered;
    }

    public void setDelivered(boolean delivered) {
        isDelivered = delivered;
    }
}
