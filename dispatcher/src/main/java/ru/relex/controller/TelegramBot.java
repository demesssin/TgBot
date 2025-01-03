package ru.relex.controller;

import lombok.extern.log4j.Log4j;
import org.apache.log4j.Logger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


@Component
@Log4j
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {
    @Value("${bot.name}")
    private String botName;
    @Value("${bot.token}")
    private String botToken;

    // private static final Logger log = Logger.getLogger(TelegramBot.class);

    @Override
    public String getBotUsername() {
        return botName;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        var originalMessage = update.getMessage();
        log.debug(originalMessage.getText());
    }
}
