package com.javarush.telegram;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Главный класс Telegram-бота MatchMentor.
 * Поддерживает режимы: GPT, Свидание, Переписка, Профиль и Первое сообщение (Opener).
 * Управляет диалогами и последовательными вопросами пользователя.
 */
public class TinderBoltApp extends MultiSessionTelegramBot {


    public static final String TELEGRAM_BOT_NAME = "MatchMentor";
    public static final String TELEGRAM_BOT_TOKEN = "8497714605:AAEimmuS1qzL-JqO52sfZG4LLrp_LSYt";
    public static final String OPEN_AI_TOKEN =
            "javcgknGzsQ2Hpv/Ox5/mEBt7jGZ9odEzk0d18IOaiI7kZO0GoWcatc1JMWuW+cZMw7KNEcSPPfJGOtkB5xmhKKVSHR7Oa/" +
                    "004F9C2eDQw2QnGq5nHX9QtYk4Ge9k+VPqgm+21HRqWwHfGpvNNoj5ZtytYrKXB3jv6MxFNeVNSkI6UbY1JO0hn1U7" +
                    "vIG45MSb0UpjfFsf/nG/M1IpLb2b5OlGC0UgMzeSRSpup+dqCs8wWidM=";


    private final ChatGPTService chatGPTService = new ChatGPTService(OPEN_AI_TOKEN);

    /**
     * Текущий режим диалога
     */
    private DialogMode currentMode = DialogMode.MAIN;

    /**
     * Имя выбранной девушки для режима DATE
     */
    private String datePersonName;

    /**
     * История сообщений для режима DATE
     */
    private final List<String> dateDialogHistory = new ArrayList<>();

    /**
     * История сообщений для режима MESSAGE
     */
    private final List<String> messageDialogHistory = new ArrayList<>();

    /**
     * Временный объект для заполнения профиля пользователя
     */
    private UserInfo tempUserInfo;

    /**
     * Шаг текущего заполнения профиля
     */
    private int profileStep;

    /**
     * Для режима OPENER: ответы на последовательные вопросы
     */
    private final List<String> tempOpenerInfo = new ArrayList<>();

    /**
     * Шаг текущего заполнения OPENER
     */
    private int openerStep = 0;

    /**
     * Текст запроса к ChatGPT для OPENER
     */
    private final String openerPrompt = """
            Помоги мужчине написать первое сообщение девушке.
            Оно должно быть не слишком длинным, ярким, интересным и интригующим. Добавь смайликов.
            Напиши сразу готовый вариант сообщения, который можно отправить - без шаблонов и подписей.
            В следующем сообщении будет немного информации о самой девушке (не нужно прямо ссылаться на эту информацию, 
            просто используй ее, чтобы сделать сообщение лучше).
            """;

    /**
     * Вопросы для режима OPENER
     */
    private final String[] openerQuestions = {
            "Как зовут девушку? или /skip",
            "Сколько ей лет? или /skip",
            "Чем она увлекается? или /skip",
            "Что её отличает или интересного в ней? или /skip",
            "Любые дополнительные детали (характер, стиль, хобби)? или /skip"
    };

    /**
     * Вопросы для режима PROFILE
     */
    private final String[] profileQuestions = {
            "Введите своё имя или /skip",
            "Ваш пол? (М/Ж/Другое) или /skip",
            "Ваш возраст? или /skip",
            "Город проживания? или /skip",
            "Ваша профессия? или /skip",
            "Ваши хобби? или /skip",
            "Оцените свою привлекательность по 10-балльной шкале? или /skip",
            "Ваш доход / финансовое положение? или /skip",
            "Что вас раздражает в людях? или /skip",
            "Ваши цели знакомства? или /skip"
    };

    /**
     * Класс для анимации печати сообщений.
     */
    private static class TypingAnimation {
        /**
         * Флаг работы анимации
         */
        volatile boolean running = true;
    }

    /**
     * Конструктор бота.
     */
    public TinderBoltApp() {
        super(TELEGRAM_BOT_NAME, TELEGRAM_BOT_TOKEN);
    }

