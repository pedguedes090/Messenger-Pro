package tn.amin.mpro2.features.util.message.command;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;

import de.robv.android.xposed.XposedBridge;
import tn.amin.mpro2.R;
import tn.amin.mpro2.constants.ModuleInfo;
import tn.amin.mpro2.constants.StringConstants;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.features.util.message.command.api.ApiResult;
import tn.amin.mpro2.features.util.message.command.api.DuckDuckGoAPI;
import tn.amin.mpro2.features.util.message.command.api.FreeDictionaryAPI;
import tn.amin.mpro2.features.util.message.command.api.LatexAPI;
import tn.amin.mpro2.features.util.message.command.api.RedditAPI;
import tn.amin.mpro2.features.util.message.command.api.UrbanAPI;
import tn.amin.mpro2.features.util.message.command.api.WikipediaAPI;
import tn.amin.mpro2.features.util.message.command.provider.AIProviderInteracter;
import tn.amin.mpro2.file.FileHelper;
import tn.amin.mpro2.file.StorageConstants;
import tn.amin.mpro2.messaging.MessageSender;
import tn.amin.mpro2.messaging.OrcaMessageSender;
import tn.amin.mpro2.messaging.history.HistoryThreadInfo;
import tn.amin.mpro2.messaging.history.MessageHistoryEntry;
import tn.amin.mpro2.messaging.history.MessageHistoryStore;
import tn.amin.mpro2.orca.OrcaGateway;
import tn.amin.mpro2.orca.OrcaStickers;
import tn.amin.mpro2.orca.builder.AttachmentBuilder;
import tn.amin.mpro2.orca.datatype.MediaAttachment;
import tn.amin.mpro2.util.BitmapUtil;
import tn.amin.mpro2.util.StringUtil;

public class CommandsManager {
    private final OrcaGateway gateway;
    private static final CommandDispatcher<Object> mDispatcher = new CommandDispatcher<>();
    private static final ArrayList<CommandFields> mCommands = new ArrayList<>();

    private ParseResults<Object> mCachedParseResults;
    private ProgressDialog mProgressDialog = null;

    private boolean mJlatexMathInit = false;

    static {
        mCommands.add(new CommandFields("word", "stub"));
        mCommands.add(new CommandFields("reddit", "stub"));
        mCommands.add(new CommandFields("wikipedia", "stub"));
        mCommands.add(new CommandFields("like", "stub"));
        mCommands.add(new CommandFields("empty", "stub"));
    }

    public CommandsManager(OrcaGateway gateway) {
        this.gateway = gateway;

        mDispatcher.register(literal("word")
                .then(literal("pronounce").then(argument("word", greedyString()).executes(c -> comAPI("word pronounce", c))))
                .then(literal("define").then(argument("word", greedyString()).executes(c -> comAPI("word define", c))))
                .then(literal("urban").then(argument("word", greedyString()).executes(c -> comAPI("word urban", c)))));
        mDispatcher.register(literal("reddit")
                .then(argument("subreddit", string()).executes(c -> comAPI("reddit", c))
                        .then(argument("sort", word()).executes(c -> comAPI("reddit", c)))));
        mDispatcher.register(literal("wikipedia")
                .then(argument("language", word())
                        .then(argument("term", greedyString()).executes(c -> comAPI("wikipedia", c)))));
        mDispatcher.register(literal("search")
                .then(argument("term", greedyString()).executes(c -> comAPI("search", c))));
        mDispatcher.register(literal("like").executes(c -> comLike(1, c))
                .then(argument("size", integer(1, 3)).executes(c -> comLike(getInteger(c, "size"), c))));
        mDispatcher.register(literal("empty").executes(c -> comEmpty(1, 1, c))
                .then(argument("row", integer(1)).executes(c -> comEmpty(getInteger(c, "row"), 1, c))
                        .then(argument("column", integer(1)).executes(c -> comEmpty(getInteger(c, "row"), getInteger(c, "column"), c)))));
        mDispatcher.register(literal("ai")
                .then(argument("prompt", greedyString()).executes(c -> comAPI("ai", c))));
        mDispatcher.register(literal("latex")
                .then(argument("expression", greedyString()).executes(c -> comAPI("latex", c))));
        mDispatcher.register(literal("history").executes(c -> comHistory(20, c))
            .then(argument("limit", integer(1, 50)).executes(c -> comHistory(getInteger(c, "limit"), c))));
        mDispatcher.register(literal("threads").executes(c -> comThreads(20, c))
            .then(argument("limit", integer(1, 50)).executes(c -> comThreads(getInteger(c, "limit"), c))));
    }

    private void update(String message, CommandBundle source) {
        if (!message.startsWith("/")) return;
        message = message.substring(1).trim();

        mCachedParseResults = mDispatcher.parse(message, source);
    }

    public boolean execute(String message, MessageSender messageSender) {
        try {
            CommandBundle bundle = new CommandBundle(messageSender);

            update(message, bundle);
            if (mCachedParseResults.getReader().canRead()) {
                return false;
            }

            mDispatcher.execute(mCachedParseResults);
            return true;
        } catch (CommandSyntaxException e) {
            Logger.error(e);
            return false;
        }
    }

