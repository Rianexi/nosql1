package ru.library.kv;

import org.springframework.stereotype.Component;
import ru.library.config.AppProperties;

@Component
public class Keys {
    private final String p;

    public Keys(AppProperties props) { this.p = props.kv().keyPrefix(); }

    public String events()                          { return p + "/events/"; }
    public String event(String id)                  { return p + "/events/" + id; }
    public String eventViews(String id)             { return p + "/events/" + id + "/views"; }
    public String orders()                          { return p + "/orders/"; }
    public String order(String id)                  { return p + "/orders/" + id; }
    public String ordersByEvent(String e)           { return p + "/idx/orders-by-event/" + e + "/"; }
    public String orderByEvent(String e, String o)  { return ordersByEvent(e) + o; }
    public String drafts()                          { return p + "/drafts/"; }
    public String draft(String id)                  { return p + "/drafts/" + id; }
    public String draftsByEvent(String e)           { return p + "/idx/drafts-by-event/" + e + "/"; }
    public String draftByEvent(String e, String d)  { return draftsByEvent(e) + d; }
    public String usersPrefix()                     { return p + "/users/"; }
    public String userSettings(String login)        { return p + "/users/" + login + "/settings"; }

    public static String lastSegment(String key)    { return key.substring(key.lastIndexOf('/') + 1); }
}