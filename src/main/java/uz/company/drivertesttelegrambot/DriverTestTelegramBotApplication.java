package uz.company.drivertesttelegrambot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import uz.company.drivertesttelegrambot.service.TelegramBotService;

@SpringBootApplication
@Slf4j
public class DriverTestTelegramBotApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext applicationContext=SpringApplication.run(DriverTestTelegramBotApplication.class, args);
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(applicationContext.getBean(TelegramBotService.class));
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }
}
