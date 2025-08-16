import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class TicketsAnalyzer {

    // DTO для билета
    static class Ticket {
        String carrier;
        String origin;
        String destination;
        LocalDateTime departure;
        LocalDateTime arrival;
        int price;

        Ticket(JSONObject obj) {
            this.carrier = obj.getString("carrier");
            this.origin = obj.getString("origin");
            this.destination = obj.getString("destination");
            this.price = obj.getInt("price");

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yy H:mm");
            this.departure = LocalDateTime.parse(
                    obj.getString("departure_date") + " " + obj.getString("departure_time"),
                    dateFormatter
            );
            this.arrival = LocalDateTime.parse(
                    obj.getString("arrival_date") + " " + obj.getString("arrival_time"),
                    dateFormatter
            );
        }

        Duration getFlightDuration() {
            return Duration.between(departure, arrival);
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Использование: java TicketsAnalyzer <путь к tickets.json> [ORIGIN DESTINATION]");
            return;
        }

        String filePath = args[0];
        if (filePath.startsWith("~")) {
            filePath = System.getProperty("user.home") + filePath.substring(1);
        }

        String origin = args.length >= 3 ? args[1] : "VVO";
        String destination = args.length >= 3 ? args[2] : "TLV";

        List<Ticket> tickets = new ArrayList<>();
        try {
            String content = Files.readString(Paths.get(filePath));
            JSONObject root = new JSONObject(content);
            JSONArray ticketsArray = root.getJSONArray("tickets");

            for (int i = 0; i < ticketsArray.length(); i++) {
                try {
                    tickets.add(new Ticket(ticketsArray.getJSONObject(i)));
                } catch (Exception e) {
                    System.err.println("Пропускаю некорректный билет №" + (i + 1) + ": " + e.getMessage());
                }
            }

        } catch (IOException e) {
            System.err.println("Ошибка чтения файла: " + e.getMessage());
            return;
        } catch (Exception e) {
            System.err.println("Ошибка обработки JSON: " + e.getMessage());
            return;
        }

        // Фильтруем билеты по маршруту
        List<Ticket> filtered = tickets.stream()
                .filter(t -> origin.equals(t.origin) && destination.equals(t.destination))
                .toList();

        if (filtered.isEmpty()) {
            System.out.println("Билеты по маршруту " + origin + " → " + destination + " не найдены.");
            return;
        }

        // Минимальное время полета по перевозчику
        Map<String, Duration> minByCarrier = filtered.stream()
                .collect(Collectors.groupingBy(
                        t -> t.carrier,
                        Collectors.collectingAndThen(
                                Collectors.minBy(Comparator.comparing(Ticket::getFlightDuration)),
                                opt -> opt.map(Ticket::getFlightDuration).orElse(Duration.ZERO)
                        )
                ));

        // Среднее и медианное время полета
        List<Long> durations = filtered.stream()
                .map(t -> t.getFlightDuration().toMinutes())
                .sorted()
                .toList();

        double avg = durations.stream().mapToLong(Long::longValue).average().orElse(0);

        double median;
        int size = durations.size();
        if (size % 2 == 0) {
            median = (durations.get(size / 2 - 1) + durations.get(size / 2)) / 2.0;
        } else {
            median = durations.get(size / 2);
        }

        // Вывод результатов
        System.out.println("Минимальное время полета по перевозчикам:");
        minByCarrier.forEach((carrier, d) ->
                System.out.println(carrier + ": " + d.toHoursPart() + "ч " + d.toMinutesPart() + "м"));

        System.out.println("\nСреднее время полета: " + (long) avg + " минут");
        System.out.println("Медианное время полета: " + (long) median + " минут");
        System.out.println("Разница (среднее - медиана): " + ((long) avg - (long) median) + " минут");
    }
}
