package tn.amin.mpro2.settings.history;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import tn.amin.mpro2.R;
import tn.amin.mpro2.messaging.history.HistoryThreadInfo;
import tn.amin.mpro2.messaging.history.MessageHistoryEntry;
import tn.amin.mpro2.messaging.history.MessageHistoryStore;

public final class HistoryDialogs {
    private HistoryDialogs() {
    }

    public static void showThreadsDialog(Context context) {
        if (context == null) {
            return;
        }

        List<HistoryThreadInfo> threads = MessageHistoryStore.getRecentThreads(200);
        if (threads.isEmpty()) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.pref_chat_history)
                    .setMessage(R.string.chat_history_empty_threads)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        ListView listView = new ListView(context);
        HistoryThreadAdapter adapter = new HistoryThreadAdapter(context);
        adapter.setItems(threads);
        listView.setAdapter(adapter);

        final androidx.appcompat.app.AlertDialog[] dialogRef = new androidx.appcompat.app.AlertDialog[1];
        listView.setOnItemClickListener((parent, view, position, id) -> {
            HistoryThreadInfo info = adapter.getItem(position);
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            showMessagesDialog(context, info);
        });

        dialogRef[0] = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_chat_history)
                .setView(listView)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialogRef[0].show();
    }

    private static void showMessagesDialog(Context context, HistoryThreadInfo info) {
        String threadName = info.threadName;
        if (threadName == null || threadName.trim().isEmpty()) {
            threadName = "Thread " + info.threadKey;
        }

        List<MessageHistoryEntry> entries = MessageHistoryStore.getRecentMessages(info.threadKey, 300);
        if (entries.isEmpty()) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(threadName)
                    .setMessage(R.string.chat_history_empty_messages)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.pref_chat_history, (d, w) -> showThreadsDialog(context))
                    .show();
            return;
        }

        ListView listView = new ListView(context);
        HistoryMessagesAdapter adapter = new HistoryMessagesAdapter(LayoutInflater.from(context));
        adapter.setItems(entries);
        listView.setAdapter(adapter);

        new MaterialAlertDialogBuilder(context)
                .setTitle(threadName)
                .setView(listView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.pref_chat_history, (d, w) -> showThreadsDialog(context))
                .show();
    }
}
