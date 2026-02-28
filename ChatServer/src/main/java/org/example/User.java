package org.example;

import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.Socket;

public class User implements Serializable {
    @SerializedName("user_id")
    private int userID;
    private String username;
    //Output stream for server to send data to client
    private transient PrintWriter outputWriter;
    //Input stream for server to listen to client
    private transient BufferedReader inputReader;
    private transient Socket client;

    public User(int userID, String username, PrintWriter output, BufferedReader in, Socket client) {
        this.userID = userID;
        this.username = username;
        this.outputWriter = output;
        this.inputReader = in;
        this.client = client;
    }

    public User(int userID, String username) {
        this.userID = userID;
        this.username = username;
    }

    public Socket getClient() {
        return client;
    }

    public int getUserID() {
        return userID;
    }

    public String getUsername() {
        return username;
    }

    public PrintWriter getOutputWriter() {
        return outputWriter;
    }

    public BufferedReader getInputReader() {
        return inputReader;
    }

    public void setOutputWriter(PrintWriter outputWriter) {
        this.outputWriter = outputWriter;
    }

    public void setInputReader(BufferedReader inputReader) {
        this.inputReader = inputReader;
    }

    public void setClient(Socket client) {
        this.client = client;
    }
}
