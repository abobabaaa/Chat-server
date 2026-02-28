package org.example;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class Chat implements Serializable {
    @SerializedName("chat_id")
    private int chatID;
    @SerializedName("user_1")
    private User user1;
    @SerializedName("user_2")
    private User user2;

    public Chat(int chatID, User user1, User user2) {
        this.chatID = chatID;
        this.user1 = user1;
        this.user2 = user2;
    }

    public int getChatID() {
        return chatID;
    }

    public User getUser1() {
        return user1;
    }

    public User getUser2() {
        return user2;
    }
}
