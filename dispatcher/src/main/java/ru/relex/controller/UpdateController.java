package ru.relex.controller;


import lombok.extern.log4j.Log4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.relex.service.UpdateProducer;
import ru.relex.utils.MessageUtils;
import ru.relex.utils.UserDataStorage;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;

@Component
@Log4j
public class UpdateController {
    private TelegramBot telegramBot;
    private final MessageUtils messageUtils;
    private final UpdateProducer updateProducer;
    private final UserDataStorage userDataStorage;

    private String currentUserCheckNumber;
    private int currentInputStep = 0;

    public UpdateController(MessageUtils messageUtils, UpdateProducer updateProducer, UserDataStorage userDataStorage) {
        this.messageUtils = messageUtils;
        this.updateProducer = updateProducer;
        this.userDataStorage = userDataStorage;
    }

    public void registerBot(TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    public void processUpdate(Update update) {
        if (update == null || update.getMessage() == null) {
            log.error("Received null update");
            return;
        }

        var message = update.getMessage();

        if (message.getText() != null) {
            handleTextMessage(update);
        } else if (message.getDocument() != null) {
            handleDocumentMessage(update);
        } else {
            setUnsupportedMessageTypeView(update);
        }
    }

    private void handleTextMessage(Update update) {
        String text = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();

        if (text.equals("/start")) {
            setView(messageUtils.generateSendMessageWithText(update, "Добро пожаловать! Пожалуйста, отправьте чек в формате PDF."));
        } else if (text.equals("/export")) {
            userDataStorage.assignRandomUUIDs();
            File exportedFile = userDataStorage.exportToExcel();
            if (exportedFile != null && exportedFile.exists()) {
                sendExcelFile(update);
                sendUIDsToChat(update);
            } else {
                setView(messageUtils.generateSendMessageWithText(update, "Ошибка: Не удалось экспортировать данные в Excel."));
            }
        } else if (text.equals("/get_excel")) {
            sendExcelFile(update);
        } else {
            collectUserInfo(update, text);
        }
    }

    private void collectUserInfo(Update update, String text) {
        if (currentUserCheckNumber == null) {
            setView(messageUtils.generateSendMessageWithText(update, "Ошибка: Сначала отправьте чек в формате PDF."));
            return;
        }
        switch (currentInputStep) {
            case 0: // ФИО
                userDataStorage.userData.putIfAbsent(currentUserCheckNumber, new HashMap<>()); // Создаем запись если ее нет
                userDataStorage.userData.get(currentUserCheckNumber).put("fio", text);
                setView(messageUtils.generateSendMessageWithText(update, "Теперь введите ваш адрес:"));
                currentInputStep++;
                break;
            case 1: // Адрес
                userDataStorage.userData.get(currentUserCheckNumber).put("address", text);
                setView(messageUtils.generateSendMessageWithText(update, "Теперь введите ваш номер телефона:"));
                currentInputStep++;
                break;
            case 2: // Номер телефона
                userDataStorage.userData.get(currentUserCheckNumber).put("phone", text);
                String uid = UUID.randomUUID().toString(); // Генерируем UUID
                userDataStorage.userData.get(currentUserCheckNumber).put("uid", uid);
                setView(messageUtils.generateSendMessageWithText(update, "Спасибо! Ваши данные сохранены. Ваш UUID: " + uid));
                currentUserCheckNumber = null;
                currentInputStep = 0;
                break;
            default:
                break;
        }
    }

    private void sendUIDsToChat(Update update) {
        StringBuilder uidList = new StringBuilder("Сгенерированные UID для пользователей:\n");

        for (var entry : userDataStorage.userData.entrySet()) {
            String uid = entry.getValue().get("uid");
            uidList.append(entry.getKey()).append(": ").append(uid).append("\n");
        }

        setView(messageUtils.generateSendMessageWithText(update, uidList.toString()));
    }

    private void handleDocumentMessage(Update update) {
        if (userDataStorage.validateAndSaveCheck(update)) {
            currentUserCheckNumber = userDataStorage.extractUniqueNumber(update);
            setView(messageUtils.generateSendMessageWithText(update, "Чек принят. Пожалуйста, введите ваше ФИО:"));
            currentInputStep = 0;
        } else {
            setView(messageUtils.generateSendMessageWithText(update, "Ошибка: Чек не прошел проверку."));
        }
    }

    private void sendExcelFile(Update update) {
        Long chatId = update.getMessage().getChatId();
        File file = new File("C:\\Users\\Admin\\Documents\\userdata.xlsx");
        if (file.exists()) {
            SendDocument sendDocument = new SendDocument();
            sendDocument.setChatId(chatId.toString());
            sendDocument.setDocument(new InputFile(file));
            telegramBot.sendAnswerDocument(sendDocument);
        } else {
            setView(messageUtils.generateSendMessageWithText(update, "Файл не найден. Сначала экспортируйте данные с помощью команды /export."));
        }
    }

    private void setUnsupportedMessageTypeView(Update update) {
        setView(messageUtils.generateSendMessageWithText(update, "Неподдерживаемый тип сообщения."));
    }

    public void setView(SendMessage sendMessage) {
        telegramBot.sendAnswerMessage(sendMessage);
    }
}