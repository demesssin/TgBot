package ru.relex.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

@Component
public class UserDataStorage {
    public final Map<String, Map<String, String>> userData = new HashMap<>();
    private final Set<String> uniqueChecks = new HashSet<>();
    private final String filePath = Paths.get("C:\\Users\\Admin\\Documents\\userdata.xlsx").toAbsolutePath().toString();

    public boolean validateAndSaveCheck(String uniqueNumber) {
        if (uniqueNumber == null || uniqueChecks.contains(uniqueNumber)) return false;

        uniqueChecks.add(uniqueNumber);
        userData.putIfAbsent(uniqueNumber, new HashMap<>());
        return true;
    }

    public File exportToExcel() {
        File excelFile = new File(filePath);
        boolean fileExists = excelFile.exists();

        try (Workbook workbook = fileExists ? new XSSFWorkbook(new FileInputStream(excelFile)) : new XSSFWorkbook()) {
            Sheet sheet = fileExists ? workbook.getSheet("UserData") : workbook.createSheet("UserData");
            if (sheet == null) sheet = workbook.createSheet("UserData");

            if (!fileExists) {
                Row header = sheet.createRow(0);
                String[] columns = {"Unique Number", "FIO", "Address", "Phone", "UID"};
                for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
            }

            int rowNum = sheet.getLastRowNum() + 1;
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

    public int getUUIDCount(String checkNumber) {
        return (int) Math.max(1, userData.getOrDefault(checkNumber, new HashMap<>())
                .getOrDefault("paymentAmount", "0")
                .chars()
                .map(Character::getNumericValue)
                .sum() / 7900);
    }

    public void addUUID(String checkNumber, String uid) {
        userData.computeIfAbsent(checkNumber, k -> new HashMap<>())
                .merge("uid", uid, (oldVal, newVal) -> oldVal + "," + newVal);
    }

    public boolean isCheckProcessed(String checkNumber) {
        return uniqueChecks.contains(checkNumber);
    }

    public void saveCheckNumber(String checkNumber) {
        uniqueChecks.add(checkNumber);
    }
}
