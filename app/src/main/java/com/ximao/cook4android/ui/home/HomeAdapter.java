package com.ximao.cook4android.ui.home;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.ximao.cook4android.R;
import com.ximao.cook4android.models.RecipeItem;

import java.util.ArrayList;
import java.util.List;

public class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_FOOTER = 1;

    private final List<RecipeItem> items = new ArrayList<>();
    private boolean showLoadingFooter = false;

    public void setItems(List<RecipeItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void addItems(List<RecipeItem> more) {
        if (more == null || more.isEmpty()) return;
        int start = items.size();
        items.addAll(more);
        notifyItemRangeInserted(start, more.size());
    }

    public void setShowLoadingFooter(boolean show) {
        if (this.showLoadingFooter == show) return;
        this.showLoadingFooter = show;
        if (show) notifyItemInserted(items.size());
        else notifyItemRemoved(items.size());
    }

    @Override public int getItemCount() {
        return items.size() + (showLoadingFooter ? 1 : 0);
    }

    @Override public int getItemViewType(int position) {
        return position < items.size() ? VIEW_TYPE_ITEM : VIEW_TYPE_FOOTER;
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ITEM) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.home_list_item, parent, false);
            return new RecipeVH(v);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_footer_loading, parent, false);
            return new FooterVH(v);
        }
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof RecipeVH) {
            RecipeItem item = items.get(position);
            ((RecipeVH) holder).bind(item);
        }
    }

    static class RecipeVH extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvTitle;

        RecipeVH(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivCover);
            tvTitle = itemView.findViewById(R.id.tvTitle);
        }

        void bind(RecipeItem item) {
            tvTitle.setText(item.title);
            Glide.with(ivCover.getContext())
                    .load(item.imageUrl)
                    .placeholder(new ColorDrawable(Color.LTGRAY))
                    .centerCrop()
                    .into(ivCover);
        }
    }

    static class FooterVH extends RecyclerView.ViewHolder {
        FooterVH(@NonNull View itemView) { super(itemView); }
    }

}