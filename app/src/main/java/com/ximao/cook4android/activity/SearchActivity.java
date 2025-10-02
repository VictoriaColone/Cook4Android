package com.ximao.cook4android.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;
import com.ximao.cook4android.R;
import com.ximao.cook4android.models.RecipeItem;
import com.ximao.cook4android.ui.home.GridSpacingItemDecoration;
import com.ximao.cook4android.ui.home.HomeAdapter;
import com.ximao.cook4android.ui.home.RecipeData;

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

public class SearchActivity extends AppCompatActivity {
    public static final String EXTRA_DEFAULT_HINT = "extra_default_hint";

    private EditText etSearch;
    private ImageView btnBack;
    private RecyclerView recycler;
    private HomeAdapter adapter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OkHttpClient okHttpClient = new OkHttpClient();
    private Call currentCall;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        etSearch = findViewById(R.id.etSearch);
        btnBack = findViewById(R.id.btnBack);
        recycler = findViewById(R.id.recyclerSearch);

        String defaultHint = getIntent().getStringExtra(EXTRA_DEFAULT_HINT);
        if (!TextUtils.isEmpty(defaultHint)) {
            etSearch.setHint(defaultHint);
        }

        setupRecycler();
        setupActions();
        etSearch.requestFocus();
    }

    private void setupRecycler() {
        int spanCount = 2;
        GridLayoutManager glm = new GridLayoutManager(this, spanCount);
        adapter = new HomeAdapter();
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return adapter.getItemViewType(position) == HomeAdapter.VIEW_TYPE_FOOTER ? spanCount : 1;
            }
        });
        recycler.setLayoutManager(glm);
        recycler.setAdapter(adapter);
        recycler.setClipToPadding(false);
        recycler.setPadding(dpToPx(8), 0, dpToPx(8), dpToPx(88));
        recycler.addItemDecoration(new GridSpacingItemDecoration(spanCount, dpToPx(8), true));
    }

    private void setupActions() {
        btnBack.setOnClickListener(v -> finish());
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            // 软键盘通常只给 actionId，不给 KeyEvent（event == null）
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_NULL
                    || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                doSearch(etSearch.getText().toString());
                return true; // 消费事件
            }
            // 兼容少数发送实体回车键事件的情况（硬键盘/部分输入法）
            if (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN) {
                doSearch(etSearch.getText().toString());
                return true;
            }
            return false;
        });

        // 再加一个 OnKeyListener 兼容硬件回车键（有些设备不会触发 EditorAction）
        etSearch.setOnKeyListener((v, keyCode, keyEvent) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && keyEvent.getAction() == KeyEvent.ACTION_UP) {
                doSearch(etSearch.getText().toString());
                return true;
            }
            return false;
        });

    }

    private void doSearch(String query) {
        if (TextUtils.isEmpty(query)) {
            Snackbar.make(recycler, "请输入要搜索的菜名", Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (currentCall != null && !currentCall.isCanceled()) currentCall.cancel();

        String url = "https://picsum.photos/v2/list?page=1&limit=80";
        Request request = new Request.Builder().url(url).build();
        currentCall = okHttpClient.newCall(request);
        adapter.setShowLoadingFooter(true);

        currentCall.enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> {
                    adapter.setShowLoadingFooter(false);
                    Snackbar.make(recycler, "搜索失败: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
                });
            }

            @Override public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> {
                        adapter.setShowLoadingFooter(false);
                        Snackbar.make(recycler, "HTTP " + response.code(), Snackbar.LENGTH_SHORT).show();
                    });
                    return;
                }
                try {
                    String body = response.body() != null ? response.body().string() : "[]";
                    JSONArray arr = new JSONArray(body);
                    List<RecipeItem> results = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String id = obj.optString("id");
                        String title = RecipeData.DISH_NAMES.get(i % RecipeData.DISH_NAMES.size());
                        if (title.toLowerCase().contains(query.toLowerCase())) {
                            results.add(new RecipeItem(title, "https://picsum.photos/id/" + id + "/600/600"));
                        }
                    }
                    mainHandler.post(() -> {
                        adapter.setShowLoadingFooter(false);
                        adapter.setItems(results);
                        if (results.isEmpty()) {
                            Snackbar.make(recycler, "没有找到相关菜名", Snackbar.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        adapter.setShowLoadingFooter(false);
                        Snackbar.make(recycler, "解析失败: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
                    });
                } finally {
                    if (response.body() != null) response.body().close();
                }
            }
        });
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (currentCall != null && !currentCall.isCanceled()) currentCall.cancel();
    }
}