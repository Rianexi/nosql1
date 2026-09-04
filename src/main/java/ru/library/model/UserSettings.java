package ru.library.model;

public record UserSettings(
        String login,
        String language,
        int pageSize,
        String theme,
        String defaultHall,
        boolean emailNotifications
) {
    public static UserSettings defaults(String login) {
        return new UserSettings(login, "ru", 20, "light", null, true);
    }
}