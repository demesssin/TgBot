package ru.relex.controller;

import lombok.extern.log4j.Log4j;
import org.apache.log4j.Logger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.annotation.PostConstruct;


@Component
@Log4j
public class TelegramBot extends TelegramLongPollingBot {
    @Value("${bot.name}")
    private String botName;
    @Value("${bot.token}")
    private String botToken;
    private UpdateController updateController;

    public TelegramBot(UpdateController updateController) { // то о чем я и говорил про связку классов
        // тут мы создаем такой же метод, и связываем обьекты классов друг с другом
        this.updateController = updateController;
    }

    @PostConstruct
    public void init(){ // сюда мы передаем ссылку на телеграмбота
        // таким образом бот может передать сообщение в контроллер,
        // а контроллер может передать ответ обратно в бота
        // это мы делаем что бы убрать баг касательно unable to deserialize error
        updateController.registerBot(this);
    }

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
        updateController.processUpdate(update);

    }

    public void sendAnswerMessage(SendMessage message) {
        if (message != null) {
            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error(e);
            }
        }
    }
}
