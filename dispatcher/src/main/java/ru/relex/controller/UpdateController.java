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
import java.util.HashMap;
import java.util.UUID;



import javax.imageio.ImageIO;
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

    // Состояние ввода данных
    private String currentUserCheckNumber; // Номер чека для текущего пользователя
    private int currentInputStep = 0; // Шаг ввода (0 - ФИО, 1 - адрес, 2 - телефон)

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
            userDataStorage.assignRandomUUIDs(); // Генерация случайных UID для всех пользователей
            File exportedFile = userDataStorage.exportToExcel();
            if (exportedFile != null && exportedFile.exists()) {
                sendExcelFile(update); // Отправляем файл пользователю
                sendUIDsToChat(update); // Отправляем UID пользователям
            } else {
                setView(messageUtils.generateSendMessageWithText(update, "Ошибка: Не удалось экспортировать данные в Excel."));
            }
        } else if (text.equals("/get_excel")) {
            sendExcelFile(update); // Отправляем файл пользователю
        } else {
            collectUserInfo(update, text);
        }
    }

    private void collectUserInfo(Update update, String text) {
        // Если номер чека еще не установлен
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
                currentUserCheckNumber = null; // Сбрасываем номер чека и шаги ввода
                currentInputStep = 0;
                break;
            default:
                break;
        }
    }

    private void sendUIDsToChat(Update update) {
        StringBuilder uidList = new StringBuilder("Сгенерированные UID для пользователей:\n");

        for (var entry : userDataStorage.userData.entrySet()) { // Доступ к userData из UserDataStorage
            String uid = entry.getValue().get("uid");
            uidList.append(entry.getKey()).append(": ").append(uid).append("\n"); // Добавляем уникальный номер и его UID
        }

        setView(messageUtils.generateSendMessageWithText(update, uidList.toString()));
    }

    private void handleDocumentMessage(Update update) {
        try {
            File pdfFile = downloadPdfFromTelegram(update); // Метод для загрузки файла от пользователя

            double paymentAmount = extractPaymentAmountFromDocument(pdfFile); // Извлекаем сумму из документа

            if (paymentAmount >= 7900) {
                currentUserCheckNumber = UUID.randomUUID().toString(); // Генерируем уникальный номер чека
                userDataStorage.userData.putIfAbsent(currentUserCheckNumber, new HashMap<>()); // Создаем запись для номера чека

                setView(messageUtils.generateSendMessageWithText(update, "Чек принят. Пожалуйста, введите ваше ФИО:"));
                currentInputStep = 0; // Начинаем с ФИО
            } else {
                setView(messageUtils.generateSendMessageWithText(update, "Ошибка: Сумма на чеке должна быть больше или равна 7900."));
            }

        } catch (Exception e) {
            log.error("Ошибка при обработке документа", e);
            setView(messageUtils.generateSendMessageWithText(update, "Ошибка при обработке документа. Попробуйте снова."));
        }
    }

    private int extractPaymentAmountFromDocument(File pdfFile) throws IOException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
        tesseract.setLanguage("rus+eng");

        String numericValue = "0";
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300);
                String pageText = tesseract.doOCR(image);

                log.info("Распознанный текст:\n" + pageText);

                String[] lines = pageText.split("\n");
                for (String line : lines) {
                    if (line.toLowerCase().contains("сумма") || line.toLowerCase().contains("итого") || line.toLowerCase().contains("total")) {
                        log.info("Обнаружена строка с суммой: " + line);

                        // Удаляем все символы, кроме цифр, пробелов, запятых и точек
                        numericValue = line.replaceAll("[^0-9,.\\s]", "").trim();

                        // Объединяем разделённые пробелами числа, например "7 900" -> "7900"
                        numericValue = numericValue.replaceAll("(\\d)\\s+(\\d)", "$1$2");

                        // Заменяем запятые на точки
                        numericValue = numericValue.replace(",", ".");

                        log.info("Обнаруженное числовое значение: " + numericValue); // Логируем, какое число было обнаружено

                        try {
                            // Если значение содержит дробную часть, округляем до ближайшего целого
                            double tempAmount = Double.parseDouble(numericValue);
                            int extractedAmount = (int) Math.round(tempAmount); // Преобразуем в Integer
                            log.info("Извлеченная сумма (округлённая): " + extractedAmount);
                            return extractedAmount; // Возвращаем сумму как Integer
                        } catch (NumberFormatException e) {
                            log.error("Ошибка преобразования суммы: " + numericValue, e);
                        }
                    }
                }
            }
        } catch (TesseractException e) {
            log.error("Ошибка при выполнении OCR", e);
        }

        log.info("Финальная сумма из чека: " + numericValue);
        return Integer.parseInt(numericValue); // Возвращаем итоговое значение как Integer
    }





    private File downloadPdfFromTelegram(Update update) throws IOException, TelegramApiException {
        String fileId = update.getMessage().getDocument().getFileId();

        String filePathResponse = telegramBot.execute(new GetFile(fileId)).getFilePath();

        String fileUrl = "https://api.telegram.org/file/bot" + telegramBot.getBotToken() + "/" + filePathResponse;

        File pdfFile = new File("C:\\Users\\Admin\\Documents\\" + fileId + ".pdf");

        try(InputStream in = new URL(fileUrl).openStream()) {
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
