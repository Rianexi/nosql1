package ru.library.web;

import org.springframework.web.bind.annotation.*;
import ru.library.config.AppProperties;
import ru.library.config.SnapshotManager;
import ru.library.kv.KeyValueStore;
import ru.library.kv.KvEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final KeyValueStore store;
    private final SnapshotManager snapshots;
    private final AppProperties props;

    public AdminController(KeyValueStore store, SnapshotManager snapshots, AppProperties props) {
        this.store = store; this.snapshots = snapshots; this.props = props;
    }

    @GetMapping("/keys")
    public List<KvEntry> keys(@RequestParam(defaultValue = "/") String prefix) {
        return store.getPrefix(prefix);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", props.kv().mode());
        m.put("revision", store.currentRevision());
        m.put("keys", store.getPrefix("/").size());
        return m;
    }

    @PostMapping("/snapshot/save")
    public Map<String, String> save(@RequestParam(required = false) String path) {
        String p = path == null ? props.kv().snapshotFile() : path;
        store.snapshotSave(p);
        return Map.of("saved", p);
    }

    @PostMapping("/snapshot/restore")
    public Map<String, Object> restore(@RequestParam(required = false) String path) {
        String p = path == null ? props.kv().snapshotFile() : path;
        int orphans = snapshots.restore(p);
        return Map.of("restored", p, "orphanDraftsRemoved", orphans, "revision", store.currentRevision());
    }
}