    /**
     * Основной метод обработки всех обновлений Telegram.
     * Обрабатывает команды, callback'и и сообщения в текущем режиме.
     *
     * @param update Обновление из Telegram
     */
    @Override
    public void onUpdateEventReceived(Update update) {
        String message = getMessageText();
        String callback = getCallbackQueryButtonKey();

        // Обработка команд
        if (message != null && !message.isBlank()) {
            switch (message) {
                case "/start" -> {
                    handleStart();
                    return;
                }
                case "/gpt" -> {
                    switchToGptMode();
                    return;
                }
                case "/date" -> {
                    switchToDateMode();
                    return;
                }
                case "/message" -> {
                    switchToMessageMode();
                    return;
                }
                case "/profile" -> {
                    switchToProfileMode();
                    return;
                }
                case "/opener" -> {
                    switchToOpenerMode();
                    return;
                }
            }
        }

        // Обработка callback кнопок
        if (callback != null) {
            switch (callback) {
                case "gpt" -> switchToGptMode();
                case "date" -> switchToDateMode();
                case "message" -> switchToMessageMode();
                case "profile" -> switchToProfileMode();
                case "opener" -> switchToOpenerMode();
            }

            if (currentMode == DialogMode.DATE && callback.startsWith("date_")) {
                handleDateCallback(callback);
                return;
            }

            if (currentMode == DialogMode.MESSAGE && callback.startsWith("message_")) {
                handleMessageCallback(callback);
                return;
            }
        }

        // Обработка сообщений по текущему режиму
        if (message != null && !message.isBlank()) {
            switch (currentMode) {
                case GPT -> handleGptMessage(message);
                case DATE -> handleDateMessage(message);
                case MESSAGE -> handleMessageMode(message);
                case PROFILE -> handleProfileMessage(message);
                case OPENER -> handleOpenerMessage(message);
                default -> sendTextMessage("Нажмите /start");
            }
        }
    }

    /* ===================== START ===================== */

    /**
     * Отправляет приветственное сообщение и кнопки выбора режима.
     */
    private void handleStart() {
        currentMode = DialogMode.MAIN;
        dateDialogHistory.clear();
        messageDialogHistory.clear();

        sendPhotoMessage("main");
        sendTextMessage(loadMessage("main"));

        sendTextButtonsMessage(
                "Выберите режим 👇",
                "🔥 Свидание", "date",
                "🧠 ChatGPT", "gpt",
                "💌 Переписка", "message",
                "📝 Профиль", "profile",
                "💬 Первое сообщение", "opener"
        );
    }

    /* ===================== GPT ===================== */

    /**
     * Переключает бота в режим GPT.
     */
    private void switchToGptMode() {
        currentMode = DialogMode.GPT;
        sendPhotoMessage("gpt");
        sendTextMessage(loadMessage("gpt"));
    }

    /**
     * Обрабатывает сообщение пользователя в режиме GPT.
     *
     * @param message Сообщение пользователя
     */
    private void handleGptMessage(String message) {
        TypingAnimation anim = new TypingAnimation();
        Message msg = startTypingAnimation("🤔 Думаю", 500, anim);

        new Thread(() -> {
            String answer = chatGPTService.sendMessage(loadPrompt("gpt"), message);
            stopTypingAnimation(anim);
            updateTextMessage(msg, answer);
        }).start();
    }

    /* ===================== MESSAGE ===================== */

    /**
     * Переключает бота в режим MESSAGE.
     */
    private void switchToMessageMode() {
        currentMode = DialogMode.MESSAGE;
        messageDialogHistory.clear();

        sendPhotoMessage("message");
        sendTextButtonsMessage(
                "Пришлите переписку 👇",
                "Следующее сообщение", "message_next",
                "Пригласить на свидание", "message_date"
        );
    }

    /**
     * Обрабатывает сообщение пользователя в режиме MESSAGE.
     *
     * @param message Сообщение пользователя
     */
    private void handleMessageMode(String message) {
        messageDialogHistory.add("Пользователь: " + message);
        sendTextMessage("Сообщение сохранено ✅");
    }

