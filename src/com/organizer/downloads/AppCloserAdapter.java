package com.organizer.downloads;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppCloserAdapter extends BaseAdapter {

    public interface OnSelectionChangedListener {
        void onSelectionChanged();
    }

    private final Context mContext;
    private final List<AppInfoItem> mList;
    private final LayoutInflater mInflater;
    private final PackageManager mPackageManager;
    private OnSelectionChangedListener mListener;
    private static final Map<String, Drawable> sIconCache = new ConcurrentHashMap<>();
    private static final ExecutorService sIconExecutor = Executors.newFixedThreadPool(4);
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public AppCloserAdapter(Context context, List<AppInfoItem> list) {
        this.mContext = context;
        this.mList = list;
        this.mInflater = LayoutInflater.from(context);
        this.mPackageManager = context.getPackageManager();
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.mListener = listener;
    }

    @Override
    public int getCount() {
        return mList.size();
    }

    @Override
    public Object getItem(int position) {
        return mList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_app_closer, parent, false);
            holder = new ViewHolder();
            holder.ivIcon = convertView.findViewById(R.id.iv_item_app_icon);
            holder.tvName = convertView.findViewById(R.id.tv_item_app_name);
            holder.tvPkg = convertView.findViewById(R.id.tv_item_package_name);
            holder.checkBox = convertView.findViewById(R.id.cb_item_select);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        AppInfoItem item = mList.get(position);
        holder.tvName.setText(item.appName);
        holder.tvPkg.setText(item.packageName + (item.isSystem ? " (System)" : ""));

        // Icon caching & async loading
        final String pkg = item.packageName;
        holder.ivIcon.setTag(pkg);
        if (item.icon != null) {
            holder.ivIcon.setImageDrawable(item.icon);
        } else if (sIconCache.containsKey(pkg)) {
            Drawable d = sIconCache.get(pkg);
            item.icon = d;
            holder.ivIcon.setImageDrawable(d);
        } else {
            holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            sIconExecutor.execute(() -> {
                try {
                    Drawable d = mPackageManager.getApplicationIcon(pkg);
                    if (d != null) {
                        sIconCache.put(pkg, d);
                        item.icon = d;
                        mMainHandler.post(() -> {
                            if (pkg.equals(holder.ivIcon.getTag())) {
                                holder.ivIcon.setImageDrawable(d);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            });
        }

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(item.isSelected);
        holder.checkBox.setOnCheckedChangeListener((btn, isChecked) -> {
            item.isSelected = isChecked;
            if (mListener != null) {
                mListener.onSelectionChanged();
            }
        });

        convertView.setOnClickListener(v -> {
            item.isSelected = !item.isSelected;
            holder.checkBox.setChecked(item.isSelected);
            if (mListener != null) {
                mListener.onSelectionChanged();
            }
        });

        return convertView;
    }

    private static class ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        TextView tvPkg;
        CheckBox checkBox;
    }
}
