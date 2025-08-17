// Импортируем классы для работы с JSON
import org.json.JSONArray;
import org.json.JSONObject;

// Импортируем стандартные Java-классы
import java.io.IOException;                      // обработка ошибок ввода/вывода
import java.nio.file.Files;                      // чтение файлов
import java.nio.file.Paths;                      // работа с путями файлов
import java.time.Duration;                       // работа с длительностью времени (разница между датами)
import java.time.LocalDateTime;                  // локальные дата и время (без часового пояса)
import java.time.format.DateTimeFormatter;       // преобразование строки в дату/время и обратно
import java.util.*;                              // коллекции, Comparator и т.д.
import java.util.stream.Collectors;              // операции со Stream API

// Главный класс анализатора билетов
public class TicketsAnalyzer {

    // DTO (Data Transfer Object) для представления одного билета
    static class Ticket {
        String carrier;        // авиакомпания
        String origin;         // аэропорт вылета (например "VVO")
        String destination;    // аэропорт прилета (например "TLV")
        LocalDateTime departure; // дата и время вылета
        LocalDateTime arrival;   // дата и время прилета
        int price;             // цена билета

        // Конструктор, принимает JSONObject и парсит данные билета
        Ticket(JSONObject obj) {
            this.carrier = obj.getString("carrier");
            this.origin = obj.getString("origin");
            this.destination = obj.getString("destination");
            this.price = obj.getInt("price");

            // Определяем формат даты и времени (например "12.05.18 16:20")
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yy H:mm");

            // Парсим дату и время вылета
            this.departure = LocalDateTime.parse(
                    obj.getString("departure_date") + " " + obj.getString("departure_time"),
                    dateFormatter
            );

            // Парсим дату и время прилета
            this.arrival = LocalDateTime.parse(
                    obj.getString("arrival_date") + " " + obj.getString("arrival_time"),
                    dateFormatter
            );
        }

        // Возвращает длительность полета как Duration
        Duration getFlightDuration() {
            return Duration.between(departure, arrival);
        }
    }

    public static void main(String[] args) {
        // Проверяем, что передан путь к файлу tickets.json
        if (args.length < 1) {
            System.err.println("Использование: java TicketsAnalyzer <путь к tickets.json> [ORIGIN DESTINATION]");
            return;
        }

        // Путь к JSON-файлу
        String filePath = args[0];

        // Поддержка записи "~/file.json" (замена ~ на домашнюю директорию)
        if (filePath.startsWith("~")) {
            filePath = System.getProperty("user.home") + filePath.substring(1);
        }

        // Задаем маршрут (по умолчанию VVO → TLV, если не переданы аргументы)
        String origin = args.length >= 3 ? args[1] : "VVO";
        String destination = args.length >= 3 ? args[2] : "TLV";

        // Список всех билетов
        List<Ticket> tickets = new ArrayList<>();
        try {
            // Читаем содержимое JSON-файла
            String content = Files.readString(Paths.get(filePath));

            // Парсим JSON-объект
            JSONObject root = new JSONObject(content);

            // Получаем массив билетов
            JSONArray ticketsArray = root.getJSONArray("tickets");

            // Пробегаем по массиву билетов
            for (int i = 0; i < ticketsArray.length(); i++) {
                try {
                    // Добавляем каждый билет в список
                    tickets.add(new Ticket(ticketsArray.getJSONObject(i)));
                } catch (Exception e) {
                    // Если билет некорректный — пропускаем и пишем в stderr
                    System.err.println("Пропускаю некорректный билет №" + (i + 1) + ": " + e.getMessage());
                }
            }

        } catch (IOException e) {
            // Ошибка чтения файла
            System.err.println("Ошибка чтения файла: " + e.getMessage());
            return;
        } catch (Exception e) {
            // Ошибка обработки JSON
            System.err.println("Ошибка обработки JSON: " + e.getMessage());
            return;
        }

        // Фильтруем билеты по выбранному маршруту
        List<Ticket> filtered = tickets.stream()
                .filter(t -> origin.equals(t.origin) && destination.equals(t.destination))
                .toList();

        // Если билеты не найдены — сообщаем и выходим
        if (filtered.isEmpty()) {
            System.out.println("Билеты по маршруту " + origin + " → " + destination + " не найдены.");
            return;
        }

        // Находим минимальное время полета для каждой авиакомпании
        Map<String, Duration> minByCarrier = filtered.stream()
                .collect(Collectors.groupingBy(
                        t -> t.carrier, // группируем по авиакомпании
                        Collectors.collectingAndThen(
                                // внутри группы ищем минимальную длительность полета
                                Collectors.minBy(Comparator.comparing(Ticket::getFlightDuration)),
                                // из Optional достаем Duration (или 0, если ничего нет)
                                opt -> opt.map(Ticket::getFlightDuration).orElse(Duration.ZERO)
                        )
                ));

        // Собираем список длительностей всех рейсов (в минутах)
        List<Long> durations = filtered.stream()
                .map(t -> t.getFlightDuration().toMinutes())
                .sorted()
                .toList();

        // Среднее время полета
        double avg = durations.stream().mapToLong(Long::longValue).average().orElse(0);

        // Медианное время полета
        double median;
        int size = durations.size();
        if (size % 2 == 0) {
            // если четное количество — берем среднее двух центральных
            median = (durations.get(size / 2 - 1) + durations.get(size / 2)) / 2.0;
        } else {
            // если нечетное — берем центральное значение
            median = durations.get(size / 2);
        }

        // Выводим результаты анализа
        System.out.println("Минимальное время полета по перевозчикам:");
        minByCarrier.forEach((carrier, d) ->
                System.out.println(carrier + ": " + d.toHoursPart() + "ч " + d.toMinutesPart() + "м"));

        System.out.println("\nСреднее время полета: " + (long) avg + " минут");
        System.out.println("Медианное время полета: " + (long) median + " минут");
        System.out.println("Разница (среднее - медиана): " + ((long) avg - (long) median) + " минут");
    }
}
