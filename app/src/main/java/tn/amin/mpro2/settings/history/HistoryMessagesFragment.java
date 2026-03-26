package tn.amin.mpro2.settings.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

import tn.amin.mpro2.R;
import tn.amin.mpro2.messaging.history.MessageHistoryEntry;
import tn.amin.mpro2.messaging.history.MessageHistoryStore;
import tn.amin.mpro2.settings.SettingsActivity;

public class HistoryMessagesFragment extends Fragment {
    private final long mThreadKey;
    private final String mThreadName;

    private HistoryMessagesAdapter mAdapter;

    public HistoryMessagesFragment(long threadKey, String threadName) {
        mThreadKey = threadKey;
        mThreadName = threadName;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ViewGroup content = (ViewGroup) inflater.inflate(R.layout.fragment_history_messages, container, false);

        ListView listView = content.findViewById(R.id.listview_history_messages);
        TextView emptyView = content.findViewById(R.id.text_empty_history_messages);
        listView.setEmptyView(emptyView);

        mAdapter = new HistoryMessagesAdapter(inflater);
        listView.setAdapter(mAdapter);

        reload();
        return content;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof SettingsActivity) {
            SettingsActivity activity = (SettingsActivity) getActivity();
            activity.updateToolbarText(
                    mThreadName == null || mThreadName.trim().isEmpty()
                            ? getString(R.string.chat_history_thread_unknown)
                            : mThreadName,
                    getString(R.string.chat_history_messages_subtitle)
            );
            activity.setApplyButtonVisible(false);
        }
        reload();
    }

    @Override
    public void onPause() {
        if (getActivity() instanceof SettingsActivity) {
            ((SettingsActivity) getActivity()).setApplyButtonVisible(true);
        }
        super.onPause();
    }

    private void reload() {
        if (mAdapter == null) {
            return;
        }

        List<MessageHistoryEntry> entries = MessageHistoryStore.getRecentMessages(mThreadKey, 300);
        mAdapter.setItems(entries);
    }
}
