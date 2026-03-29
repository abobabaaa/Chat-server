package org.example;

public record AuthorizationResult(boolean isSuccess, User user, int userID, Error error) {
    @Override
    public boolean isSuccess() {
        return isSuccess;
    }

    @Override
    public User user() {
        return user;
    }

    @Override
    public Error error() {
        return error;
    }

    @Override
    public int userID() {
        return userID;
    }
}
