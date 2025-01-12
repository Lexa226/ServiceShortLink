package org.example;

import com.mongodb.client.MongoDatabase;

import java.util.Properties;
import java.util.Scanner;

import static org.example.PersonalLink.logicWorkLink;
import static org.example.WorkingWithTheMongoDB.connectionToDB;
import static org.example.WorkingWithTheMongoDB.loadConfiguration;

/**
 * Класс App - точка входа в приложение.
 * Включает логику работы с главным меню, подключением к БД,
 * а также чтение конфигурационного файла.
 */
public class App {

    public static void main(String[] args) {
        MongoDatabase connectionDB = connectionToDB();

        Properties config = loadConfiguration();
        if (config == null) {
            System.out.println("Не удалось загрузить конфигурационный файл. Программа завершается.");
            return;
        }

        System.out.println("<-Вы запустили работу сервиса коротких ссылок->");
        Scanner scanner = new Scanner(System.in);

        int choiceCommand;
        while (true) {
            System.out.print("\n1 - [завершить работу программы]"
                    + "\n2 - [перейти к работе с URL]"
                    + "\n3 - [перезапуск программы]");
            System.out.print("\n\nВыберите действие: ");

            try {
                choiceCommand = Integer.parseInt(scanner.nextLine());
                switch (choiceCommand) {
                    case 1:
                        System.out.println("\nВы завершили работу программы..");
                        scanner.close();
                        return;

                    case 2:
                        logicWorkLink(connectionDB, config);
                        break;

                    case 3:
                        System.out.println("Программа перезапущена.");
                        App.main(args);
                        break;

                    default:
                        System.out.println("Такого номера команды не существует. Попробуйте снова.");
                }
            } catch (Exception e) {
                System.out.println("Произошла ошибка ввода. Убедитесь, что вы вводите номер команды.");
            }
        }
    }
}

