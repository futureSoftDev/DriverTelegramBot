package uz.company.drivertesttelegrambot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.company.drivertesttelegrambot.constants.MessageConstants;
import uz.company.drivertesttelegrambot.dto.TelegramBotSessionDataDto;
import uz.company.drivertesttelegrambot.enums.TelegramBotSessionState;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramBotService extends TelegramLongPollingBot {

    @Value("${application.telegram-bot-config.token}")
    String botToken;

    @Value("${application.telegram-bot-config.username}")
    String botUsername;

    @Value("${application.telegram-bot-config.session_close_time_in_hours}")
    int sessionCloseTimeInHours;

    @Value("${application.telegram-bot-config.auth_check_limit_in_hour}")
    int authCheckLimitInHours;

    private final Map<Long, TelegramBotSessionDataDto> sessionMap = new ConcurrentHashMap<>();

    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();

        Long chatId = null;
        if (message != null) {
            chatId = update.getMessage().getChatId();
        } else {
            if (update.getCallbackQuery() != null && update.getCallbackQuery().getMessage() != null) {
                chatId = update.getCallbackQuery().getMessage().getChatId();
            }
        }
        if (message != null && message.hasText()) {
            String messageText = message.getText();
            if (MessageConstants.START_MESSAGE.equals(messageText)) {
                if (!sessionMap.containsKey(chatId)) {
                    askPhoneNumber(chatId);
                    TelegramBotSessionDataDto dataDto = TelegramBotSessionDataDto.builder()
                            .sessionState(TelegramBotSessionState.INITIAL)
                            .build();
                    sessionMap.put(chatId, dataDto);
                } else {
                    TelegramBotSessionDataDto dataDto = sessionMap.get(chatId);
                    if (TelegramBotSessionState.INITIAL.equals(dataDto.getSessionState())) {
                        askPhoneNumber(chatId);
                    } else if (TelegramBotSessionState.CONTACT_SHARED.equals(dataDto.getSessionState())) {
                        if (checkContactInWhiteList(dataDto.getPhoneNumber())) {
                            showInlineMenuForCheckDRB(chatId);
                            dataDto.setAuthTime(Instant.now());
                        } else {
                            handleContactNotAllowed(chatId, dataDto);
                        }
                    } else if (TelegramBotSessionState.CONTACT_NOT_ALLOWED.equals(dataDto.getSessionState())) {
                        handleContactNotAllowed(chatId, dataDto);
                    }
                }
            } else if (MessageConstants.CHECK_DRB.equals(messageText)) {
                handleCheckDrb(chatId);
            } else if (sessionMap.containsKey(chatId)) {
                TelegramBotSessionDataDto dataDto = sessionMap.get(chatId);
                if (TelegramBotSessionState.WAITING_DRB.equals(dataDto.getSessionState())) {
                    if (checkContactInWhiteList(dataDto.getPhoneNumber())) {
                        sendText(chatId, LocalDateTime.now().toString());
                        dataDto.setSessionState(TelegramBotSessionState.CONTACT_SHARED);
                    } else {
                        showMenu(chatId, List.of("Recheck contact"));
                    }
                } else {
                    showInlineMenuForCheckDRB(chatId);
                }
            } else {
                askPhoneNumber(chatId);
            }
        } else if (message != null && message.hasContact()) {
            handleContact(message.getContact(), chatId);
        } else if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            if (MessageConstants.CHECK_DRB.equals(data)) {
                handleCheckDrb(chatId);
            }
        }
    }

    private void handleCheckDrb(Long chatId) {
        TelegramBotSessionDataDto session = sessionMap.get(chatId);
        if (session == null || sessionExpired(session.getAuthTime())) {
            askPhoneNumber(chatId);
        } else {
            sendTextWithKeyboardRemove(chatId, " Iltimos avtomobil raqamini kiriting \uD83D\uDE97");
            session.setSessionState(TelegramBotSessionState.WAITING_DRB);
        }
    }

    private void askPhoneNumber(Long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(" \uD83D\uDCDE Please share your phone number to continue");

        KeyboardButton contactButton = new KeyboardButton("Kontaktni ulashish");
        contactButton.setRequestContact(true);

        KeyboardRow row = new KeyboardRow();
        row.add(contactButton);

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setKeyboard(List.of(row));
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(true);
        msg.setReplyMarkup(keyboard);
        send(msg);
    }

    private void showInlineMenu(Long chatId, Map<String, String> options) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(" \uD83D\uDD3D Tanlang");
        List<InlineKeyboardButton> rows = new ArrayList<>();
        options.forEach((callBackData, text) -> {
            InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
            inlineKeyboardButton.setText(text);
            inlineKeyboardButton.setCallbackData(callBackData);
            rows.add(inlineKeyboardButton);
        });
        List<List<InlineKeyboardButton>> keyboard = List.of(rows);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        msg.setReplyMarkup(markup);
        send(msg);
    }

    private void showInlineMenuForCheckDRB(Long chatId) {
        Map<String, String> options = new HashMap<>();
        options.put(MessageConstants.CHECK_DRB, "Davlat raqam belgisini tekshirish");
        this.showInlineMenu(chatId, options);
    }


    private void handleContact(Contact contact, Long chatId) {
        String phoneNumber = contact.getPhoneNumber();
        TelegramBotSessionDataDto sessionDataDto = sessionMap.getOrDefault(chatId, TelegramBotSessionDataDto.builder().build());
        sessionDataDto.setPhoneNumber(phoneNumber);
        if (checkContactInWhiteList(phoneNumber)) {
            showInlineMenuForCheckDRB(chatId);
            sessionDataDto.setSessionState(TelegramBotSessionState.CONTACT_SHARED);
            sessionDataDto.setAuthTime(Instant.now());
        } else {
            sendText(chatId, "You are not allowed to use this bot.");
            sessionDataDto.setSessionState(TelegramBotSessionState.CONTACT_NOT_ALLOWED);
            sessionDataDto.setAuthCheckCount(sessionDataDto.getAuthCheckCount() + 1);
        }
        sessionMap.put(chatId, sessionDataDto);
    }

    private void showMenu(Long chatId, List<String> options) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText("Choose an option:");
        List<KeyboardRow> rows = new ArrayList<>();
        for (String option : options) {
            KeyboardRow row = new KeyboardRow();
            row.add(option);
            rows.add(row);
        }


        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setKeyboard(rows);
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(true);

        msg.setReplyMarkup(keyboard);
        send(msg);
    }

    private void send(SendMessage message) {
        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendText(Long chatId, String text) {
        SendMessage msg = new SendMessage(chatId.toString(), text);
        send(msg);
    }

    private void sendTextWithKeyboardRemove(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        msg.setReplyMarkup(new ReplyKeyboardRemove(true));
        send(msg);
    }

    private boolean sessionExpired(Instant lastChecked) {
        return Duration.between(lastChecked == null ? Instant.now().plusMillis(1) : lastChecked, Instant.now()).toHours() >= sessionCloseTimeInHours;
    }

    private boolean checkContactInWhiteList(String phoneNumber) {
        if (StringUtils.hasText(phoneNumber)) {
            return phoneNumber.equals("+998909014459");
        }
        return false;
    }

    private void handleContactNotAllowed(Long chatId, TelegramBotSessionDataDto sessionDataDto) {
        if (!checkContactInWhiteList(sessionDataDto.getPhoneNumber())) {
            if (authCheckLimitInHours < sessionDataDto.getAuthCheckCount()) {
                sendText(chatId, "You are not allowed to use this bot. Check after 1 hour");
            } else {
                showMenu(chatId, List.of("Recheck contact"));
                sessionDataDto.setAuthCheckCount(sessionDataDto.getAuthCheckCount() + 1);
            }
        } else {
            showInlineMenuForCheckDRB(chatId);
        }
    }

    private void validateDrb() {
        //TODO regex pattern
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return this.botToken;
    }
}