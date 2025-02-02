package ru.relex.controller;

import lombok.extern.log4j.Log4j;
import org.apache.log4j.Logger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;

import javax.annotation.PostConstruct;
import java.io.InputStream;

@Component
@Log4j
public class TelegramBot extends TelegramLongPollingBot{
    @Value("${bot.name}")
    private String botName;
    @Value("${bot.token}")
    private String botToken;
    private UpdateController updateController;

    @Autowired
    public TelegramBot(UpdateController updateController) { // то о чем я и говорил про связку классов
        // тут мы создаем такой же метод, и связываем обьекты классов друг с другом
        this.updateController = updateController;
    }

    public InputStream downloadFileAsStream(Document document) {
        try {
            GetFile getFile = new GetFile();
            getFile.setFileId(document.getFileId());
            File file = execute(getFile);
            return downloadFileAsStream(file); // встроенный метод Telegram API
        } catch (TelegramApiException e) {
            log.error("Ошибка при скачивании файла", e);
            return null;
        }
    }

    @PostConstruct
    public void init(){
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

    public void sendAnswerDocument(SendDocument document) {
        try {
            execute(document);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке документа", e);
        }
    }


    public boolean isAdmin(Long chatId) {
        return chatId.equals(625054506); // ID администратора
    }
}
