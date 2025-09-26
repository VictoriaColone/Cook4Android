package com.ximao.cook4android.ui.cooking;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class CookingViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public CookingViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("这是烹饪页面");
    }

    public LiveData<String> getText() {
        return mText;
    }
}
