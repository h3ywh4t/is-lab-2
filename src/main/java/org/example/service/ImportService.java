package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.*;
import org.example.entity.*;
import org.example.repository.*;
import org.example.controller.WebSocketNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ImportService {

    @Autowired
    private HumanBeingRepository humanBeingRepository;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private CoordinatesRepository coordinatesRepository;

    @Autowired
    private ImportHistoryRepository importHistoryRepository;

    @Autowired
    private WebSocketNotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    private final Map<Long, ImportProgressDTO> progressMap = new ConcurrentHashMap<>();

    private List<HumanBeingDTO> parseJSON(MultipartFile file) throws Exception {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            HumanBeingDTO[] dtos = objectMapper.readValue(content, HumanBeingDTO[].class);
            return Arrays.asList(dtos);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage(), e);
        }
    }

    private List<HumanBeingDTO> parseCSV(MultipartFile file) throws Exception {
        List<HumanBeingDTO> dtos = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return dtos;
            }

            String[] headers = headerLine.split(",");
            Map<String, Integer> headerMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                headerMap.put(headers[i].trim(), i);
            }

            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) continue;

                try {
                    String[] values = parseCSVLine(line);
                    HumanBeingDTO dto = parseCSVRow(values, headerMap);
                    dtos.add(dto);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Error parsing CSV line " + lineNum + ": " + e.getMessage(), e);
                }
            }
        }
        return dtos;
    }

    private String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }

    private HumanBeingDTO parseCSVRow(String[] values, Map<String, Integer> headerMap) throws Exception {
        HumanBeingDTO dto = new HumanBeingDTO();

        dto.setName(getCSVValue(values, headerMap, "name"));

        CoordinatesDTO coords = new CoordinatesDTO();
        coords.setX(Integer.parseInt(getCSVValue(values, headerMap, "coordinates.x", "x")));
        coords.setY(Double.parseDouble(getCSVValue(values, headerMap, "coordinates.y", "y")));
        dto.setCoordinates(coords);

        dto.setRealHero(Boolean.parseBoolean(getCSVValue(values, headerMap, "realHero", "false")));

        String hasToothpick = getCSVValue(values, headerMap, "hasToothpick");
        if (hasToothpick != null && !hasToothpick.isEmpty() && !hasToothpick.equalsIgnoreCase("null")) {
            dto.setHasToothpick(Boolean.parseBoolean(hasToothpick));
        }

        CarDTO car = new CarDTO();
        String carName = getCSVValue(values, headerMap, "car.name", "carName");
        car.setName(carName);
        dto.setCar(car);

        String moodStr = getCSVValue(values, headerMap, "mood");
        if (moodStr == null || moodStr.isEmpty()) {
            throw new IllegalArgumentException("Mood cannot be null");
        }
        try {
            dto.setMood(Mood.valueOf(moodStr));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid mood value: " + moodStr + ". Valid values: SADNESS, CALM, RAGE");
        }

        dto.setImpactSpeed(Float.parseFloat(getCSVValue(values, headerMap, "impactSpeed")));

        dto.setMinutesOfWaiting(Float.parseFloat(getCSVValue(values, headerMap, "minutesOfWaiting")));

        String weaponType = getCSVValue(values, headerMap, "weaponType");
        if (weaponType != null && !weaponType.isEmpty() && !weaponType.equalsIgnoreCase("null")) {
            try {
                dto.setWeaponType(WeaponType.valueOf(weaponType));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid weaponType value: " + weaponType +
                        ". Valid values: HAMMER, RIFLE, MACHINE_GUN, BAT");
            }
        }

        return dto;
    }

    private String getCSVValue(String[] values, Map<String, Integer> headerMap, String key) {
        return getCSVValue(values, headerMap, key, null);
    }

    private String getCSVValue(String[] values, Map<String, Integer> headerMap, String key, String altKey) {
        Integer index = headerMap.get(key);
        if (index == null && altKey != null) {
            index = headerMap.get(altKey);
        }
        if (index != null && index < values.length) {
            String value = values[index].trim();
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            return value.isEmpty() ? null : value;
        }
        return null;
    }


}