package org.example;

import org.example.messages.Message;
import org.example.messages.TextMessage;
import org.jetbrains.annotations.NotNull;
import org.mindrot.jbcrypt.BCrypt;

import java.net.InetAddress;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class DatabaseHandler {
    private static final String DB_URL = "DB_URL";
    private static final String DB_USERNAME = "DB_USERNAME";
    private static final String DB_PASSWORD = "DB_PASSWORD";

    private static final String ERROR_TEMPLATE =
            ConsoleColor.RED +
            "[DATABASE]: An error occurred while trying to %s: \n%s" +
            ConsoleColor.RESET_COLOR + "\n";

    private static final String WARNING_TEMPLATE =
            ConsoleColor.YELLOW +
            "[DATABASE]: WARNING! %s while trying to %s\n(%s)" +
            ConsoleColor.RESET_COLOR + "\n";

    private static final String DB_MESSAGE = "[DATABASE]: %s\n";

    public static Connection getConnection(){
        try {
            return DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean initDatabase(){
        Connection connection = getConnection();
        String createUsersTable =
                """
                create table if not exists users(
                    id int primary key auto_increment,
                    username varchar(64) not null unique,
                    password varchar(256) not null,
                    last_online timestamp
                )
                """;

        String createPrivateChatsTable =
                """
                create table if not exists private_chats(
                    id int primary key auto_increment,
                    user_1_id int not null,
                    user_2_id int not null
                )
                """;

        String addCheckChatTrigger =
                """
                create trigger if not exists check_chat before insert on private_chats
                for each row begin
                    if exists(
                        select * from private_chats where
                              (NEW.user_1_id = user_1_id and NEW.user_2_id = user_2_id) or
                              (NEW.user_2_id = user_2_id and NEW.user_1_id = user_1_id)
                    )then
                        signal sqlstate '45000' set message_text = 'Error. Chat for these 2 users is already exists';
                    end if;
                end;
                """;

        String createMessagesTable =
                """
                create table if not exists messages(
                    id int primary key auto_increment,
                    chat_id int not null,
                    from_user_id int not null,
                    text varchar(256) not null,
                    constraint chk_is_text_empty check(trim(text) != ''),
                    delivered boolean not null default false,
                    viewed_date timestamp,
                    date timestamp default current_timestamp
                )
                """;

        //TODO: Make ip field unique
        String createAuthRequestsTable =
                """
                create table if not exists auth_requests(
                    id int not null primary key auto_increment,
                    ip varchar(32) not null unique,
                    target_user int not null,
                    created_at timestamp default current_timestamp
                )
                """;

        String createDevicesTable =
                """
                create table if not exists devices(
                device_uid varchar(46) not null primary key,
                user_id int not null
                )
                """;

        String add_prc_fk_user1_id =
                """
                alter table private_chats add constraint pr_fk_user1_id foreign key(user_1_id) references users(id)
                on delete cascade
                on update restrict
                """;

        String add_prc_fk_user2_id =
                """
                alter table private_chats add constraint pr_fk_user2_id foreign key(user_2_id) references users(id)
                on delete cascade
                on update restrict
                """;

        String add_msg_fk_chat_id =
                """
                alter table messages add constraint msg_fk_chat_id foreign key(chat_id) references private_chats(id)
                on delete cascade
                on update restrict
                """;

        String add_msg_fk_from_user_id =
                """
                alter table messages add constraint msg_fk_from_user_id foreign key(from_user_id) references users(id)
                on update restrict
                """;

        String add_devices_fk_user_id =
                """
                alter table devices add constraint fk_user_id
                foreign key(user_id) references users(id)
                on delete cascade
                on update restrict
                """;

        String add_auth_req_fk_target_user_id =
                """
                alter table auth_requests add constraint fk_target_user_id
                foreign key(target_user) references users(id)
                on delete cascade
                on update restrict
                """;
        try {
            Statement stmt = connection.createStatement();
            stmt.execute(createUsersTable);

            stmt.execute(createPrivateChatsTable);
            stmt.execute(addCheckChatTrigger);

            stmt.execute(createMessagesTable);
            stmt.execute(createDevicesTable);
            stmt.execute(createAuthRequestsTable);

            stmt.addBatch(add_prc_fk_user1_id);
            stmt.addBatch(add_prc_fk_user2_id);
            stmt.addBatch(add_msg_fk_chat_id);
            stmt.addBatch(add_msg_fk_from_user_id);
            stmt.addBatch(add_devices_fk_user_id);
            stmt.addBatch(add_auth_req_fk_target_user_id);

            stmt.executeBatch();
            stmt.clearBatch();
            return true;
        }
        catch (SQLException e){
            if (e.getErrorCode() == 1826){
                System.out.println("[DATABASE]: Constraints are already exist");
                return true;
            }

            System.out.printf(ERROR_TEMPLATE,"initialize the database",e.getMessage());
            e.printStackTrace();
            return false;
        }

    }

    //Object to change
    public static boolean downloadDataToServerMemory(List<User> users, List<Chat> chats){
        Connection connection = getConnection();
        try {
            Statement stmt = connection.createStatement();
            ResultSet resultSet = stmt.executeQuery("select * from users");

            while (resultSet.next()){
                int userID = resultSet.getInt("id");
                String username = resultSet.getString("username");

                users.add(new User(userID,username));
            }

            resultSet = stmt.executeQuery("select * from private_chats");
            while (resultSet.next()){
                int chatID = resultSet.getInt("id");
                int user1ID = resultSet.getInt("user_1_id");
                int user2ID = resultSet.getInt("user_2_id");

                User user1 = null;
                User user2 = null;
                for (User user : users){
                    if (user.getUserID() == user1ID)
                        user1 = user;
                    if (user.getUserID() == user2ID)
                        user2 = user;
                }

                if (user1 != null && user2 != null){
                    chats.add(new Chat(chatID,user1ID,user2ID));
                }
                else {
                    System.out.printf(
                            ERROR_TEMPLATE,
                            "create chat instance for users with IDs " + user1ID + "and" + user2ID,
                            "Such chat may not exist! Check database"
                    );
                    return false;
                }
            }
            return true;
        }
        catch (SQLException e){
            System.out.printf(ERROR_TEMPLATE, "download data to server memory", e.getMessage());
            return false;
        }
    }

    public static boolean checkUser(int id, String username){
        String sql = "select * from users where id = ? and username = ?";
        Connection connection = getConnection();

        try {
            PreparedStatement prstmt = connection.prepareStatement(sql);
            prstmt.setInt(1,id);
            prstmt.setString(2,username);

            ResultSet resultSet = prstmt.executeQuery();
            return resultSet.next();
        }
        catch (SQLException e){
            System.out.printf(ERROR_TEMPLATE,"select user from the database",e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static AuthorizationResult authorizeUser(String username, String password, String deviceUID){
        String selectUser = "select * from users where username = ?";
        Connection connection = getConnection();

        try {
            PreparedStatement prstmt = connection.prepareStatement(selectUser);
            prstmt.setString(1, username);
            ResultSet resultSet = prstmt.executeQuery();

            int userID;
            if (resultSet.next()){
                String hashedPassword = resultSet.getString("password");
                userID = resultSet.getInt("id");

                if (deviceUID == null)
                    return new AuthorizationResult(false, null, userID, Error.AUTH_NO_DEVICE_UID);

                if (BCrypt.checkpw(password,hashedPassword)){

                    String selectDevice = "select * from devices where user_id = ?";
                    prstmt = connection.prepareStatement(selectDevice);
                    prstmt.setInt(1, userID);

                    resultSet = prstmt.executeQuery();

                    while (resultSet.next()){
                        String dbUID = resultSet.getString("device_uid");
                        if (dbUID.equals(deviceUID)){
                            User user = new User(userID,username);
                            return new AuthorizationResult(true,user,userID,null);
                        }
                    }
                    return  new AuthorizationResult(false,null,userID,Error.AUTH_UNKNOWN_DEVICE);
                }
                else {
                    return new AuthorizationResult(false,null,userID,Error.AUTH_INCORRECT_PASSWORD);
                }
            }
            else {
                System.out.printf(WARNING_TEMPLATE,
                        "Selection query returned nothing(expected user)",
                        "authorize user",
                        "user with username " + username + " not found"
                );
                return new AuthorizationResult(false,null,0,Error.AUTH_USER_NOT_FOUND);
            }
        }
        catch (SQLException e){
            System.out.printf(ERROR_TEMPLATE,"authorize user",e.getMessage());
            e.printStackTrace();
            return new AuthorizationResult(false,null,0,Error.DATABASE_ERROR);
        }
    }

    public static RegistrationResult registerUser(String username, String password) {
        Connection connection = getConnection();
        String select = "select * from users where username = ?";
        try {
            PreparedStatement prstmt = connection.prepareStatement(select);
            prstmt.setString(1,username);

            ResultSet resultSet = prstmt.executeQuery();
            if (resultSet.next())
                return new RegistrationResult(false,null, Error.REG_USERNAME_OCCUPIED);
            else {
                String insert = "insert into users(username, password) values(?, ?)";
                prstmt = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);

                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
                prstmt.setString(1, username);
                prstmt.setString(2, hashedPassword);

                int rows = prstmt.executeUpdate();
                if (rows > 0){
                    ResultSet generatedKeys = prstmt.getGeneratedKeys();
                    if (generatedKeys.next()){
                        int userID = generatedKeys.getInt(1);
                        User user = new User(userID,username);
                        return new RegistrationResult(true, user, null);
                    }
                    else {
                        System.out.printf(
                                WARNING_TEMPLATE,
                                "Insertion query returned nothing(expected userID)",
                                "register new user",
                                "registerUser method"
                        );
                        return new RegistrationResult(false,null,Error.DATABASE_ERROR);
                    }
                }
                else {
                    System.out.printf(
                            WARNING_TEMPLATE,
                            "insertion query failed",
                            "register new user",
                            "registerUser method"
                    );
                    return new RegistrationResult(false, null, Error.DATABASE_ERROR);
                }
            }
        }
        catch (SQLException e){
            System.out.printf(ERROR_TEMPLATE, "register new user", e.getMessage());
            return  new RegistrationResult(false, null, Error.DATABASE_ERROR);
        }
    }

    public static boolean saveDeviceUID(int userID, String deviceUID){
        Connection connection= getConnection();
        String insert = "insert into devices(device_uid, user_id) values(?, ?)";
        try {
            PreparedStatement prstmt = connection.prepareStatement(insert);
            prstmt.setString(1,deviceUID);
            prstmt.setInt(2, userID);

            int rows = prstmt.executeUpdate();
            if (rows > 0){
                return true;
            }
            System.out.printf(
                    WARNING_TEMPLATE,
                    "Insertion query returned 0",
                    "insert new device UID",
                    "saveDeviceUID method"
            );
            return false;
        }
        catch (SQLException e){
            System.out.printf(ERROR_TEMPLATE, "insert device UID", e.getMessage());
            return false;
        }
    }

    public static Auth2FARequest create2FARequest(InetAddress ip, int targetUser){
        Connection conn = getConnection();
        String insert = "insert into auth_requests(ip, target_user) values(?, ?)";
        try {
            PreparedStatement prstmt = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);
            prstmt.setString(1,ip.toString());
            prstmt.setInt(2,targetUser);

            int rows = prstmt.executeUpdate();

            if (rows > 0){
                ResultSet generatedKeys = prstmt.getGeneratedKeys();
                if (generatedKeys.next()){
                    int requestID = generatedKeys.getInt(1);

                    String select = "select created_at from auth_requests where id = ?";
                    prstmt = conn.prepareStatement(select);
                    prstmt.setInt(1, requestID);

                    ResultSet resultSet = prstmt.executeQuery();
                    if (resultSet.next()){
                        Timestamp date = resultSet.getTimestamp(1);
                        return new Auth2FARequest(requestID,ip,targetUser,date);
                    }
                    else {
                        System.out.printf(
                                WARNING_TEMPLATE,
                                "Selection query returned nothing(expected 2FA request date)",
                                "select date of created 2FA request from the database",
                                "create2FAAuthRequest method"
                        );
                        return null;
                    }
                }
                else{
                    System.out.printf(
                            WARNING_TEMPLATE,
                            "Insertion query returned nothing(expected 2FA request id)",
                            "create new 2FA request",
                            "create2FARequest method"
                    );
                    return null;
                }
            }
            else {
                System.out.printf(
                        WARNING_TEMPLATE,
                        "insertion query returned 0",
                        "create new 2FA authorization request",
                        "create2FAAuthRequest method"
                );
                return null;
            }

        }
        catch (SQLException e){
            System.out.printf(ERROR_TEMPLATE, "create authorization request", e.getMessage());
            return null;
        }
    }

    /**
     * Removes 2FA request with a specified id from the database
     * @param requestID id of 2FA request to remove
     */
    public static void remove2FARequest(int requestID) {
        Connection connection = getConnection();
        String remove = "delete from auth_requests where id = ?";
        try {
            PreparedStatement prstmt = connection.prepareStatement(remove);
            prstmt.setInt(1, requestID);

            int rows = prstmt.executeUpdate();
            if (rows > 0){
                System.out.printf(DB_MESSAGE, "Successfully removed 2FA request with id " + requestID + "!");
            }
            else {
                System.out.printf(
                        WARNING_TEMPLATE,
                        "deletion request returned 0",
                        "remove 2FA request with id" + requestID +". Request with such id is not exists",
                        "remove2FARequest method"
                );
            }
        }
        catch (SQLException e){
            System.out.printf(ERROR_TEMPLATE, "remove 2FA request from the database", e.getMessage());
        }
    }

    public static void setLastUserOnline(User user, Long datetime){
        Connection conn = getConnection();
        int userID = user.getUserID();
        String username = user.getUsername();

        try {
            PreparedStatement prstmt = conn.prepareStatement("update users set last_online = ? where id = ? and username = ?");
            if (datetime == null){
                prstmt.setNull(1,Types.TIMESTAMP);
            }
            else {
                prstmt.setTimestamp(1,new Timestamp(datetime));
            }
            prstmt.setInt(2,userID);
            prstmt.setString(3,username);
            int rows = prstmt.executeUpdate();

            if (rows < 0){
                System.out.printf(
                        WARNING_TEMPLATE,
                        "Update query returned 0",
                        "update last online datetime",
                        "setLastUserOnline method"
                );
            }
        }
        catch (SQLException e){
            System.out.printf(
                    ERROR_TEMPLATE,
                    "update last online datetime",
                    e.getMessage()
            );
        }
    }

    public static Chat createChat(User user1, User user2){
        Connection connection = getConnection();
        try {
            String addChat = "insert into private_chats(user_1_id, user_2_id) values(?, ?)";
            PreparedStatement prstmt = connection.prepareStatement(addChat);

            User userBuf = user1;
            if (user2.getUserID() < user1.getUserID()){
                user1 = user2;
                user2 = userBuf;
            }

            int user1Id = user1.getUserID();
            int user2Id = user2.getUserID();

            prstmt.setInt(1,user1Id);
            prstmt.setInt(2,user2Id);

            int rows = prstmt.executeUpdate();
            if (rows > 0){
                prstmt = connection.prepareStatement("select id from private_chats where user_1_id = ? and user_2_id = ?");
                prstmt.setInt(1,user1Id);
                prstmt.setInt(2,user2Id);

                ResultSet resultSet = prstmt.executeQuery();
                if (resultSet.next()){
                    int chatId = resultSet.getInt("id");
                    return new Chat(chatId,user1Id,user2Id);
                }
                System.out.println("[DATABASE]: Chat wasn't created for some reasons(DatabaseHandler:166)");
                return null;
            }
            System.out.println("[DATABASE]: Chat wasn't created for some reasons(DatabaseHandler:169)");
            return null;
        }
        catch (SQLException e){
            System.out.printf(ERROR_TEMPLATE,"create new private chat",e.getMessage());
            return null;
        }
    }

    public static boolean addGroupChat(){
        return false;
    }

    /**
     * this method adds {@link Message} object from client to the database and sets
     * {@code date} and {@code messageID} fields for this object
     * @param message {@link Message} object from client
     */
    public static boolean addMessage(Message message){
        Connection connection = getConnection();

        int chatID = message.getChatID();
        int userID = message.getFromUser().getUserID();
        try {

            if (message instanceof TextMessage){
                String insertMessage = "insert into messages(chat_id, from_user_id, text) values(?, ?, ?)";
                PreparedStatement prstmt = connection.prepareStatement(insertMessage, Statement.RETURN_GENERATED_KEYS);

                prstmt.setInt(1, chatID);
                prstmt.setInt(2, userID);
                prstmt.setString(3, ((TextMessage) message).getText());

                prstmt.executeUpdate();
                ResultSet insertResult = prstmt.getGeneratedKeys();

                if (insertResult.next()){
                    int messageId = insertResult.getInt(1);
                    String selectMessage ="select * from messages where id = ?";

                    prstmt = connection.prepareStatement(selectMessage);
                    prstmt.setInt(1, messageId);

                    ResultSet selectResult = prstmt.executeQuery();

                    if (selectResult.next()){
                        Timestamp timestamp = selectResult.getTimestamp("date");
                        LocalDateTime dateTime = getLocalDateTime(timestamp);

                        message.setMessageID(messageId);
                        message.setDate(dateTime);
                        return true;
                    }
                    else{
                        System.out.printf(
                                WARNING_TEMPLATE,
                                "Selection query returned nothing",
                                "select new message from the database",
                                "addMessage method"
                        );
                        return false;
                    }

                    //Photo messages handling or other

                }
                else {
                    System.out.printf(
                            WARNING_TEMPLATE,
                            "Insertion query returned nothing",
                            "receive generated id of new message from the database",
                            "addMessage method"
                    );
                    return false;
                }
            }

        }
        catch (SQLException e){
            System.out.printf(
                    ERROR_TEMPLATE,
                    "add new message to the database",
                    e.getMessage()
            );
            return false;
        }
        return false;
    }

    /**
     * Marks message with a specified messageID as delivered
     * @param messageID {@code messageID} of message that will be marked as delivered
     * @return {@code userID} of message sender or 0 if something went wrong
     */
    public static int markMessageAsDelivered(int messageID){
        Connection conn = getConnection();
        try {
            PreparedStatement prstmt = conn.prepareStatement("update messages set delivered = true where id = ?");
            prstmt.setInt(1,messageID);
            int rows = prstmt.executeUpdate();
            if (rows > 0){
                prstmt = conn.prepareStatement("select from_user_id from messages where id = ?");
                prstmt.setInt(1,messageID);
                ResultSet resultSet = prstmt.executeQuery();
                if (resultSet.next()){
                    return resultSet.getInt(1);
                }
                else {
                    System.out.printf(
                            WARNING_TEMPLATE,
                            "Selection result returned nothing(expected userID)",
                            "select sender userID from messages table",
                            "markMessageAsDelivered method"
                    );
                    return 0;
                }
            }
            else {
                System.out.printf(
                        WARNING_TEMPLATE,
                        "Data update failed",
                        "update field in the messages table",
                        "markMessageAsDelivered method"
                );
                return 0;
            }
        }
        catch (SQLException e){
            System.out.printf(
                    ERROR_TEMPLATE,
                    "mark message as delivered",
                    e.getMessage()
            );
            return 0;
        }
    }

    /**
     * Marks message with a specified messageID as viewed(inserts view date in the database)
     * @param messageID of a message to be marked as viewed
     * @param requestSenderUserID userID of user, whose client tries to view message
     * @return sender {@code userID} if everything ok
     * <p>0 if something went wrong</p>
     * <p>-1 if client attempted to view message sent by himself</p>
     *
     */
    public static int markMessageAsViewed(int messageID, int requestSenderUserID) {
        Connection conn = getConnection();
        try {
            PreparedStatement prstmt = conn.prepareStatement("select from_user_id from messages where id = ?");
            prstmt.setInt(1,messageID);
            ResultSet fromUserSelection = prstmt.executeQuery();

            if (fromUserSelection.next()){
                int msgSenderUserID = fromUserSelection.getInt(1);
                if (msgSenderUserID == requestSenderUserID)
                    return -1;
            }
            else {
                System.out.printf(
                        WARNING_TEMPLATE,
                        "Selection query returned nothing(expected userID)",
                        "select sender userID from messages table",
                        "markMessageAsViewed method"
                );
                return 0;
            }

            prstmt = conn.prepareStatement("update messages set viewed_date = ? where id = ?");
            Timestamp viewDatetime = new Timestamp(System.currentTimeMillis());
            prstmt.setTimestamp(1,viewDatetime);
            prstmt.setInt(2,messageID);

            int rows = prstmt.executeUpdate();
            if (rows > 0)
                return fromUserSelection.getInt(1);
            else {
                System.out.printf(
                        WARNING_TEMPLATE,
                        "Data update failed",
                        "update field in the messages table",
                        "markMessageAsViewed method"
                );
                return 0;
            }
        }
        catch (SQLException e){
            System.out.printf(
                    ERROR_TEMPLATE,
                    "mark message as viewed",
                    e.getMessage()
            );
            return 0;
        }
    }

    @NotNull
    private static LocalDateTime getLocalDateTime(Timestamp timestamp) {
        Date date = new Date(timestamp.getTime());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);

        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int month = calendar.get(Calendar.MONTH);
        int year = calendar.get(Calendar.YEAR);
        //24 hour format(not AM/PM)
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        return LocalDateTime.of(year,month,day,hour,minute);
    }
}
