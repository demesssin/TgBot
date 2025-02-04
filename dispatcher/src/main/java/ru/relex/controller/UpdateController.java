package ru.relex.controller;

import lombok.extern.log4j.Log4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.relex.service.UpdateProducer;
import ru.relex.utils.MessageUtils;
import ru.relex.utils.UserDataStorage;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

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
            case 0:
                userDataStorage.userData.putIfAbsent(currentUserCheckNumber, new HashMap<>());
                userDataStorage.userData.get(currentUserCheckNumber).put("fio", text);
                setView(messageUtils.generateSendMessageWithText(update, "Теперь введите ваш адрес:"));
                currentInputStep++;
                break;
            case 1:
                userDataStorage.userData.get(currentUserCheckNumber).put("address", text);
                setView(messageUtils.generateSendMessageWithText(update, "Теперь введите ваш номер телефона:"));
                currentInputStep++;
                break;
            case 2:
                userDataStorage.userData.get(currentUserCheckNumber).put("phone", text);
                int uuidCount = userDataStorage.getUUIDCount(currentUserCheckNumber);

                for (int i = 0; i < uuidCount; i++) {
                    String uid = UUID.randomUUID().toString();
                    userDataStorage.addUUID(currentUserCheckNumber, uid);
                    setView(messageUtils.generateSendMessageWithText(update, "Спасибо! Ваши данные сохранены. Ваш UUID: " + uid));
                }

                currentUserCheckNumber = null;
                currentInputStep = 0;
                break;
        }
    }

    private void handleDocumentMessage(Update update) {
        try {
            File pdfFile = downloadPdfFromTelegram(update);
            double paymentAmount = extractPaymentAmountFromDocument(pdfFile);
            String checkNumber = extractCheckNumberFromDocument(pdfFile);

            if (userDataStorage.isCheckProcessed(checkNumber)) {
                setView(messageUtils.generateSendMessageWithText(update, "Ошибка: Чек с номером " + checkNumber + " уже был обработан."));
                return;
            }

            if (paymentAmount >= 7900) {
                currentUserCheckNumber = checkNumber;
                userDataStorage.userData.putIfAbsent(currentUserCheckNumber, new HashMap<>());
                userDataStorage.saveCheckNumber(currentUserCheckNumber);

                setView(messageUtils.generateSendMessageWithText(update, "Чек принят. Пожалуйста, введите ваше ФИО:"));
                currentInputStep = 0;
            } else {
                setView(messageUtils.generateSendMessageWithText(update, "Ошибка: Сумма на чеке должна быть больше или равна 7900."));
            }

        } catch (Exception e) {
            log.error("Ошибка при обработке документа", e);
            setView(messageUtils.generateSendMessageWithText(update, "Ошибка при обработке документа. Попробуйте снова."));
        }
    }

    private double extractPaymentAmountFromDocument(File pdfFile) throws IOException, TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
        tesseract.setLanguage("rus+eng");

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                log.info("Сканирование страницы: " + (page + 1));
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300);
                String pageText = tesseract.doOCR(image);
                log.info("Распознанный текст страницы: \n" + pageText);

                String[] lines = pageText.split("\n");
                boolean ignoreNextLine = false;

                for (String line : lines) {
                    log.info("Обрабатываемая строка: " + line);

                    if (ignoreNextLine) {
                        ignoreNextLine = false;
                        String amount = line.replaceAll("[^0-9,]", "").trim();
                        if (!amount.isEmpty()) {
                            log.info("Принятая сумма после игнорированной строки: " + amount);
                            return Double.parseDouble(amount.replace(",", "."));
                        }
                    }

                    if (line.contains("ИП Sulu Home(6. 18-20)")) {
                        log.info("Обнаружена строка 'ИП Sulu Home(6. 18-20)', игнорируем её и принимаем следующую строку.");
                        ignoreNextLine = true;
                        continue;
                    }

                    if (line.matches(".*(\\d{1,3}(?:\\s\\d{3})*(?:,\\d{2})?).*")) {
                        String amount = line.replaceAll("[^0-9,]", "").trim();
                        log.info("Найденная сумма: " + amount);
                        return Double.parseDouble(amount.replace(",", "."));
                    }
                }
            }
        }
        log.warn("Не удалось найти сумму на чеке.");
        return 0;
    }


    private void sendUIDsToChat(Update update) {
        StringBuilder uidList = new StringBuilder("Сгенерированные UUID для пользователей:\n");
        for (var entry : userDataStorage.userData.entrySet()) {
            String uids = entry.getValue().get("uid");
            uidList.append(entry.getKey()).append(": ").append(uids).append("\n");
        }
        setView(messageUtils.generateSendMessageWithText(update, uidList.toString()));
    }

    private String extractCheckNumberFromDocument(File pdfFile) throws IOException, TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
        tesseract.setLanguage("rus+eng");

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300);
                String pageText = tesseract.doOCR(image);

                for (String line : pageText.split("\n")) {
                    if (line.contains("№ чека")) {
                        String checkNumber = line.replaceAll("[^0-9]", "").trim();
                        log.info("Извлечён номер чека: " + checkNumber);
                        return checkNumber;
                    }
                }
            }
        }
        return UUID.randomUUID().toString();
    }

    private File downloadPdfFromTelegram(Update update) throws IOException, TelegramApiException {
        String fileId = update.getMessage().getDocument().getFileId();
        String filePathResponse = telegramBot.execute(new GetFile(fileId)).getFilePath();
        String fileUrl = "https://api.telegram.org/file/bot" + telegramBot.getBotToken() + "/" + filePathResponse;

        File pdfFile = new File("C:\\Users\\Admin\\Documents\\" + fileId + ".pdf");
        try (InputStream in = new URL(fileUrl).openStream()) {
            Files.copy(in, pdfFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        return pdfFile;
    }

    private void sendExcelFile(Update update) {
        Long chatId = update.getMessage().getChatId();
        File file = new File("C:\\Users\\Admin\\Documents\\userdata.xlsx"); // Убедитесь, что путь совпадает с тем местом, где создается файл
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
