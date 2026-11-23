package io.bookmind.server.api;

import io.bookmind.server.plugin.PluginDescriptor;
import io.bookmind.server.plugin.PluginManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/plugins")
public class PluginController {

    private final PluginManager pluginManager;

    public PluginController(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @GetMapping
    public List<PluginDescriptor> listPlugins() {
        return pluginManager.listPlugins();
    }
}
