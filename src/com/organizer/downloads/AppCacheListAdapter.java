package com.organizer.downloads;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class AppCacheListAdapter extends BaseAdapter {

    public interface SelectionChangeListener {
        void onSelectionChanged(long totalSelectedBytes, int selectedCount);
    }

    private final Context mContext;
    private final List<AppCacheItem> mAllItems;
    private final List<AppCacheItem> mDisplayItems = new ArrayList<>();
    private final LayoutInflater mInflater;
    private SelectionChangeListener mListener;

    private int mCurrentTabMode = 0; // 0 = All, 1 = User, 2 = System
    private String mSearchQuery = "";

    public AppCacheListAdapter(Context context, List<AppCacheItem> items, SelectionChangeListener listener) {
        this.mContext = context;
        this.mAllItems = items;
        this.mListener = listener;
        this.mInflater = LayoutInflater.from(context);
        applyFilters();
    }

    public void setTabFilter(int tabMode) {
        mCurrentTabMode = tabMode;
        applyFilters();
    }

    public void setSearchQuery(String query) {
        mSearchQuery = query != null ? query.trim().toLowerCase() : "";
        applyFilters();
    }

    private void applyFilters() {
        mDisplayItems.clear();
        for (AppCacheItem item : mAllItems) {
            // Check Tab filter
            if (mCurrentTabMode == 1 && item.isSystemApp) {
                continue; // User apps only
            } else if (mCurrentTabMode == 2 && !item.isSystemApp) {
                continue; // System apps only
            }

            // Check Search query
            if (!mSearchQuery.isEmpty()) {
                boolean matchesName = item.appName != null && item.appName.toLowerCase().contains(mSearchQuery);
                boolean matchesPkg = item.packageName != null && item.packageName.toLowerCase().contains(mSearchQuery);
                if (!matchesName && !matchesPkg) {
                    continue;
                }
            }

            mDisplayItems.add(item);
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    @Override
    public int getCount() {
        return mDisplayItems.size();
    }

    @Override
    public AppCacheItem getItem(int position) {
        return mDisplayItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_cache_app, parent, false);
            holder = new ViewHolder();
            holder.ivIcon = convertView.findViewById(R.id.iv_cache_app_icon);
            holder.cbCheck = convertView.findViewById(R.id.cb_cache_app);
            holder.tvName = convertView.findViewById(R.id.tv_cache_app_name);
            holder.tvPkg = convertView.findViewById(R.id.tv_cache_pkg_name);
            holder.tvSize = convertView.findViewById(R.id.tv_cache_size);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        AppCacheItem item = mDisplayItems.get(position);
        if (item.icon != null) {
            holder.ivIcon.setImageDrawable(item.icon);
        } else {
            holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        holder.tvName.setText(item.appName);
        holder.tvPkg.setText(item.packageName);
        holder.tvSize.setText(CacheCleaner.formatSize(item.cacheBytes));
        holder.cbCheck.setChecked(item.isSelected);

        convertView.setOnClickListener(v -> {
            item.isSelected = !item.isSelected;
            holder.cbCheck.setChecked(item.isSelected);
            notifySelectionChanged();
        });

        return convertView;
    }

    public void selectAll() {
        for (AppCacheItem item : mDisplayItems) {
            item.isSelected = true;
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void deselectAll() {
        for (AppCacheItem item : mDisplayItems) {
            item.isSelected = false;
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public boolean areAllSelected() {
        if (mDisplayItems.isEmpty()) return false;
        for (AppCacheItem item : mDisplayItems) {
            if (!item.isSelected) return false;
        }
        return true;
    }

    public List<String> getSelectedPackages() {
        List<String> pkgs = new ArrayList<>();
        for (AppCacheItem item : mAllItems) {
            if (item.isSelected) {
                pkgs.add(item.packageName);
            }
        }
        return pkgs;
    }

    public long getTotalSelectedBytes() {
        long total = 0;
        for (AppCacheItem item : mAllItems) {
            if (item.isSelected) {
                total += item.cacheBytes;
            }
        }
        return total;
    }

    public int getSelectedCount() {
        int count = 0;
        for (AppCacheItem item : mAllItems) {
            if (item.isSelected) count++;
        }
        return count;
    }

    private void notifySelectionChanged() {
        if (mListener != null) {
            mListener.onSelectionChanged(getTotalSelectedBytes(), getSelectedCount());
        }
    }

    private static class ViewHolder {
        ImageView ivIcon;
        CheckBox cbCheck;
        TextView tvName;
        TextView tvPkg;
        TextView tvSize;
    }
}
