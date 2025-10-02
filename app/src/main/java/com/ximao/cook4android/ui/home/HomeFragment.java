package com.ximao.cook4android.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.ximao.cook4android.R;
import com.ximao.cook4android.activity.SearchActivity;
import com.ximao.cook4android.models.RecipeItem;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HomeFragment extends Fragment {
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private HomeAdapter adapter;

    private View searchEntry;
    private TextView tvSearchHint;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private OkHttpClient okHttpClient;
    private Call currentCall;

    private int currentPage = 1;
    private final int pageSize = 20;
    private boolean hasMore = true;
    private boolean isLoading = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh);
        recyclerView = view.findViewById(R.id.recycler);
        searchEntry = view.findViewById(R.id.searchEntry);
        tvSearchHint = view.findViewById(R.id.tvSearchHint);

        okHttpClient = new OkHttpClient();

        setupSearchEntry();
        setupRecycler();
        setupRefresh();

        swipeRefreshLayout.setRefreshing(true);
        refresh();
    }

    private void setupSearchEntry() {
        tvSearchHint.setText("搜索菜名");
        searchEntry.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), SearchActivity.class);
            intent.putExtra(SearchActivity.EXTRA_DEFAULT_HINT, tvSearchHint.getText().toString());
            startActivity(intent);
        });
    }

    private void setupRecycler() {
        final int spanCount = 2;
        GridLayoutManager glm = new GridLayoutManager(getContext(), spanCount);
        adapter = new HomeAdapter();
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return adapter.getItemViewType(position) == HomeAdapter.VIEW_TYPE_FOOTER ? spanCount : 1;
            }
        });
        recyclerView.setLayoutManager(glm);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(dpToPx(8), 0, dpToPx(8), dpToPx(88));
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(spanCount, dpToPx(8), true));

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                if (dy <= 0) return;
                int total = glm.getItemCount();
                int lastVisible = glm.findLastVisibleItemPosition();
                if (!isLoading && hasMore && lastVisible >= total - 3) {
                    loadMore();
                }
            }
        });
    }

    private void setupRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::refresh);
    }

    private void refresh() {
        cancelCurrentCall();
        currentPage = 1;
        hasMore = true;
        isLoading = true;
        adapter.setShowLoadingFooter(false);

        fetchRecipes(currentPage, pageSize, null, new FetchCallback() {
            @Override public void onSuccess(List<RecipeItem> list) {
                mainHandler.post(() -> {
                    adapter.setItems(list);
                    isLoading = false;
                    hasMore = list.size() >= pageSize;
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
            @Override public void onError(Exception e) {
                mainHandler.post(() -> {
                    isLoading = false;
                    hasMore = true;
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(), "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadMore() {
        if (!hasMore || isLoading) return;
        isLoading = true;
        adapter.setShowLoadingFooter(true);
        int nextPage = currentPage + 1;

        fetchRecipes(nextPage, pageSize, null, new FetchCallback() {
            @Override public void onSuccess(List<RecipeItem> list) {
                mainHandler.post(() -> {
                    adapter.setShowLoadingFooter(false);
                    adapter.addItems(list);
                    currentPage = nextPage;
                    isLoading = false;
                    hasMore = list.size() >= pageSize;
                });
            }
            @Override public void onError(Exception e) {
                mainHandler.post(() -> {
                    adapter.setShowLoadingFooter(false);
                    isLoading = false;
                    Toast.makeText(getContext(), "加载更多失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void fetchRecipes(int page, int size, @Nullable String query, FetchCallback callback) {
        String url = "https://picsum.photos/v2/list?page=" + page + "&limit=" + size;
        Request request = new Request.Builder().url(url).build();
        currentCall = okHttpClient.newCall(request);
        currentCall.enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) callback.onError(e);
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful()) {
                    if (callback != null) callback.onError(new IOException("HTTP " + response.code()));
                    return;
                }
                try {
                    String body = response.body() != null ? response.body().string() : "[]";
                    JSONArray arr = new JSONArray(body);
                    List<RecipeItem> list = new ArrayList<>();
                    int base = (page - 1) * size;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String id = obj.optString("id");
                        String title = RecipeData.DISH_NAMES.get((base + i) % RecipeData.DISH_NAMES.size());
                        String image = "https://picsum.photos/id/" + id + "/600/600";
                        list.add(new RecipeItem(title, image));
                    }
                    if (callback != null) callback.onSuccess(list);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                } finally {
                    if (response.body() != null) response.body().close();
                }
            }
        });
    }

    private void cancelCurrentCall() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
            currentCall = null;
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }

    interface FetchCallback {
        void onSuccess(List<RecipeItem> list);
        void onError(Exception e);
    }

}