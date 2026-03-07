package org.example;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class Chat implements Serializable {
    @SerializedName("chat_id")
    private int chatID;
    @SerializedName("user_1")
    private int user1ID;
    @SerializedName("user_2")
    private int user2ID;

    public Chat(int chatID, int user1ID, int user2ID) {
        this.chatID = chatID;
        this.user1ID = user1ID;
        this.user2ID = user2ID;
    }

    public int getChatID() {
        return chatID;
    }

    public int getUser1ID() {
        return user1ID;
    }

    public int getUser2ID() {
        return user2ID;
    }

    //Array from 2 elements
    public int[] getUsers(){
        return new int[]{user1ID, user2ID};
    }
}
