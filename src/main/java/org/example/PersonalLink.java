package org.example;

import com.mongodb.client.MongoDatabase;

import java.io.File;
import java.io.FileWriter;
import java.util.Properties;
import java.util.Scanner;
import java.util.UUID;

import static org.example.WorkingWithTheMongoDB.*;

/**
 * Класс PersonalLink - отвечает за логику создания персональной короткой ссылки и работу с ней.
 */
public class PersonalLink {
    private final String personalUUID;
    private final String shortURL;

    private static String generatePersonalUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Конструктор для создания персональной ссылки:
     * - personalUUID: уникальный идентификатор ссылки (важно не путать с "идентификатором пользователя",
     *   но по условию ТЗ именно этот UUID будет выступать проверкой прав редактирования/удаления).
     * - shortURL: сама короткая ссылка (берём часть UUID).
     */
    public PersonalLink() {
        this.personalUUID = generatePersonalUUID();
        this.shortURL = "clck.ru/" + this.personalUUID.substring(0, 6);
    }

    public String getUUID() {
        return this.personalUUID;
    }

    public String getShortURL() {
        return this.shortURL;
    }

    /**
     * Фабричный метод для получения нового объекта PersonalLink
     */
    public static PersonalLink getShortURLAndUUID() {
        return new PersonalLink();
    }

    /**
     * Загрузка UUID в файл (при желании пользователь может сохранить себе файл
     * с идентификатором созданной ссылки, чтобы потом иметь к нему доступ).
     */
    public static String uploadingData(String uuid, String nameFile) {
        String pathToCatalog = System.getProperty("user.dir");
        try {
            File file = new File(pathToCatalog + "/" + nameFile);
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(uuid);
            fileWriter.close();
            return pathToCatalog;
        } catch (Exception e) {
            System.out.println("Ошибка: " + e);
        }
        return pathToCatalog;
    }

    /**
     * Основная логика работы с короткими ссылками (меню "2 - перейти к работе с URL").
     * @param dataBase - подключение к MongoDB
     * @param config - загруженные параметры конфигурации
     */
    public static void logicWorkLink(MongoDatabase dataBase, Properties config) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            try {
                System.out.println("\n1 - [получить информацию]"
                        + "\n2 - [создать новую ссылку]"
                        + "\n3 - [перейти по короткой ссылке]"
                        + "\n4 - [изменить лимит переходов]"
                        + "\n5 - [удалить ссылку]"
                        + "\n6 - [шаг назад]");
                System.out.print("\nВыберите действие: ");

                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    System.out.println("Пустой ввод. Попробуйте снова.");
                    continue;
                }

                int choice = Integer.parseInt(input);

                switch (choice) {
                    case 1:
                        System.out.print("\nВведите Ваш персональный UUID, чтобы получить информацию по ссылке: ");
                        String uuidInfo = scanner.nextLine().trim();
                        getInfoOfLink(uuidInfo, dataBase);
                        break;

                    case 2:
                        System.out.print("\nВведите оригинальный URL-адрес, чтобы получить сокращенный: ");
                        String longURL = scanner.nextLine().trim();

                        long userTTL = askUserTTL(scanner);
                        int userTrafficLimit = askUserTrafficLimit(scanner);

                        long configTTL = Long.parseLong(config.getProperty("defaultTTL", "3600"));
                        int configLimit = Integer.parseInt(config.getProperty("defaultTrafficLimit", "5"));

                        long finalTTL = Math.min(userTTL, configTTL);
                        int finalTraffic = Math.max(userTrafficLimit, configLimit);

                        PersonalLink personalLink = PersonalLink.getShortURLAndUUID();
                        String shortURL = personalLink.getShortURL();
                        String personalUUID = personalLink.getUUID();

                        createTTLIndex(dataBase);
                        insertInfoLinkWithTTL(dataBase, personalUUID, longURL, shortURL, finalTTL, finalTraffic);

                        System.out.println("\nВы успешно создали новый URL (!!!обязательно сохраните UUID!!!) ->");
                        getInfoOfLink(personalUUID, dataBase);

                        System.out.println("\nЖелаете выгрузить UUID, чтобы не потерять его? -> ");
                        System.out.println("1 - [Да]\n2 - [Нет]");
                        System.out.print("\nВыберите действие: ");
                        String inputChoiceTwo = scanner.nextLine().trim();
                        int choiceTwo = Integer.parseInt(inputChoiceTwo);

                        if (choiceTwo == 1) {
                            System.out.print("\nВведите название, под которым хотите сохранить файл -> ");
                            String nameFile = scanner.nextLine();
                            String pathToFile = uploadingData(personalUUID, nameFile);
                            System.out.println("\nИнформация выгружена в файл по пути: " + pathToFile);
                        }
                        break;

                    case 3:
                        System.out.print("\nВведите короткую ссылку (формат: clck.ru/ABCDEF): ");
                        String shortUrlToGo = scanner.nextLine().trim();
                        goToShortLink(shortUrlToGo, dataBase);
                        break;

                    case 4:
                        System.out.print("\nВведите UUID ссылки, лимит которой хотите изменить: ");
                        String uuidUpdate = scanner.nextLine().trim();
                        updateTrafficLimit(uuidUpdate, dataBase, scanner);
                        break;

                    case 5:
                        System.out.print("\nВведите UUID ссылки, которую хотите удалить: ");
                        String uuidDelete = scanner.nextLine().trim();
                        deleteLink(uuidDelete, dataBase);
                        break;

                    case 6:
                        System.out.println("\nВыход в главное меню...");
                        return;

                    default:
                        System.out.println("Некорректный выбор, попробуйте снова.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Ошибка ввода. Пожалуйста, введите корректное число.");
            } catch (Exception e) {
                System.out.println("Произошла ошибка: " + e.getMessage());
            }
        }
    }

    /**
     * Вспомогательный метод: считываем TTL (в секундах) от пользователя.
     */
    private static long askUserTTL(Scanner scanner) {
        System.out.print("\nУкажите желаемое время существования ссылки (сек): ");
        String userTTLString = scanner.nextLine().trim();
        long userTTL = 3600;
        try {
            userTTL = Long.parseLong(userTTLString);
        } catch (NumberFormatException e) {
            System.out.println("Некорректный ввод. Установлено значение по умолчанию (3600).");
        }
        return userTTL;
    }

    /**
     * Вспомогательный метод: считываем лимит переходов от пользователя.
     */
    private static int askUserTrafficLimit(Scanner scanner) {
        System.out.print("\nУкажите лимит переходов по ссылке: ");
        String userLimitString = scanner.nextLine().trim();
        int userLimit = 5;
        try {
            userLimit = Integer.parseInt(userLimitString);
        } catch (NumberFormatException e) {
            System.out.println("Некорректный ввод. Установлено значение по умолчанию (5).");
        }
        return userLimit;
    }
}

