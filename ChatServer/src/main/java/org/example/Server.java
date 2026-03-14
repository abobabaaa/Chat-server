package org.example;

import com.google.gson.*;
import org.example.fromUser.ClientRequest;
import org.example.messages.TextMessage;
import org.example.toUser.ServerRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Server {
    private static final int PORT = 2865;

    public static final String SERVER_DATETIME_FORMAT = "dd-MM-yyyy|hh:mm";

    private static List<User> onlineUsers = Collections.synchronizedList(new ArrayList<>());
    private static List<User> allUsers = Collections.synchronizedList(new ArrayList<>());
    private static List<Chat> allChats = Collections.synchronizedList(new ArrayList<>());

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Error.class, new Error.ErrorSerializer())
            .registerTypeAdapter(TextMessage.class, new TextMessage.Serializer())
            .setPrettyPrinting()
            .create();

    private static final String CONNECTION_REQUEST = "Connection request received, ip: %s\n";

    private static final String CLIENT_RECOGNIZED =
            ConsoleColor.GREEN +
            "Client with ip %s successfully connected and recognized as %s(userID: %d)!" +
            ConsoleColor.RESET_COLOR + "\n";

    private static final String INVALID_REQUEST =
            ConsoleColor.YELLOW +
            "Invalid request from user %s(userID: %d, ip: %s):\n%s" +
            ConsoleColor.RESET_COLOR + "\n";

    private static final String CONNECTION_REQUEST_DECLINED =
            ConsoleColor.RED +
            "Connection request from ip %s declined by server: %s" +
            ConsoleColor.RESET_COLOR + "\n";

    private static final String USER_DISCONNECTED_BY_SERVER =
            ConsoleColor.RED +
            "User %s(userID: %d) disconnected by server: %s" +
            ConsoleColor.RESET_COLOR + "\n";

    private static final String CHAT_CREATED =
            ConsoleColor.GREEN +
            "Successfully created private chat(chatID: %d)" +
            ConsoleColor.RESET_COLOR + "\n";

    private static final String ERROR_OCCURRED =
            ConsoleColor.RED +
            "An error occurred while trying to %s" +
            ConsoleColor.RESET_COLOR + "\n";

    private static final String RED_MESSAGE = ConsoleColor.RED + "%s" + ConsoleColor.RESET_COLOR + "\n";
    private static final String GREEN_MESSAGE = ConsoleColor.GREEN + "%s" + ConsoleColor.RESET_COLOR + "\n";


    static void main() {
        System.out.println("Starting server...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)){
            //TODO: Скачивать данные с базы данных и загружать их в память сервера
            System.out.println("Initializing database...");
            boolean isInitialized = DatabaseHandler.initDatabase(); //&& downloadDataFromDatabase();
            if (!isInitialized){
                System.out.printf(RED_MESSAGE, "Database initialization failed!");
                System.out.printf(RED_MESSAGE, "Disabling server due to database initialization failure...");
                return;
            }
            System.out.printf(GREEN_MESSAGE, "Successfully initialized the database!");

            System.out.println("Downloading data from the database to server memory...");
            boolean isDataDownloaded = DatabaseHandler.downloadDataToServerMemory(allUsers,allChats);
            if (!isDataDownloaded){
                System.out.printf(RED_MESSAGE, "Data download failed!");
                System.out.printf(RED_MESSAGE, "Disabling server due to data download failure...");
                return;
            }
            System.out.printf(GREEN_MESSAGE, "Successfully downloaded data from the database!");

            System.out.println("Server started at port " + PORT);

            while (true){
                Socket client = serverSocket.accept();
                System.out.printf(CONNECTION_REQUEST,client.getInetAddress().toString());

                new Thread(()->{
                    InetAddress ip = client.getInetAddress();
                    boolean isAlreadyConnected = checkConnection(ip);
                    if (!isAlreadyConnected){
                        User user = recognizeUser(client);

                        if (user != null){
                            handleClient(user);
                        }
                    }
                    else {
                        try (PrintWriter out = new PrintWriter(client.getOutputStream(),true)){
                            ServerRequest serverRequest = new ServerRequest(
                                    client.getInetAddress(),
                                    null,
                                    ServerRequest.Type.ERROR,
                                    Error.DUPLICATED_CONNECTION
                            );
                            out.println(gson.toJson(serverRequest) + "\0");
                            client.close();
                            client.getInputStream().close();
                            client.getOutputStream().close();
                            System.out.printf(
                                    CONNECTION_REQUEST_DECLINED,
                                    ip,
                                    "client with this ip is already connected to the server"
                            );
                        }
                        catch (IOException e){
                            System.out.printf(
                                    ERROR_OCCURRED,
                                    "disconnect client with ip " + client.getInetAddress().toString()
                            );
                        }
                    }
                }).start();
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private static User recognizeUser(Socket client) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(client.getOutputStream(),true);

            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(Error.class, new Error.ErrorSerializer())
                    .setPrettyPrinting().create();

            StringBuilder json = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null){
                json.append(line);

                if (json.toString().endsWith("\0")){
                    try {
                        String noNullByte = removeNullByte(json.toString());

                        json = new StringBuilder(noNullByte);

                        String jsonRequest = json.toString();

                        ClientRequest clientRequest = gson.fromJson(jsonRequest, ClientRequest.class);

                        if (clientRequest.getRequestType() == ClientRequest.Type.CONNECT){
                            User fromUser = clientRequest.getFrom();

                            int id = fromUser.getUserID();
                            String username = fromUser.getUsername();

                            boolean isUserExists = DatabaseHandler.checkUser(id,username);

                            if (isUserExists) {

                                fromUser.setInputReader(in);
                                fromUser.setOutputWriter(out);
                                fromUser.setClient(client);

                                ServerRequest serverRequest = new ServerRequest(
                                        client.getInetAddress(),
                                        fromUser,ServerRequest.Type.SUCCESSFUL_CONNECTION,
                                        null
                                );
                                out.println(gson.toJson(serverRequest) + "\0");

                                System.out.printf(CLIENT_RECOGNIZED,client.getInetAddress(), username, id);

                                return fromUser;
                            }
                            else{
                                JsonObject errorJson = new JsonObject();
                                errorJson.addProperty("error_code",Error.USER_NOT_FOUND.getCode());
                                ServerRequest serverRequest = new ServerRequest(
                                        client.getInetAddress(),
                                        null,
                                        ServerRequest.Type.ERROR,
                                        Error.USER_NOT_FOUND
                                );

                                out.println(gson.toJson(serverRequest) + "\0");

                                client.close();
                                System.out.printf(CONNECTION_REQUEST_DECLINED,
                                        client.getInetAddress(),
                                        String.format("User %s(userID: %d) not found",username,id)
                                );
                                return null;
                            }
                        }
                        else {
                            ServerRequest serverRequest = new ServerRequest(
                                    client.getInetAddress(),
                                    null,
                                    ServerRequest.Type.ERROR,
                                    Error.BAD_REQUEST
                            );

                            out.println(gson.toJson(serverRequest) + "\0");

                            client.close();

                            JsonElement el = JsonParser.parseString(json.toString());
                            json = new StringBuilder(gson.toJson(el));

                            System.out.printf(CONNECTION_REQUEST_DECLINED,
                                    client.getInetAddress(),
                                    "invalid connection request(invalid request type for connection):\n" + json
                            );
                            return null;
                        }

                    }
                    catch (JsonSyntaxException e){
                        ServerRequest serverRequest = new ServerRequest(
                                client.getInetAddress(),
                                null,
                                ServerRequest.Type.ERROR,
                                Error.BAD_REQUEST);

                        out.println(gson.toJson(serverRequest) + "\0");

                        client.close();
                        System.out.printf(CONNECTION_REQUEST_DECLINED,
                                client.getInetAddress(),
                                "invalid connection request(invalid JSON syntax):\n" + json + "\n" + e.getMessage());
                        return null;
                    }
                }
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private static void handleClient(User user) {
        onlineUsers.add(user);

        try (
                BufferedReader input = user.getInputReader();
                PrintWriter output = user.getOutputWriter();
                Socket client = user.getClient()
                ){

            InetAddress ip = client.getInetAddress();
            int userId = user.getUserID();
            String username = user.getUsername();

            StringBuilder json = new StringBuilder();
            String line;
            while ((line = input.readLine()) != null){
                json.append(line);

                if (json.toString().endsWith("\0")){
                    String request = removeNullByte(json.toString());

                    try {
                        ClientRequest clientRequest = gson.fromJson(request,ClientRequest.class);
                        User fromUser = clientRequest.getFrom();

                        boolean isSenderExists = DatabaseHandler.checkUser(fromUser.getUserID(), fromUser.getUsername());

                        if (!isSenderExists){
                            ServerRequest serverRequest = new ServerRequest(
                                    client.getInetAddress(),
                                    fromUser,
                                    ServerRequest.Type.ERROR,
                                    Error.USER_NOT_FOUND
                            );
                            output.println(gson.toJson(serverRequest) + "\0");

                            JsonElement el = gson.fromJson(request,JsonElement.class);
                            System.out.printf(
                                    INVALID_REQUEST,
                                    username,
                                    userId,
                                    ip,
                                    gson.toJson(el) + "\n(Target user not found)"
                            );
                        }

                        JsonObject body = clientRequest.getRequestBody();
                        ClientRequest.Type type = clientRequest.getRequestType();

                        if (user.getUserID() == userId && user.getUsername().equals(username)){
                            switch (type){

                                case CREATE_CHAT -> {
                                    User withUser = gson.fromJson(gson.toJson(body), User.class);
                                    boolean isUserExists = DatabaseHandler.checkUser(withUser.getUserID(),withUser.getUsername());

                                    if (isUserExists){
                                        Chat chat = DatabaseHandler.createChat(fromUser,withUser);
                                        if (chat != null){
                                            allChats.add(chat);
                                            ServerRequest serverRequest = new ServerRequest(
                                                    client.getInetAddress(),
                                                    fromUser,
                                                    ServerRequest.Type.CHAT_CREATED,
                                                    chat
                                            );
                                            output.println(gson.toJson(serverRequest) + "\0");
                                            System.out.printf(CHAT_CREATED, chat.getChatID());
                                        }
                                        else {
                                            ServerRequest serverRequest = new ServerRequest(
                                                    ip,
                                                    fromUser,
                                                    ServerRequest.Type.ERROR,
                                                    Error.DATABASE_ERROR
                                            );
                                            output.println(gson.toJson(serverRequest) + "\0");
                                            //Database will notify about error
                                        }
                                    }
                                    else {
                                        ServerRequest serverRequest = new ServerRequest(
                                                client.getInetAddress(),
                                                fromUser,
                                                ServerRequest.Type.ERROR,
                                                Error.USER_NOT_FOUND
                                        );
                                        output.println(gson.toJson(serverRequest) + "\0");

                                        JsonElement el = gson.fromJson(request,JsonElement.class);
                                        System.out.printf(
                                                INVALID_REQUEST,
                                                username,
                                                userId,
                                                ip,
                                                gson.toJson(el) + "\n(Target user not found)"
                                        );
                                    }

                                }

                                case CREATE_GROUP_CHAT -> {
                                    //group chat creating
                                }

                                case MESSAGE_RECEIVED -> {
                                    int messageID = body.get("message_id").getAsInt();
                                    int senderUserID = DatabaseHandler.markMessageAsDelivered(messageID);

                                    if (senderUserID != 0){
                                        User sender = getOnlineUserByID(senderUserID);

                                        if (sender != null){
                                            JsonObject jsonObject = new JsonObject();
                                            jsonObject.addProperty("message_id",messageID);
                                            ServerRequest serverRequest = new ServerRequest(
                                                    sender.getClient().getInetAddress(),
                                                    sender,
                                                    ServerRequest.Type.MESSAGE_DELIVERED,
                                                    jsonObject
                                            );
                                            sender.getOutputWriter().println(gson.toJson(serverRequest) + "\0");
                                        }
                                    }
                                    else {
                                        ServerRequest serverRequest = new ServerRequest(
                                                ip,
                                                fromUser,
                                                ServerRequest.Type.ERROR,
                                                Error.USER_NOT_FOUND
                                        );
                                        output.println(gson.toJson(serverRequest) + "\0");
                                        System.out.printf(
                                                INVALID_REQUEST,
                                                username,
                                                userId,
                                                ip.toString(),
                                                "sender of message with messageID " + messageID + " not found"
                                        );
                                    }
                                }

                                case MESSAGE_VIEWED -> {
                                    int messageID = body.get("message_id").getAsInt();
                                    int senderUserID = DatabaseHandler.markMessageAsViewed(messageID, fromUser.getUserID());

                                    if (senderUserID == 0){
                                        ServerRequest serverRequest = new ServerRequest(
                                                ip,
                                                fromUser,
                                                ServerRequest.Type.ERROR,
                                                Error.USER_NOT_FOUND
                                        );
                                        output.println(gson.toJson(serverRequest) + "\0");

                                    }
                                    else if (senderUserID == -1) {
                                        ServerRequest serverRequest = new ServerRequest(
                                                ip,
                                                fromUser,
                                                ServerRequest.Type.ERROR,
                                                Error.SELF_MESSAGE_VIEW
                                        );
                                        output.println(gson.toJson(serverRequest) + "\0");
                                        System.out.printf(
                                                INVALID_REQUEST,
                                                username,
                                                userId,
                                                ip,
                                                "client attempted to mark message sent by himself as viewed"
                                        );
                                    }
                                    else {
                                        User sender = getOnlineUserByID(senderUserID);

                                        if (sender != null){
                                            JsonObject jsonObject = new JsonObject();
                                            jsonObject.addProperty("message_id",messageID);
                                            ServerRequest serverRequest = new ServerRequest(
                                                    sender.getClient().getInetAddress(),
                                                    sender,
                                                    ServerRequest.Type.MESSAGE_VIEWED,
                                                    jsonObject
                                            );
                                            sender.getOutputWriter().println(gson.toJson(serverRequest) + "\0");
                                        }
                                    }
                                }

                                case SEND_TEXT_MESSAGE -> {

                                    int toChatID = body.get("chat_id").getAsInt();
                                    String msgText = body.get("text").getAsString().trim();

                                    if (msgText.isEmpty()){
                                        ServerRequest serverRequest = new ServerRequest(
                                                ip,
                                                fromUser,
                                                ServerRequest.Type.ERROR,
                                                Error.EMPTY_TEXT_MESSAGE
                                        );
                                        output.println(gson.toJson(serverRequest) + "\0");
                                        System.out.printf(
                                                INVALID_REQUEST,
                                                username,
                                                userId,
                                                ip,
                                                "client attempted to send an empty text message"
                                        );
                                        break;
                                    }

                                    Chat toChat = null;
                                    for (Chat chat : allChats){
                                        if (chat.getChatID() == toChatID){
                                            toChat = chat;
                                            break;
                                        }
                                    }
                                    if (toChat != null){
                                        TextMessage textMessage = new TextMessage(toChatID, fromUser, msgText);
                                        boolean isSuccess = DatabaseHandler.addMessage(textMessage);

                                        if (isSuccess){
                                            int receiverId = 0;
                                            for (int usrId : toChat.getUsers()){
                                                if (usrId != fromUser.getUserID()){
                                                    receiverId = usrId;
                                                    break;
                                                }
                                            }

                                            if (receiverId != 0){
                                                for (User usr : onlineUsers){
                                                    if (usr.getUserID() == receiverId){
                                                        ServerRequest serverRequest = new ServerRequest(
                                                                usr.getClient().getInetAddress(),
                                                                usr,
                                                                ServerRequest.Type.RECEIVE_MESSAGE,
                                                                textMessage
                                                        );
                                                        String jsonReq = gson.toJson(serverRequest);
                                                        usr.getOutputWriter().println(jsonReq + "\0");
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                        else {
                                            ServerRequest serverRequest = new ServerRequest(
                                                    client.getInetAddress(),
                                                    user,
                                                    ServerRequest.Type.ERROR,
                                                    Error.DATABASE_ERROR
                                            );
                                            output.println(gson.toJson(serverRequest) + "\0");

                                            String formattedUser = String.format("%s(userID: %d)",username,userId);
                                            System.out.printf(
                                                    ERROR_OCCURRED,
                                                    "handle request from user " + formattedUser + "(Database error)"
                                            );
                                        }
                                    }
                                    else {
                                        ServerRequest serverRequest = new ServerRequest(
                                                ip,
                                                user,
                                                ServerRequest.Type.ERROR,
                                                Error.CHAT_NOT_FOUND
                                        );
                                        output.println(gson.toJson(serverRequest) + "\0");

                                        JsonElement el = gson.fromJson(request, JsonElement.class);
                                        System.out.printf(
                                                INVALID_REQUEST,
                                                username,
                                                userId,
                                                ip.toString(),
                                                gson.toJson(el) + "\n(Chat not found)"
                                        );
                                    }

                                }
                            }
                        }

                    }
                    catch (JsonSyntaxException e){
                        ServerRequest serverRequest = new ServerRequest(
                                client.getInetAddress(),
                                user,
                                ServerRequest.Type.ERROR,
                                Error.BAD_REQUEST
                        );
                        output.println(gson.toJson(serverRequest) + "\0");

                        System.out.printf(
                                INVALID_REQUEST,
                                username,
                                userId,
                                ip,
                                json + "\n(Invalid json syntax):\n" + e.getMessage()
                        );
                    }

                    json.delete(0, json.length());
                }
            }

            onlineUsers.remove(user);
            System.out.println("User " + user.getUsername() + "(ip: " + ip + ") disconnected");
        }
        catch (IOException e) {
            boolean isClosed = user.getClient().isClosed();
            if (isClosed){
                System.out.printf("User %s(userID: %d) disconnected: %s", user.getUsername(),user.getUserID(),"client initiated disconnect\n");
                onlineUsers.remove(user);
            }
            else {
                String clientInstanceDesc = String.format("%s(userID: %d, ip: %s)", user.getUsername(), user.getUserID(), user.getClient().getInetAddress());
                System.out.printf(ERROR_OCCURRED, "trying to handle client of user" + clientInstanceDesc);
                e.printStackTrace();
            }
        }

    }

    private static String removeNullByte(String str){
        byte[] stringBytes = str.getBytes(StandardCharsets.UTF_8);
        byte[] noNullByte = new byte[stringBytes.length - 1];

        System.arraycopy(stringBytes,0,noNullByte,0,noNullByte.length);
        return new String(noNullByte, StandardCharsets.UTF_8);
    }

    private static User getOnlineUserByID(int userID){
        for (User user : onlineUsers){
            if (user.getUserID() == userID)
                return user;
        }
        return null;
    }

    /**
     * Checks is client with the specified ip already connected to the server
     * @param ip ip address to check
     * @return {@code true} if client with this ip is already connected or
     *  <p>{@code false} otherwise</p>
     */
    private static boolean checkConnection(InetAddress ip){
        String ipStr = ip.toString();
        for (User user : onlineUsers){
            String bufIp = user.getClient().getInetAddress().toString();
            if (ipStr.equals(bufIp))
                return true;
        }
        return false;
    }
}
