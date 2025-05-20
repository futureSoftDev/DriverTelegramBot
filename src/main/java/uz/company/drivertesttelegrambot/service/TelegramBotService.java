package uz.company.drivertesttelegrambot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.company.drivertesttelegrambot.constants.TelegramMessageConstants;
import uz.company.drivertesttelegrambot.dto.TelegramBotSessionDataDto;
import uz.company.drivertesttelegrambot.enums.TelegramBotSessionState;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramBotService extends TelegramLongPollingBot {

    @Value("${application.telegram-bot-config.token}")
    private String botToken;

    @Value("${application.telegram-bot-config.username}")
    private String botUsername;

    @Value("${application.telegram-bot-config.session_close_time_in_hours}")
    private int sessionCloseTimeInHours;

    private final Map<Long, TelegramBotSessionDataDto> sessionMap = new ConcurrentHashMap<>();

    @Override
    public void onUpdateReceived(Update update) {
        Long chatId = getChatId(update);
        if (chatId == null) return;

        if (update.hasMessage()) {
            Message message = update.getMessage();
            if (message.hasText()) {
                handleTextMessage(chatId, message.getText());
            } else if (message.hasContact()) {
                handleContact(message.getContact(), chatId, update);
            } else {
                askPhoneNumber(chatId);
            }
        } else if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery(), chatId);
        } else {
            askPhoneNumber(chatId);
        }
    }

    private void handleTextMessage(Long chatId, String text) {
        TelegramBotSessionDataDto session = sessionMap.get(chatId);

        switch (text) {
            case TelegramMessageConstants.START_MESSAGE -> handleStart(chatId, session);
            case TelegramMessageConstants.CHECK_DRB -> handleCheckDrb(chatId);
            default -> handleDefaultText(chatId, session);
        }
    }

    private void handleStart(Long chatId, TelegramBotSessionDataDto session) {
        if (session == null) {
            askPhoneNumber(chatId);
            sessionMap.put(chatId, TelegramBotSessionDataDto.builder()
                    .sessionState(TelegramBotSessionState.INITIAL)
                    .build());
            return;
        }

        switch (session.getSessionState()) {
            case INITIAL -> askPhoneNumber(chatId);
            case AUTHORIZED -> {
                if (checkContactInWhiteList(session.getPhoneNumber())) {
                    showInlineMenuForCheckDRB(chatId);
                    session.setAuthTime(Instant.now());
                } else {
                    handleContactNotAllowed(chatId, session);
                }
            }
            case UN_AUTHORIZED -> handleContactNotAllowed(chatId, session);
        }
    }

    private void handleDefaultText(Long chatId, TelegramBotSessionDataDto session) {
        if (session == null) {
            askPhoneNumber(chatId);
            return;
        }

        switch (session.getSessionState()) {
            case WAITING_DRB -> {
                if (checkContactInWhiteList(session.getPhoneNumber())) {
                    session.setAuthTime(Instant.now());
                    sendText(chatId, getDrbInfo());
                    handleCheckDrb(chatId);
                } else {
                    handleContactNotAllowed(chatId, session);
                }
            }
            case UN_AUTHORIZED -> {
                if (checkContactInWhiteList(session.getPhoneNumber())) {
                    showInlineMenuForCheckDRB(chatId);
                } else {
                    handleContactNotAllowed(chatId, session);
                }
            }
        }
    }

    private void handleCallback(CallbackQuery callbackQuery, Long chatId) {
        String data = callbackQuery.getData();
        if (TelegramMessageConstants.CHECK_DRB.equals(data)) {
            handleCheckDrb(chatId);
        }
    }

    private void handleCheckDrb(Long chatId) {
        TelegramBotSessionDataDto session = sessionMap.get(chatId);
        if (session == null || sessionExpired(session.getAuthTime())) {
            askPhoneNumber(chatId);
        } else {
            sendTextWithKeyboardRemove(chatId);
            session.setSessionState(TelegramBotSessionState.WAITING_DRB);
        }
    }

    private void handleContact(Contact contact, Long chatId, Update update) {
        if (!Objects.equals(contact.getUserId(), update.getMessage().getFrom().getId())) {
            sendText(chatId, TelegramMessageConstants.SHARE_OWN_CONTACT_WARNING_MESSAGE);
            askPhoneNumber(chatId);
            return;
        }

        String phoneNumber = contact.getPhoneNumber();
        boolean isInWhiteList = checkContactInWhiteList(phoneNumber);

        TelegramBotSessionDataDto session = sessionMap.getOrDefault(chatId, TelegramBotSessionDataDto.builder().build());
        session.setPhoneNumber(phoneNumber);

        if (isInWhiteList) {
            session.setSessionState(TelegramBotSessionState.AUTHORIZED);
            session.setAuthTime(Instant.now());
            showInlineMenuForCheckDRB(chatId);
        } else {
            handleContactNotAllowed(chatId, session);
        }

        sessionMap.put(chatId, session);
    }

    private void askPhoneNumber(Long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(TelegramMessageConstants.SHARE_CONTACT_HEADER_MESSAGE);

        KeyboardButton contactButton = new KeyboardButton(TelegramMessageConstants.SHARE_CONTACT_MESSAGE);
        contactButton.setRequestContact(true);

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup(List.of(new KeyboardRow(List.of(contactButton))));
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(true);

        msg.setReplyMarkup(keyboard);
        send(msg);
    }

    private void showInlineMenuForCheckDRB(Long chatId) {
        Map<String, String> options = Map.of(
                TelegramMessageConstants.CHECK_DRB, TelegramMessageConstants.CHECK_DRB_TEXt
        );
        showInlineMenu(chatId, options);
    }

    private void showInlineMenu(Long chatId, Map<String, String> options) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            InlineKeyboardButton button = new InlineKeyboardButton(entry.getValue());
            button.setCallbackData(entry.getKey());
            keyboard.add(List.of(button));
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(keyboard);
        SendMessage msg = new SendMessage(chatId.toString(), TelegramMessageConstants.SELECT_OPTION);
        msg.setReplyMarkup(markup);
        send(msg);
    }

    private void handleContactNotAllowed(Long chatId, TelegramBotSessionDataDto session) {
        showMenu(chatId, List.of(TelegramMessageConstants.CONTACT_STATE_RECHECK));
        session.setSessionState(TelegramBotSessionState.UN_AUTHORIZED);
    }

    private void showMenu(Long chatId, List<String> options) {
        List<KeyboardRow> rows = new ArrayList<>();
        for (String option : options) {
            KeyboardRow row = new KeyboardRow();
            row.add(option);
            rows.add(row);
        }

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup(rows);
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(true);

        SendMessage msg = new SendMessage(chatId.toString(), TelegramMessageConstants.USER_NOT_ALLOWED_TO_USE_THIS_BOT);
        msg.setReplyMarkup(keyboard);
        send(msg);
    }

    private boolean sessionExpired(Instant lastChecked) {
        return lastChecked == null || Duration.between(lastChecked, Instant.now()).toHours() >= sessionCloseTimeInHours;
    }

    private boolean checkContactInWhiteList(String phoneNumber) {
        return StringUtils.hasText(phoneNumber) && phoneNumber.equals("+998909014458");
    }

    private void send(SendMessage message) {
        try {
            execute(message);
        } catch (Exception e) {
            log.error("Telegram send error: {}", e.getMessage(), e);
        }
    }

    private void sendText(Long chatId, String text) {
        send(new SendMessage(chatId.toString(), text));
    }

    private void sendTextWithKeyboardRemove(Long chatId) {
        SendMessage msg = new SendMessage(chatId.toString(), TelegramMessageConstants.ASK_DRB);
        msg.setReplyMarkup(new ReplyKeyboardRemove(true));
        send(msg);
    }

    private Long getChatId(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        } else if (update.hasCallbackQuery()) {
            return Optional.ofNullable(update.getCallbackQuery().getMessage())
                    .map(MaybeInaccessibleMessage::getChatId)
                    .orElse(null);
        }
        return null;
    }

    private String getDrbInfo() {
        return LocalDateTime.now().toString();
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
}