    /**
     * Обрабатывает callback для режима MESSAGE.
     *
     * @param callback Ключ callback кнопки
     */
    private void handleMessageCallback(String callback) {
        if ("message_next".equals(callback)) {
            sendTextMessage("Пришлите следующее сообщение");
        }
        if ("message_date".equals(callback)) {
            String prompt = """
                    Ты опытный и уверенный мужчина.
                    Твоя задача — пригласить девушку на свидание.
                    Сообщение должно быть:
                    — коротким
                    — живым
                    — интересным
                    — интригующим
                    — с эмодзи
                    Используй переписку ниже:
                    %s
                    """.formatted(String.join("\n", messageDialogHistory));

            TypingAnimation anim = new TypingAnimation();
            Message msg = startTypingAnimation("✍️ Формулирую приглашение", 500, anim);

            new Thread(() -> {
                String answer = chatGPTService.sendMessage(prompt, "");
                stopTypingAnimation(anim);
                updateTextMessage(msg, answer);
            }).start();
        }
    }

    /* ===================== DATE ===================== */

    /**
     * Переключает бота в режим DATE.
     */
    private void switchToDateMode() {
        currentMode = DialogMode.DATE;
        dateDialogHistory.clear();

        sendPhotoMessage("date");
        sendTextButtonsMessage(
                "Выберите девушку 👇",
                "Ариана Гранде", "date_grande",
                "Марго Робби", "date_robbie",
                "Зендея", "date_zendaya",
                "Эмма Уотсон", "date_wotson"
        );
    }

    /**
     * Обрабатывает callback выбора девушки в режиме DATE.
     *
     * @param callback Callback кнопки
     */
    private void handleDateCallback(String callback) {
        dateDialogHistory.clear();

        switch (callback) {
            case "date_grande" -> datePersonName = "Ариана Гранде";
            case "date_robbie" -> datePersonName = "Марго Робби";
            case "date_zendaya" -> datePersonName = "Зендея";
            case "date_wotson" -> datePersonName = "Эмма Уотсон";
        }

        sendPhotoMessage(callback);
        sendTextMessage("💬 Вы общаетесь с " + datePersonName);
    }

    /**
     * Обрабатывает сообщение пользователя в режиме DATE.
     *
     * @param message Сообщение пользователя
     */
    private void handleDateMessage(String message) {
        String systemPrompt = """
                Ты %s.
                Ты реальная девушка, а не ИИ.
                Ты общаешься в чате знакомств.
                Ты отвечаешь от первого лица.
                Ты эмоциональная, живая, иногда флиртуешь.
                Пиши коротко, естественно, как в мессенджере.
                Иногда используй эмодзи 😉
                Не говори, что ты бот или нейросеть.
                """.formatted(datePersonName);

        dateDialogHistory.add("Пользователь: " + message);

        TypingAnimation anim = new TypingAnimation();
        Message msg = startTypingAnimation("💬 Девушка печатает", 600, anim);

        new Thread(() -> {
            String answer = chatGPTService.sendMessage(systemPrompt, String.join("\n", dateDialogHistory));
            stopTypingAnimation(anim);
            dateDialogHistory.add(datePersonName + ": " + answer);
            updateTextMessage(msg, answer);
        }).start();
    }

    /* ===================== PROFILE ===================== */

    /**
     * Переключает бота в режим PROFILE.
     */
    private void switchToProfileMode() {
        currentMode = DialogMode.PROFILE;
        tempUserInfo = new UserInfo();
        profileStep = 0;

        sendPhotoMessage("profile");
        sendTextMessage("Давайте создадим ваш Tinder-профиль 📝");
        sendTextMessage(profileQuestions[0]);
    }

    /**
     * Обрабатывает сообщение пользователя в режиме PROFILE.
     *
     * @param message Сообщение пользователя
     */
    private void handleProfileMessage(String message) {
        if ("/skip".equalsIgnoreCase(message)) message = "";

        switch (profileStep) {
            case 0 -> tempUserInfo.name = message;
            case 1 -> tempUserInfo.sex = message;
            case 2 -> tempUserInfo.age = message;
            case 3 -> tempUserInfo.city = message;
            case 4 -> tempUserInfo.occupation = message;
            case 5 -> tempUserInfo.hobby = message;
            case 6 -> tempUserInfo.handsome = message;
            case 7 -> tempUserInfo.wealth = message;
            case 8 -> tempUserInfo.annoys = message;
            case 9 -> {
                tempUserInfo.goals = message;
                generateProfile();
                return;
            }
        }

        profileStep++;
        sendTextMessage(profileQuestions[profileStep]);
    }

