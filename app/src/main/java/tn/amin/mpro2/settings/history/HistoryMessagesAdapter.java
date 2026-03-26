package tn.amin.mpro2.settings.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import tn.amin.mpro2.R;
import tn.amin.mpro2.messaging.history.MessageHistoryEntry;

public class HistoryMessagesAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    private final List<MessageHistoryEntry> mItems = new ArrayList<>();
    private final SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public HistoryMessagesAdapter(LayoutInflater inflater) {
        mInflater = inflater;
    }

    public void setItems(List<MessageHistoryEntry> items) {
        mItems.clear();
        if (items != null) {
            mItems.addAll(items);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public MessageHistoryEntry getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.row_history_message, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        MessageHistoryEntry item = getItem(position);
        String sender = item.isIncoming() ? simplifySender(item.senderUserKey) : "You";
        String header = "[" + mTimeFormat.format(new Date(item.timestamp)) + "] " + sender;
        holder.header.setText(header);
        holder.content.setText(item.content == null || item.content.isEmpty() ? "(empty)" : item.content);

        return convertView;
    }

    private String simplifySender(String userKey) {
        if (userKey == null || userKey.isEmpty()) {
            return "Unknown";
        }
        if (userKey.startsWith("fbid:")) {
            return userKey.substring(5);
        }
        return userKey;
    }

    private static class ViewHolder {
        final TextView header;
        final TextView content;

        ViewHolder(View row) {
            header = row.findViewById(R.id.text_history_message_header);
            content = row.findViewById(R.id.text_history_message_content);
        }
    }
}
