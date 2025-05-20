package uz.company.drivertesttelegrambot.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import uz.company.drivertesttelegrambot.enums.TelegramBotSessionState;

import java.time.Instant;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class TelegramBotSessionDataDto {

    String phoneNumber;

    Instant authTime;

    TelegramBotSessionState sessionState;
}