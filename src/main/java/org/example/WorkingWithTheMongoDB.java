package org.example;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import org.bson.Document;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Updates;

import java.io.FileInputStream;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class WorkingWithTheMongoDB {

    /**
     * Подключаемся к MongoDB.
     */
    public static MongoDatabase connectionToDB() {
        String CONNECTION_URL = "mongodb://localhost:27017";
        MongoDatabase dataBase = null;
        try {
            MongoClient mongoClient = MongoClients.create(CONNECTION_URL);
            dataBase = mongoClient.getDatabase("test");
            // System.out.println("Подключение к БД прошло успешно!");
        } catch (Exception e) {
            System.out.println("Ошибка подключения: " + e.getMessage());
        }
        return dataBase;
    }

    /**
     * Метод для загрузки параметров конфигурации из файла config.properties (или иного).
     */
    public static Properties loadConfiguration() {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            properties.load(fis);
            return properties;
        } catch (Exception e) {
            System.out.println("Ошибка чтения config.properties: " + e.getMessage());
            return null;
        }
    }

    /**
     * Создаем/обновляем индекс TTL по полю expireAt (если он не создан).
     * При достижении времени, указанного в expireAt, MongoDB автоматически удалит документ.
     */
    public static void createTTLIndex(MongoDatabase connectionDB) {
        try {
            MongoCollection<Document> collection = connectionDB.getCollection("urls");
            IndexOptions indexOptions = new IndexOptions().expireAfter(0L, TimeUnit.SECONDS);
            collection.createIndex(new Document("expireAt", 1), indexOptions);
        } catch (Exception e) {
            System.out.println("Ошибка создания TTL-индекса: " + e.getMessage());
        }
    }

    /**
     * Вставка информации о ссылке в коллекцию "urls" c учетом TTL и лимита переходов.
     *
     * @param ttlInSeconds  время жизни ссылки в секундах
     * @param trafficLimit  лимит переходов
     */
    public static void insertInfoLinkWithTTL(MongoDatabase connectionDB, String personalUUID, String longURL,
                                             String shortURL, long ttlInSeconds, int trafficLimit) {
        try {
            MongoCollection<Document> collection = connectionDB.getCollection("urls");

            Date currentDateInUTC = new Date(System.currentTimeMillis());
            Date expireAt = new Date(System.currentTimeMillis() + ttlInSeconds * 1000);

            Document document = new Document("uuid", personalUUID)
                    .append("createdAt", currentDateInUTC)
                    .append("expireAt", expireAt)
                    .append("longURL", longURL)
                    .append("shortURL", shortURL)
                    .append("trafficUsed", 0)
                    .append("trafficLimit", trafficLimit);

            collection.insertOne(document);

        } catch (Exception e) {
            System.out.println("Ошибка записи ссылки в БД: " + e.getMessage());
        }
    }

    /**
     * Получение информации по ссылке (UUID).
     * Показываем UUID, Long URL, Short URL, даты, лимиты.
     */
    public static void getInfoOfLink(String personalUUID, MongoDatabase dataBase) {
        try {
            MongoCollection<Document> collection = dataBase.getCollection("urls");
            Document query = new Document("uuid", personalUUID);
            Document resultSearch = collection.find(query).first();

            if (resultSearch == null) {
                System.out.println("Ссылка с таким UUID не найдена.");
                return;
            }

            System.out.println("Ваш персональный UUID: " + resultSearch.getString("uuid")
                    + "\nLong URL: " + resultSearch.getString("longURL")
                    + "\nShort URL: " + resultSearch.getString("shortURL")
                    + "\nДата создания: " + resultSearch.getDate("createdAt")
                    + "\nДата прекращения работы: " + resultSearch.getDate("expireAt")
                    + "\nИспользований / Лимит: " + resultSearch.getInteger("trafficUsed")
                    + " / " + resultSearch.getInteger("trafficLimit"));
        } catch (Exception e) {
            System.out.println("Ошибка получения данных по UUID -> " + e.getMessage());
        }
    }

    /**
     * Метод, эмулирующий "переход" по короткой ссылке (shortURL).
     * Проверяем, есть ли запись в БД, не истёк ли срок и не превышен ли лимит.
     * Если всё ок - увеличиваем счётчик (trafficUsed) и "переходим" (для консольного примера - просто выводим сообщение).
     */
    public static void goToShortLink(String shortUrl, MongoDatabase dataBase) {
        try {
            MongoCollection<Document> collection = dataBase.getCollection("urls");
            Document foundDoc = collection.find(new Document("shortURL", shortUrl)).first();

            if (foundDoc == null) {
                System.out.println("Данная короткая ссылка не зарегистрирована в системе.");
                return;
            }

            Date expireAt = foundDoc.getDate("expireAt");
            Date now = new Date();
            if (now.after(expireAt)) {
                System.out.println("Срок действия ссылки истёк. Доступ запрещён.");
                return;
            }

            int trafficUsed = foundDoc.getInteger("trafficUsed", 0);
            int trafficLimit = foundDoc.getInteger("trafficLimit", 0);
            if (trafficUsed >= trafficLimit) {
                System.out.println("Лимит переходов по данной ссылке достигнут. Доступ запрещён.");
                return;
            }

            collection.updateOne(Filters.eq("shortURL", shortUrl),
                    Updates.set("trafficUsed", trafficUsed + 1));

            String originalURL = foundDoc.getString("longURL");
            System.out.println("\nПереход на: " + originalURL);
            System.out.println("(Имитация открытия ссылки в браузере...)");

        } catch (Exception e) {
            System.out.println("Ошибка при переходе по короткой ссылке: " + e.getMessage());
        }
    }

    /**
     * Метод для изменения лимита переходов.
     * Доступно только создателю - проверка по UUID (у нас роль UUID = "владелец").
     * В реальности здесь нужно было бы разграничивать "ссылка" и "пользователь".
     * Но в данном упрощённом варианте предполагается, что UUID - это и есть признак владельца.
     */
    public static void updateTrafficLimit(String personalUUID, MongoDatabase dataBase, java.util.Scanner scanner) {
        try {
            MongoCollection<Document> collection = dataBase.getCollection("urls");
            Document foundDoc = collection.find(new Document("uuid", personalUUID)).first();

            if (foundDoc == null) {
                System.out.println("Ссылка с таким UUID не найдена. Изменение невозможно.");
                return;
            }

            System.out.print("Введите новый лимит переходов: ");
            String userLimitString = scanner.nextLine().trim();
            int newLimit;
            try {
                newLimit = Integer.parseInt(userLimitString);
            } catch (NumberFormatException e) {
                System.out.println("Некорректный ввод. Операция отменена.");
                return;
            }

            collection.updateOne(Filters.eq("uuid", personalUUID),
                    Updates.set("trafficLimit", newLimit));
            System.out.println("Лимит переходов успешно обновлён.");

        } catch (Exception e) {
            System.out.println("Ошибка при изменении лимита: " + e.getMessage());
        }
    }

    /**
     * Удаление ссылки. Доступно только создателю (UUID).
     */
    public static void deleteLink(String personalUUID, MongoDatabase dataBase) {
        try {
            MongoCollection<Document> collection = dataBase.getCollection("urls");
            Document foundDoc = collection.find(new Document("uuid", personalUUID)).first();

            if (foundDoc == null) {
                System.out.println("Ссылка с таким UUID не найдена. Удаление невозможно.");
                return;
            }

            collection.deleteOne(new Document("uuid", personalUUID));
            System.out.println("Ссылка (UUID: " + personalUUID + ") успешно удалена.");

        } catch (Exception e) {
            System.out.println("Ошибка при удалении ссылки: " + e.getMessage());
        }
    }
}