    private ApiResult comAI(String prompt) {
        if (!gateway.isPackageInstalled(ModuleInfo.PACKAGE_AI_PLUGIN)) {
            return new ApiResult.SendText(gateway.res.getString(R.string.need_install_aiplugin));
        }

        String completion = AIProviderInteracter.getCompletion(
                gateway.getContext(),
                gateway.pref.getAiConfigModel(),
                gateway.pref.getAiConfigProvider(),
                gateway.pref.getAiConfigAuthData(),
                prompt
        );
        if (completion == null || StringUtils.isBlank(completion)) completion = gateway.res.getString(R.string.unexpected_error);

        return new ApiResult.SendText(completion);
    }

    private ApiResult comReddit(String subreddit, String sort) {
        if (sort == null) sort = "";

        String postDescription = RedditAPI.fetchLatestPost(subreddit, sort);
        return new ApiResult.SendText(postDescription);
    }

    private ApiResult comWordDefinition(String word, String type)  {
        switch (type) {
            case "pronounce": {
                String result = FreeDictionaryAPI.fetchPronunciation(word);
                if (result.startsWith("http")) {
                    File pronunciation = FileHelper.downloadFromUrl(result);
                    if (pronunciation != null) {
                        return new ApiResult.SendMedia(new MediaAttachment(pronunciation));
                    }
                } else {
                    return new ApiResult.SendText(result);
                }
            }
            case "define": {
                String result = FreeDictionaryAPI.fetchDefinitions(word);
                if (result != null) {
                    return new ApiResult.SendText(result);
                }
            }
            case "urban": {
                String result = UrbanAPI.fetchDefinition(word);

                if (!result.equals("")) {
                    return new ApiResult.SendText(result);
                }

            }
        }
        XposedBridge.log(new UnknownError());
        return new ApiResult.SendText(gateway.res.getText(R.string.unexpected_error));
    }

    private ApiResult comWikipedia(String term, String language) {
        if (language == null || language.isEmpty()) language = "en";
        String article = WikipediaAPI.fetchArticle(term, language);
        return new ApiResult.SendText(article);
    }

    private ApiResult comSearch(String term) {
        String result = DuckDuckGoAPI.fetchSearchResult(term, "en-us");
        return new ApiResult.SendText(result);
    }

    private int comLike(int likeSize, CommandContext c) {
        MessageSender messageSender = ((CommandBundle)c.getSource()).messageSender;

        switch (likeSize) {
            case 1 -> messageSender.sendSticker(OrcaStickers.LIKE_SMALL);
            case 2 -> messageSender.sendSticker(OrcaStickers.LIKE_MEDIUM);
            case 3 -> messageSender.sendSticker(OrcaStickers.LIKE_BIG);
        }
        return 1;
    }

    private int comEmpty(int row, int column, CommandContext c) {
        MessageSender messageSender = ((CommandBundle) c.getSource()).messageSender;

        final String delim = StringConstants.EMPTY;
        String rowMessage = delim + StringUtil.multiply(" ", column);
        if (column > 1) rowMessage += delim;
        rowMessage += '\n';
        String message = StringUtil.multiply(rowMessage, row);;

        messageSender.sendMessage(message);

        return 1;
    }

    private ApiResult comLatex(String expression) {
        String url = LatexAPI.getLinkToLatexImage(expression);
        Bitmap bmp = BitmapUtil.getBitmapFromUrl(url);

        if (bmp == null)
            return new ApiResult.SendText(gateway.res.getText(R.string.unexpected_error));

        bmp = BitmapUtil.convertTransparentToWhiteBackground(bmp, 10);

        File image = FileHelper.createTempFile("jpg", StorageConstants.moduleInternalCache);
        BitmapUtil.saveBitmapAsJPEG(bmp, image);

        if (image == null)
            return new ApiResult.SendText(gateway.res.getText(R.string.unexpected_error));

        return new ApiResult.SendMedia(new MediaAttachment(image, "latex.jpg", AttachmentBuilder.FILETYPE_IMAGE));
    }

