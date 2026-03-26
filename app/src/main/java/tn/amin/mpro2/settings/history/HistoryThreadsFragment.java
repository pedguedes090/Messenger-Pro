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
import tn.amin.mpro2.messaging.history.HistoryThreadInfo;
import tn.amin.mpro2.messaging.history.MessageHistoryStore;
import tn.amin.mpro2.settings.SettingsActivity;

public class HistoryThreadsFragment extends Fragment {
    private HistoryThreadAdapter mAdapter;

    public HistoryThreadsFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ViewGroup content = (ViewGroup) inflater.inflate(R.layout.fragment_history_threads, container, false);

        ListView listView = content.findViewById(R.id.listview_history_threads);
        TextView emptyView = content.findViewById(R.id.text_empty_history_threads);
        listView.setEmptyView(emptyView);

        mAdapter = new HistoryThreadAdapter(requireContext());
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> openThread(mAdapter.getItem(position)));

        reload();
        return content;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getActivity() instanceof SettingsActivity) {
            SettingsActivity activity = (SettingsActivity) getActivity();
            activity.updateToolbarFor(tn.amin.mpro2.settings.SettingsType.CHAT_HISTORY);
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

        List<HistoryThreadInfo> threads = MessageHistoryStore.getRecentThreads(200);
        mAdapter.setItems(threads);
    }

    private void openThread(HistoryThreadInfo info) {
        String displayName = info.threadName;
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = getString(R.string.chat_history_thread_unknown);
        }

        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new HistoryMessagesFragment(info.threadKey, displayName))
                .addToBackStack("fragChatHistoryDetail")
                .commit();
    }
}
