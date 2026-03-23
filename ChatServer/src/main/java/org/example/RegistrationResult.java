package org.example;

public record RegistrationResult(boolean isSuccess, User user, Error error) {
    @Override
    public boolean isSuccess() {
        return isSuccess;
    }

    @Override
    public Error error() {
        return error;
    }

    @Override
    public User user() {
        return user;
    }
}
