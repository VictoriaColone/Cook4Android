package com.ximao.cook4android.ui.favorites;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.ximao.cook4android.R;
import com.ximao.cook4android.models.RecipeItem;
import com.ximao.cook4android.ui.home.HomeAdapter;
import com.ximao.cook4android.ui.home.GridSpacingItemDecoration;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FavoritesFragment extends Fragment {
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private HomeAdapter adapter;

    private ViewGroup searchEntry;
    private TextView tvSearchHint;
    private EditText etSearch;
    private ImageView ivSearchIcon;
    private boolean isSearchMode = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private OkHttpClient okHttpClient;
    private Call currentCall;

    private int currentPage = 1;
    private final int pageSize = 20;
    private boolean hasMore = true;
    private boolean isLoading = false;
    private String currentQuery = null;
    
    // 保存滚动位置和数据
    private static final String KEY_SCROLL_POSITION = "scroll_position";
    private static final String KEY_SCROLL_OFFSET = "scroll_offset";
    private static final String KEY_IS_FIRST_LOAD = "is_first_load";
    private static final String KEY_CURRENT_PAGE = "current_page";
    private static final String KEY_HAS_MORE = "has_more";
    private static final String KEY_CURRENT_QUERY = "current_query";
    private static final String KEY_IS_SEARCH_MODE = "is_search_mode";
    private static final String KEY_SEARCH_TEXT = "search_text";
    private int savedScrollPosition = 0;
    private int savedScrollOffset = 0;
    private boolean isFirstLoad = true;
    private List<RecipeItem> cachedItems = new ArrayList<>();
    private String savedSearchText = "";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh);
        recyclerView = view.findViewById(R.id.recycler);
        searchEntry = (ViewGroup) view.findViewById(R.id.searchEntry);
        tvSearchHint = view.findViewById(R.id.tvSearchHint);
        ivSearchIcon = view.findViewById(R.id.ivSearchIcon);

        okHttpClient = new OkHttpClient();

        setupSearchEntry();
        setupRecycler();
        setupRefresh();

        // 恢复保存的滚动位置和加载状态
        if (savedInstanceState != null) {
            savedScrollPosition = savedInstanceState.getInt(KEY_SCROLL_POSITION, 0);
            savedScrollOffset = savedInstanceState.getInt(KEY_SCROLL_OFFSET, 0);
            isFirstLoad = savedInstanceState.getBoolean(KEY_IS_FIRST_LOAD, true);
            currentPage = savedInstanceState.getInt(KEY_CURRENT_PAGE, 1);
            hasMore = savedInstanceState.getBoolean(KEY_HAS_MORE, true);
            currentQuery = savedInstanceState.getString(KEY_CURRENT_QUERY, null);
            isSearchMode = savedInstanceState.getBoolean(KEY_IS_SEARCH_MODE, false);
            savedSearchText = savedInstanceState.getString(KEY_SEARCH_TEXT, "");
        }

        // 只在首次加载时刷新数据
        if (isFirstLoad) {
            // 首次加载不显示刷新动画，避免黑屏
            refresh();
        } else {
            // 非首次加载，恢复缓存的数据
            restoreCachedData();
        }
    }

    private void setupSearchEntry() {
        tvSearchHint.setText("搜索收藏的菜谱");
        searchEntry.setOnClickListener(v -> {
            if (!isSearchMode) {
                enterSearchMode();
            }
        });
    }

    private void enterSearchMode() {
        isSearchMode = true;
        searchEntry.removeAllViews();
        
        // 创建搜索输入框
        etSearch = new EditText(requireContext());
        etSearch.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT));
        etSearch.setHint("搜索收藏的菜谱");
        etSearch.setPadding(dpToPx(12), 0, dpToPx(12), 0);
        etSearch.setBackground(null);
        etSearch.setTextSize(14);
        etSearch.setTextColor(getResources().getColor(android.R.color.black));
        etSearch.setHintTextColor(getResources().getColor(android.R.color.darker_gray));
        
        // 添加搜索图标
        ImageView searchIcon = new ImageView(requireContext());
        searchIcon.setImageResource(R.drawable.ic_search_24);
        searchIcon.setColorFilter(getResources().getColor(android.R.color.darker_gray));
        searchIcon.setPadding(dpToPx(12), dpToPx(8), dpToPx(8), dpToPx(8));
        
        // 添加清除按钮
        ImageView clearIcon = new ImageView(requireContext());
        clearIcon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        clearIcon.setColorFilter(getResources().getColor(android.R.color.darker_gray));
        clearIcon.setPadding(dpToPx(8), dpToPx(8), dpToPx(12), dpToPx(8));
        clearIcon.setOnClickListener(v -> exitSearchMode());
        
        searchEntry.addView(searchIcon);
        searchEntry.addView(etSearch);
        searchEntry.addView(clearIcon);
        
        etSearch.requestFocus();
        
        // 添加搜索监听
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                savedSearchText = query;
                if (query.isEmpty()) {
                    currentQuery = null;
                    refresh();
                } else {
                    currentQuery = query;
                    search(query);
                }
            }
        });
    }

    private void exitSearchMode() {
        isSearchMode = false;
        currentQuery = null;
        savedSearchText = "";
        searchEntry.removeAllViews();
        
        // 恢复原来的搜索提示
        ivSearchIcon = new ImageView(requireContext());
        ivSearchIcon.setImageResource(R.drawable.ic_search_24);
        ivSearchIcon.setColorFilter(getResources().getColor(android.R.color.darker_gray));
        ivSearchIcon.setPadding(dpToPx(12), dpToPx(8), dpToPx(8), dpToPx(8));
        
        tvSearchHint = new TextView(requireContext());
        tvSearchHint.setText("搜索收藏的菜谱");
        tvSearchHint.setTextColor(getResources().getColor(android.R.color.darker_gray));
        tvSearchHint.setTextSize(14);
        tvSearchHint.setPadding(dpToPx(8), dpToPx(8), dpToPx(12), dpToPx(8));
        
        searchEntry.addView(ivSearchIcon);
        searchEntry.addView(tvSearchHint);
        
        // 刷新数据
        refresh();
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
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // 手动下拉刷新时，重置滚动位置
            savedScrollPosition = 0;
            savedScrollOffset = 0;
            refreshWithAnimation();
        });
    }

    private void refresh() {
        refreshInternal(false);
    }

    private void refreshWithAnimation() {
        refreshInternal(true);
    }

    private void refreshInternal(boolean showAnimation) {
        cancelCurrentCall();
        currentPage = 1;
        hasMore = true;
        isLoading = true;
        adapter.setShowLoadingFooter(false);
        
        if (showAnimation) {
            swipeRefreshLayout.setRefreshing(true);
        }

        fetchFavorites(currentPage, pageSize, currentQuery, new FetchCallback() {
            @Override public void onSuccess(List<RecipeItem> list) {
                mainHandler.post(() -> {
                    adapter.setItems(list);
                    // 缓存数据
                    cachedItems.clear();
                    cachedItems.addAll(list);
                    isLoading = false;
                    hasMore = list.size() >= pageSize;
                    swipeRefreshLayout.setRefreshing(false);
                    
                    // 首次加载完成后，标记为非首次加载
                    isFirstLoad = false;
                    
                    // 恢复滚动位置
                    restoreScrollPosition();
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

    private void search(String query) {
        cancelCurrentCall();
        currentPage = 1;
        hasMore = true;
        isLoading = true;
        adapter.setShowLoadingFooter(false);

        fetchFavorites(currentPage, pageSize, query, new FetchCallback() {
            @Override public void onSuccess(List<RecipeItem> list) {
                mainHandler.post(() -> {
                    adapter.setItems(list);
                    // 缓存搜索结果
                    cachedItems.clear();
                    cachedItems.addAll(list);
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
                    Toast.makeText(getContext(), "搜索失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadMore() {
        if (!hasMore || isLoading) return;
        isLoading = true;
        adapter.setShowLoadingFooter(true);
        int nextPage = currentPage + 1;

        fetchFavorites(nextPage, pageSize, currentQuery, new FetchCallback() {
            @Override public void onSuccess(List<RecipeItem> list) {
                mainHandler.post(() -> {
                    adapter.setShowLoadingFooter(false);
                    adapter.addItems(list);
                    // 缓存新加载的数据
                    cachedItems.addAll(list);
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

    private void fetchFavorites(int page, int size, @Nullable String query, FetchCallback callback) {
        // 使用不同的API接口获取收藏数据
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
                        String author = obj.optString("author", "未知作者");
                        String title = "收藏菜谱 " + (base + i + 1);
                        String image = "https://picsum.photos/id/" + id + "/600/600";
                        
                        // 如果有搜索查询，进行本地过滤
                        if (query != null && !query.isEmpty()) {
                            if (title.toLowerCase().contains(query.toLowerCase()) || 
                                author.toLowerCase().contains(query.toLowerCase())) {
                                list.add(new RecipeItem(title, image));
                            }
                        } else {
                            list.add(new RecipeItem(title, image));
                        }
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存当前滚动位置和加载状态
        if (recyclerView != null && recyclerView.getLayoutManager() != null) {
            GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
            int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
            View firstVisibleView = layoutManager.findViewByPosition(firstVisiblePosition);
            int offset = 0;
            if (firstVisibleView != null) {
                offset = firstVisibleView.getTop();
            }
            outState.putInt(KEY_SCROLL_POSITION, firstVisiblePosition);
            outState.putInt(KEY_SCROLL_OFFSET, offset);
        }
        outState.putBoolean(KEY_IS_FIRST_LOAD, isFirstLoad);
        outState.putInt(KEY_CURRENT_PAGE, currentPage);
        outState.putBoolean(KEY_HAS_MORE, hasMore);
        outState.putString(KEY_CURRENT_QUERY, currentQuery);
        outState.putBoolean(KEY_IS_SEARCH_MODE, isSearchMode);
        outState.putString(KEY_SEARCH_TEXT, savedSearchText);
    }

    private void restoreCachedData() {
        if (!cachedItems.isEmpty()) {
            // 恢复缓存的数据
            adapter.setItems(cachedItems);
            // 恢复滚动位置
            restoreScrollPosition();
            // 恢复搜索状态
            restoreSearchState();
        } else {
            // 如果没有缓存数据，静默加载（不显示刷新动画）
            refresh();
        }
    }

    private void restoreSearchState() {
        // 如果之前在搜索模式，恢复搜索状态
        if (isSearchMode && !savedSearchText.isEmpty()) {
            enterSearchMode();
            if (etSearch != null) {
                etSearch.setText(savedSearchText);
            }
        }
    }

    private void restoreScrollPosition() {
        if (recyclerView != null && recyclerView.getLayoutManager() != null && savedScrollPosition > 0) {
            // 延迟恢复滚动位置，确保RecyclerView已经完成布局
            recyclerView.post(() -> {
                if (recyclerView.getLayoutManager() != null) {
                    ((GridLayoutManager) recyclerView.getLayoutManager())
                            .scrollToPositionWithOffset(savedScrollPosition, savedScrollOffset);
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelCurrentCall();
    }

    interface FetchCallback {
        void onSuccess(List<RecipeItem> list);
        void onError(Exception e);
    }
}
