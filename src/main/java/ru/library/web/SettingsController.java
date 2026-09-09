package ru.library.web;

import org.springframework.web.bind.annotation.*;
import ru.library.model.UserSettings;
import ru.library.service.UserSettingsService;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final UserSettingsService service;

    public SettingsController(UserSettingsService service) { this.service = service; }

    @GetMapping("/me")
    public UserSettings me(Principal p) { return service.get(p.getName()); }

    @PutMapping("/me")
    public UserSettings save(@RequestBody UserSettings s, Principal p) {
        return service.save(new UserSettings(p.getName(), s.language(), s.pageSize(), s.theme(),
                s.defaultHall(), s.emailNotifications()));
    }

    @DeleteMapping("/me")
    public void reset(Principal p) { service.reset(p.getName()); }

    @GetMapping("/cache-stats")
    public Map<String, Object> stats() { return service.stats(); }
}