    private int comAPI(String api, CommandContext c) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            mProgressDialog = ProgressDialog.show(gateway.getActivity(), "Loading", "Querying response");
        });

        CommandBundle bundle = (CommandBundle) c.getSource();
        MessageSender messageSender = bundle.messageSender;

        new Thread(() -> {
            final ApiResult apiResult;
            switch (api) {
                case "reddit" -> {
                    String sort;
                    apiResult = comReddit(getString(c, "subreddit"), getStringOrNull(c, "sort"));
                }
                case "word define" -> {
                    apiResult = comWordDefinition(getString(c, "word"), "define");
                }
                case "word pronounce" ->
                        apiResult = comWordDefinition(getString(c, "word"), "pronounce");
                case "word urban" -> {
                    apiResult = comWordDefinition(getString(c, "word"), "urban");
                }
                case "wikipedia" ->
                        apiResult = comWikipedia(getString(c, "term"), getStringOrNull(c, "language"));
                case "search" -> apiResult = comSearch(getString(c, "term"));
                case "ai" -> apiResult = comAI(getString(c, "prompt"));
                case "latex" -> apiResult = comLatex(getString(c, "expression"));
                default -> {
                    XposedBridge.log(new UnknownError());
                    apiResult = new ApiResult.SendText(gateway.res.getText(R.string.unexpected_error));
                }
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                    mProgressDialog = null;
                }
                apiResult.revealResult(messageSender);
            });
        }).start();
        return 1;
    }

    private int comHistory(int limit, CommandContext c) {
        CommandBundle bundle = (CommandBundle) c.getSource();
        MessageSender messageSender = bundle.messageSender;

        Long threadKey = null;
        if (messageSender instanceof OrcaMessageSender) {
            threadKey = ((OrcaMessageSender) messageSender).getThreadKey();
        }

        if (threadKey == null && gateway.requireThreadKey(false)) {
            threadKey = gateway.currentThreadKey;
        }

        if (threadKey == null || threadKey <= 0) {
            messageSender.sendMessage(gateway.res.getString(R.string.threadkey_required));
            return 1;
        }

        rememberCurrentThreadName(threadKey);

        List<MessageHistoryEntry> entries = MessageHistoryStore.getRecentMessages(threadKey, limit);
        if (entries.isEmpty()) {
            messageSender.sendMessage("No chat history found for this conversation yet.");
            return 1;
        }

        String message = formatHistory(entries, limit, threadKey);
        messageSender.sendMessage(message);
        return 1;
    }

    private int comThreads(int limit, CommandContext c) {
        MessageSender messageSender = ((CommandBundle) c.getSource()).messageSender;

        if (gateway.requireThreadKey(false) && gateway.currentThreadKey != null) {
            rememberCurrentThreadName(gateway.currentThreadKey);
        }

        List<HistoryThreadInfo> threads = MessageHistoryStore.getRecentThreads(limit);
        if (threads.isEmpty()) {
            messageSender.sendMessage("No threads found in chat history yet.");
            return 1;
        }

        StringBuilder builder = new StringBuilder("Threads in history:");
        for (int i = 0; i < threads.size(); i++) {
            HistoryThreadInfo thread = threads.get(i);
            builder.append('\n')
                    .append(i + 1)
                    .append(". ")
                    .append(resolveThreadDisplayName(thread.threadName, thread.threadKey))
                    .append(" (")
                    .append(thread.messageCount)
                    .append(" msgs)");
        }

        messageSender.sendMessage(builder.toString());
        return 1;
    }

    private String formatHistory(List<MessageHistoryEntry> entries, int limit, long threadKey) {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        StringBuilder builder = new StringBuilder();
        String threadName = resolveThreadDisplayName(MessageHistoryStore.getThreadName(threadKey), threadKey);
        builder.append("Chat history - ")
                .append(threadName)
                .append(" (last ")
                .append(Math.min(limit, entries.size()))
                .append(")");

        for (MessageHistoryEntry entry : entries) {
            String sender = entry.isIncoming() ? simplifySender(entry.senderUserKey) : "You";
            String content = sanitizeHistoryText(entry.content);

            builder.append('\n')
                    .append('[').append(timeFormat.format(new Date(entry.timestamp))).append("] ")
                    .append(sender)
                    .append(": ")
                    .append(content);

            if (builder.length() > 3400) {
                builder.append("\n...");
                break;
            }
        }

        return builder.toString();
    }

    private void rememberCurrentThreadName(long threadKey) {
        if (threadKey <= 0) {
            return;
        }

        if (gateway.getActivity() == null) {
            return;
        }

        CharSequence title = gateway.getActivity().getTitle();
        if (title == null) {
            return;
        }

        MessageHistoryStore.updateThreadName(threadKey, title.toString());
    }

    private String resolveThreadDisplayName(String storedName, long threadKey) {
        if (storedName != null && !storedName.trim().isEmpty()) {
            return storedName;
        }

        if (gateway.currentThreadKey != null && gateway.currentThreadKey == threadKey
                && gateway.getActivity() != null && gateway.getActivity().getTitle() != null) {
            String liveName = gateway.getActivity().getTitle().toString().trim();
            if (!liveName.isEmpty()) {
                MessageHistoryStore.updateThreadName(threadKey, liveName);
                return liveName;
            }
        }

        return "Unknown thread";
    }

    private String sanitizeHistoryText(String text) {
        if (text == null || text.isEmpty()) {
            return "(empty)";
        }

        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() <= 120) {
            return oneLine;
        }
        return oneLine.substring(0, 117) + "...";
    }

    private String simplifySender(String senderUserKey) {
        if (senderUserKey == null || senderUserKey.isEmpty()) {
            return "Unknown";
        }

        if (senderUserKey.startsWith("fbid:")) {
            return senderUserKey.substring(5);
        }
        return senderUserKey;
    }


    private static String getStringOrNull(CommandContext c, String key) {
        String s;
        try {
            s = getString(c, key);
        } catch (IllegalArgumentException e) {
            s = null;
        }
        return s;
    }
}
