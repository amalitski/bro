package ru.timebook.bro.app.listeners;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.timebook.bro.flow.configs.Config;
import ru.timebook.bro.flow.utils.InfoUtil;

@Component
public class InfoEventListener {
    @Autowired
    private Config config;

    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {
        InfoUtil.showUsage();
        InfoUtil.showConfig(config);
    }
}
