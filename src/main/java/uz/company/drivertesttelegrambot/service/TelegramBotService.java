package uz.company.drivertesttelegrambot.service;

import io.micrometer.common.util.StringUtils;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.company.drivertesttelegrambot.constants.MessageConstants;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

import static lombok.AccessLevel.PRIVATE;

@Service
@FieldDefaults(level = PRIVATE)
@Slf4j
public class TelegramBotService extends TelegramLongPollingBot {

    @Value("${application.telegram-bot-config.token}")
    String botToken;

    @Value("${application.telegram-bot-config.username}")
    String botUsername;

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && !StringUtils.isEmpty(update.getMessage().getText())) {
            String messageText = update.getMessage().getText().toLowerCase();
            Long chatId = update.getMessage().getChatId();
            String responseMessage = null;
            switch (messageText) {
                case MessageConstants.START_MESSAGE -> responseMessage = "Xabar yuboring";
                case MessageConstants.WELCOME_MESSAGE -> {
                    StringBuilder responseMessageBuilder = new StringBuilder();
                    LocalDateTime now = LocalDateTime.now();

                    responseMessageBuilder.append("sana: ").append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))).append("\n");
                    responseMessageBuilder.append("vaqt: ").append(now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append("\n");
                    responseMessageBuilder.append("hash: ").append(Arrays.toString(DigestUtils.sha256(now.toString())));

                    responseMessage = responseMessageBuilder.toString();
                }
                default -> {
                    //TODO
                }
            }
            try {
                execute(sendMessageToChat(chatId, responseMessage));
            } catch (TelegramApiException e) {
                log.error("Error message: {}", e.getMessage());
            }
        }
    }

    public SendMessage sendMessageToChat(Long chatId, String message) {
        SendMessage responseMessage = new SendMessage();
        responseMessage.setChatId(chatId);
        responseMessage.setText(Optional.ofNullable(message).orElse(""));
        return responseMessage;
    }

    @Override
    public String getBotUsername() {
        return this.botUsername;
    }

    @Override
    public String getBotToken() {
        return this.botToken;
    }
}
