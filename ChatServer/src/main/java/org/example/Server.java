package org.example;

import com.google.gson.*;
import org.example.fromUser.ClientRequest;
import org.example.messages.TextMessage;
import org.example.toUser.ServerRequest;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public class Server {
    private static final int PORT = 2865;
    private static final String KEY_STORE_PASSWORD = "KEY_STORE_PASSWORD";
    private static final String KEY_STORE_PATH = "KEY_STORE_PATH";
    private static final String KEY_STORE_TYPE = "KEY_STORE_TYPE";
    private static final String KEY_MANAGER_ALGORITHM = "KEY_MANAGER_ALGORITHM";
    private static final String ENCRYPTION_ALGORITHM = "ENCRYPTION_ALGORITHM";

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

    private static final String CLIENT_AUTHORIZED =
            ConsoleColor.GREEN +
            "Client with ip %s successfully connected and authorized as %s(userID: %d)!" +
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
        System.out.println("Initializing secure server socket...");
        try (SSLServerSocket serverSocket = initSecureServerSocket()){
            if (serverSocket == null){
                System.out.printf(RED_MESSAGE, "Secure socket initialization failed!");
                return;
            }
            System.out.printf(GREEN_MESSAGE, "Secure server socket successfully initialized!");

            System.out.println("Initializing database...");
            boolean isInitialized = DatabaseHandler.initDatabase();
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

            System.out.printf(GREEN_MESSAGE, "Server started at port " + PORT);

            while (true){
                SSLSocket client = (SSLSocket) serverSocket.accept();
                System.out.printf(CONNECTION_REQUEST,client.getInetAddress().toString());

                new Thread(()->{
                    InetAddress ip = client.getInetAddress();
                    try {
                        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                        PrintWriter out = new PrintWriter(client.getOutputStream(), true);

                        StringBuilder jsonStr = new StringBuilder();
                        String line;
                        String deviceUID = null;
                        User registeredUser = null;
                        while ((line = in.readLine()) != null){
                            jsonStr.append(line);

                            if (line.endsWith("\0")){
                                String jsonRequest = removeNullByte(jsonStr.toString());
                                ClientRequest clientRequest = parseCLientRequest(jsonRequest);

                                if (clientRequest == null){
                                    ServerRequest serverRequest = new ServerRequest(
                                            ip,
                                            null,
                                            ServerRequest.Type.ERROR,
                                            Error.BAD_REQUEST
                                    );
                                    out.println(gson.toJson(serverRequest) + "\0");
                                    in.close();
                                    out.close();
                                    client.close();

                                    System.out.printf(CONNECTION_REQUEST_DECLINED, ip, "Invalid json request");
                                    break;
                                }

                                JsonObject body = clientRequest.getRequestBody();
                                String username = body.get("username").getAsString();
                                String password = body.get("password").getAsString();
                                switch (clientRequest.getRequestType()){
                                    case AUTHORIZE_AND_CONNECT -> {
                                        //TODO: Remake authorization(handle device UID)
                                        AuthorizationResult authResult = authorizeUser(client,username,password);

                                        if (authResult.isSuccess()){
                                            User user = authResult.user();

                                            ServerRequest serverRequest = new ServerRequest(
                                                    ip,
                                                    user,
                                                    ServerRequest.Type.SUCCESSFUL_AUTHORIZATION,
                                                    null
                                            );
                                            out.println(gson.toJson(serverRequest) + "\0");

                                            int userID = user.getUserID();
                                            System.out.printf(
                                                    CLIENT_AUTHORIZED,
                                                    ip,
                                                    username,
                                                    userID
                                            );
                                            handleClient(authResult.user());
                                        }
                                        else {
                                            ServerRequest serverRequest = new ServerRequest(
                                                    ip,
                                                    null,
                                                    ServerRequest.Type.ERROR,
                                                    authResult.error()
                                            );
                                            out.println(gson.toJson(serverRequest) + "\0");

                                            in.close();
                                            out.close();
                                            client.close();
                                            System.out.printf(
                                                    CONNECTION_REQUEST_DECLINED,
                                                    ip,"authorization failed. " + authResult.error().getMessage()
                                            );
                                        }
                                        client.close();
                                    }
                                    case REGISTER_ACCOUNT -> {
                                        RegistrationResult result = registerUser(client, username, password);
                                        if (result.isSuccess()){
                                            User user = result.user();
                                            int userID = user.getUserID();

                                            SecureRandom secureRandom = new SecureRandom();
                                            byte[] bytes = new byte[32];
                                            secureRandom.nextBytes(bytes);
                                            deviceUID = Base64.getEncoder().encodeToString(bytes);

                                            JsonObject jsonObject = new JsonObject();
                                            jsonObject.addProperty("user_id",user.getUserID());
                                            jsonObject.addProperty("username", user.getUsername());
                                            jsonObject.addProperty("device_uid",deviceUID);
                                            //there
                                            ServerRequest serverRequest = new ServerRequest(
                                                    ip,
                                                    null,
                                                    ServerRequest.Type.SUCCESSFUL_REGISTRATION,
                                                    jsonObject
                                            );
                                            out.println(gson.toJson(serverRequest) + "\0");

                                            String formattedUser = String.format("%s(userID: %d)", user.getUsername(), userID);
                                            System.out.printf(
                                                    GREEN_MESSAGE,
                                                    "Successfully registered new user " + formattedUser +
                                                    "! Waiting for device uid confirmation.."
                                            );
                                            registeredUser = user;
                                        }
                                        else {
                                            Error resultError = result.error();
                                            ServerRequest serverRequest = new ServerRequest(
                                                    ip,
                                                    null,
                                                    ServerRequest.Type.REGISTRATION_FAILED,
                                                    resultError
                                            );
                                            out.println(gson.toJson(serverRequest) + "\0");
                                            System.out.printf(RED_MESSAGE, "Failed to register user " + username + ": " + resultError);
                                            client.close();
                                        }
                                    }
                                    case DEVICE_UID_RECEIVED -> {
                                        if (deviceUID != null){
                                            boolean isSuccess = false;
                                            for (int i = 0; i <= 3; i++) {
                                                isSuccess = DatabaseHandler.saveDeviceUID(registeredUser.getUserID(),deviceUID);
                                                if (isSuccess)
                                                    break;
                                            }
                                            if (isSuccess){
                                                System.out.printf(
                                                        CLIENT_AUTHORIZED,
                                                        ip, username, registeredUser.getUserID()
                                                );
                                                handleClient(registeredUser);
                                            }
                                            else {
                                                System.out.printf(RED_MESSAGE, "deviceUID save of user " + username + " failed!");
                                                client.close();
                                                System.out.printf(
                                                        USER_DISCONNECTED_BY_SERVER,
                                                        username, registeredUser.getUserID(),
                                                        "device UID save failed after 3 attempts"
                                                );
                                            }
                                        }
                                        else {
                                            client.close();
                                            System.out.printf(
                                                    RED_MESSAGE, "deviceUID is null! User " + username
                                                    + "was forcibly disconnected"
                                            );
                                        }
                                    }
                                }
                            }
                        }
                    }
                    catch (IOException e){
                        System.out.printf(ERROR_OCCURRED, "handle connection of client with ip " + ip + ": " + e.getMessage());
                        try {
                            client.close();
                        }
                        catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }).start();
            }
        }
        catch (IOException e){
            System.out.printf(RED_MESSAGE,"An I/O error occurred(server was disabled): " + e.getMessage());
            e.printStackTrace();
        }

    }

    /**
     * Initializes secure server socket to handle clients with encryption algorithms
     * @return {@link SSLServerSocket} instance or {@code null} if something went wrong
     */
    private static SSLServerSocket initSecureServerSocket(){
        try (FileInputStream fileInput = new FileInputStream(KEY_STORE_PATH)){
            KeyStore keyStore = KeyStore.getInstance(KEY_STORE_TYPE);
            keyStore.load(fileInput,KEY_STORE_PASSWORD.toCharArray());

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KEY_MANAGER_ALGORITHM);
            keyManagerFactory.init(keyStore,KEY_STORE_PASSWORD.toCharArray());

            SSLContext context = SSLContext.getInstance(ENCRYPTION_ALGORITHM);
            context.init(keyManagerFactory.getKeyManagers(),null,null);

            SSLServerSocketFactory factory = context.getServerSocketFactory();
            return (SSLServerSocket) factory.createServerSocket(PORT);
        }
        catch (KeyStoreException | NoSuchAlgorithmException |
               UnrecoverableKeyException | KeyManagementException |
               IOException | CertificateException e) {
            System.out.printf(
                    ERROR_OCCURRED,
                    "initialize secure server socket: " + e.getMessage()
            );
            return null;
        }
    }


    private static AuthorizationResult authorizeUser(Socket client, String username, String password) {
        AuthorizationResult authResult = DatabaseHandler.authorizeUser(username, password);

        if (authResult.isSuccess()) {
            User user = authResult.user();

            try {
                user.setInputReader(new BufferedReader(new InputStreamReader(client.getInputStream())));
                user.setOutputWriter(new PrintWriter(client.getOutputStream(),true));
                user.setClient(client);
            }
            catch (IOException e){
                System.out.printf(ERROR_OCCURRED, "authorize user " + username + ": " + e.getMessage());
                return new AuthorizationResult(false, null, Error.SERVER_ERROR);
            }
        }
        return authResult;
    }

    private static RegistrationResult registerUser(Socket client, String username, String password){
        RegistrationResult registrationResult = DatabaseHandler.registerUser(username, password);

        if (registrationResult.isSuccess()){
            try {
                User user = registrationResult.user();
                user.setInputReader(new BufferedReader(new InputStreamReader(client.getInputStream())));
                user.setOutputWriter(new PrintWriter(client.getOutputStream(), true));
                user.setClient(client);
            }
            catch (IOException e){
                System.out.printf(ERROR_OCCURRED, "register user " + username + ": " + e.getMessage());
                return new RegistrationResult(false,null, Error.SERVER_ERROR);
            }
        }
        return registrationResult;
    }

    private static void handleClient(User user) {
        onlineUsers.add(user);
        DatabaseHandler.setLastUserOnline(user,null);

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
                        //TODO: use parseClientRequest method
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
            DatabaseHandler.setLastUserOnline(user, System.currentTimeMillis());
            System.out.println("User " + user.getUsername() + "(ip: " + ip + ") disconnected");
        }
        catch (IOException e) {
            boolean isClosed = user.getClient().isClosed();
            if (isClosed){
                System.out.printf("User %s(userID: %d) disconnected: %s", user.getUsername(),user.getUserID(),"client initiated disconnect\n");
                onlineUsers.remove(user);
                DatabaseHandler.setLastUserOnline(user,System.currentTimeMillis());
            }
            else {
                String clientInstanceDesc = String.format("%s(userID: %d, ip: %s)", user.getUsername(), user.getUserID(), user.getClient().getInetAddress());
                System.out.printf(ERROR_OCCURRED, "trying to handle client of user" + clientInstanceDesc);
                e.printStackTrace();
            }
        }

    }

    private static ClientRequest parseCLientRequest(String json){
        try {
            return gson.fromJson(json, ClientRequest.class);
        }
        catch (JsonSyntaxException e){
            System.out.printf(
                    ERROR_OCCURRED,
                    "parse client request: " + e.getMessage()
            );
            return null;
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
     * @deprecated Requires changes
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
