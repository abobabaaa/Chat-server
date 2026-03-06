package org.example;

import org.example.messages.Message;
import org.example.messages.TextMessage;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class DatabaseHandler {
    private static final String url = "DB_URL";
    private static final String username = "DB_USERNAME";
    private static final String password = "DB_PASSWORD";

    private static final String ERROR_TEMPLATE =
            ConsoleColor.RED +
            "[DATABASE]: An error occurred while trying to %s: \n%s" +
            ConsoleColor.RESET_COLOR + "\n";

    private static final String WARNING_TEMPLATE =
            ConsoleColor.YELLOW +
            "[DATABASE]: WARNING! %s while trying to %s\n(%s)" +
            ConsoleColor.RESET_COLOR;

    public static Connection getConnection(){
        try {
            return DriverManager.getConnection(url,username,password);
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
                    password varchar(256) not null
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
                    delivered boolean not null default false,
                    date timestamp default current_timestamp
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
        try {
            Statement stmt = connection.createStatement();
            stmt.execute(createUsersTable);

            stmt.execute(createPrivateChatsTable);
            stmt.execute(addCheckChatTrigger);

            stmt.execute(createMessagesTable);

            stmt.addBatch(add_prc_fk_user1_id);
            stmt.addBatch(add_prc_fk_user2_id);
            stmt.addBatch(add_msg_fk_chat_id);
            stmt.addBatch(add_msg_fk_from_user_id);

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
                    chats.add(new Chat(chatID,user1,user2));
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
                    return new Chat(chatId,user1,user2);
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
