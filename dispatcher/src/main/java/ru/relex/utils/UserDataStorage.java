package ru.relex.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

@Component
public class UserDataStorage {
    public final Map<String, Map<String, String>> userData = new HashMap<>();
    private final Set<String> uniqueChecks = new HashSet<>();

    public boolean validateAndSaveCheck(Update update) {
        String uniqueNumber = extractUniqueNumber(update);
        if (uniqueNumber == null || uniqueChecks.contains(uniqueNumber)) return false;

        uniqueChecks.add(uniqueNumber);

        userData.putIfAbsent(uniqueNumber, new HashMap<>());

        return true;
    }

    public File exportToExcel() {
        String filePath = Paths.get("C:\\Users\\Admin\\Documents\\userdata.xlsx").toAbsolutePath().toString();
        File excelFile = new File(filePath);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("UserData");
            Row header = sheet.createRow(0);
            String[] columns = {"Unique Number", "FIO", "Address", "Phone", "UID"};
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);

            int rowNum = 1;
            for (var entry : userData.entrySet()) {
                Map<String, String> data = entry.getValue();
                if (!data.containsKey("fio") || !data.containsKey("address") || !data.containsKey("phone")) {
                    continue;
                }

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(data.getOrDefault("fio", ""));
                row.createCell(2).setCellValue(data.getOrDefault("address", ""));
                row.createCell(3).setCellValue(data.getOrDefault("phone", ""));
                row.createCell(4).setCellValue(data.getOrDefault("uid", ""));
            }

            try (FileOutputStream fileOut = new FileOutputStream(excelFile)) {
                workbook.write(fileOut);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return excelFile;
    }

    public void assignRandomUUIDs() {
        for (var entry : userData.entrySet()) {
            Map<String, String> data = entry.getValue();

            if (data.containsKey("fio") && data.containsKey("address") && data.containsKey("phone")) {
                data.putIfAbsent("uid", UUID.randomUUID().toString());
            }
        }
    }

    public String extractUniqueNumber(Update update) {
        return UUID.randomUUID().toString();
    }
}