    /**
     * Формирует профиль пользователя через ChatGPT.
     */
    private void generateProfile() {
        String systemPrompt = """
                Ты опытный копирайтер и Tinder-эксперт.
                Твоя задача — создать привлекательный профиль для Tinder на основе предоставленной информации.
                Пиши живо, уверенно, с юмором, используя эмодзи умеренно.
                """;

        TypingAnimation anim = new TypingAnimation();
        Message msg = startTypingAnimation("✍️ Формирую профиль", 600, anim);

        new Thread(() -> {
            String answer = chatGPTService.sendMessage(systemPrompt, tempUserInfo.toString());
            stopTypingAnimation(anim);
            updateTextMessage(msg, answer);
        }).start();
    }

    /* ===================== OPENER ===================== */

    /**
     * Переключает бота в режим OPENER.
     * Запускает последовательные вопросы для составления первого сообщения.
     */
    private void switchToOpenerMode() {
        currentMode = DialogMode.OPENER;
        tempOpenerInfo.clear();
        openerStep = 0;

        sendPhotoMessage("opener");
        sendTextMessage("Давайте составим первое сообщение. Ответьте на вопросы 👇");
        sendTextMessage(openerQuestions[openerStep]);
    }

    /**
     * Обрабатывает ответы пользователя в режиме OPENER.
     *
     * @param message Сообщение пользователя
     */
    private void handleOpenerMessage(String message) {
        if (currentMode != DialogMode.OPENER) return;

        if ("/skip".equalsIgnoreCase(message)) message = "";

        tempOpenerInfo.add(message);
        openerStep++;

        if (openerStep < openerQuestions.length) {
            sendTextMessage(openerQuestions[openerStep]);
        } else {
            String collectedInfo = String.join("\n", tempOpenerInfo);

            TypingAnimation anim = new TypingAnimation();
            Message msg = startTypingAnimation("✍️ Составляю первое сообщение", 500, anim);

            new Thread(() -> {
                String answer = chatGPTService.sendMessage(openerPrompt, collectedInfo);
                stopTypingAnimation(anim);
                updateTextMessage(msg, answer);
            }).start();
        }
    }

    /* ===================== АНИМАЦИЯ ===================== */

    /**
     * Запускает анимацию "печатает".
     *
     * @param baseText Базовый текст анимации
     * @param delayMs  Задержка между обновлениями
     * @param anim     Объект анимации
     * @return Сообщение с анимацией
     */
    private Message startTypingAnimation(String baseText, long delayMs, TypingAnimation anim) {
        Message msg = sendTextMessage(baseText);
        new Thread(() -> {
            int i = 0;
            try {
                while (anim.running) {
                    safeUpdateTextMessage(msg, baseText + ".".repeat(i % 4));
                    i++;
                    Thread.sleep(delayMs);
                }
            } catch (InterruptedException ignored) {
            }
        }).start();
        return msg;
    }

    /**
     * Обновляет текст сообщения безопасно (если текст изменился).
     *
     * @param msg     Сообщение для обновления
     * @param newText Новый текст
     */
    private void safeUpdateTextMessage(Message msg, String newText) {
        try {
            if (!msg.getText().equals(newText)) updateTextMessage(msg, newText);
        } catch (Exception ignored) {
        }
    }

    /**
     * Останавливает анимацию.
     *
     * @param anim Объект анимации
     */
    private void stopTypingAnimation(TypingAnimation anim) {
        anim.running = false;
    }

    /* ===================== MAIN ===================== */

    /**
     * Запуск бота.
     *
     * @param args Аргументы командной строки
     * @throws TelegramApiException В случае ошибки API
     */
    public static void main(String[] args) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(new TinderBoltApp());
    }
}
