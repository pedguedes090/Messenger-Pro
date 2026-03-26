package tn.amin.mpro2.settings.history;

import android.content.Context;
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
import tn.amin.mpro2.messaging.history.HistoryThreadInfo;

public class HistoryThreadAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    private final Context mContext;
    private final List<HistoryThreadInfo> mItems = new ArrayList<>();
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

    public HistoryThreadAdapter(Context context) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
    }

    public void setItems(List<HistoryThreadInfo> items) {
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
    public HistoryThreadInfo getItem(int position) {
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
            convertView = mInflater.inflate(R.layout.row_history_thread, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        HistoryThreadInfo item = getItem(position);
        String threadName = item.threadName;
        if (threadName == null || threadName.trim().isEmpty()) {
            threadName = "Thread " + item.threadKey;
        }

        holder.title.setText(threadName);
        String subtitle = item.messageCount + " msgs";
        if (item.lastTimestamp > 0) {
            subtitle = subtitle + " • " + mDateFormat.format(new Date(item.lastTimestamp));
        }
        holder.subtitle.setText(subtitle);

        return convertView;
    }

    private static class ViewHolder {
        final TextView title;
        final TextView subtitle;

        ViewHolder(View row) {
            title = row.findViewById(R.id.text_history_thread_name);
            subtitle = row.findViewById(R.id.text_history_thread_meta);
        }
    }
}
