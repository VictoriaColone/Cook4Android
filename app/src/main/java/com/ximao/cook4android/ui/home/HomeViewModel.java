package com.ximao.cook4android.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public HomeViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("欢迎来到Cook！\n发现美味食谱，开始您的烹饪之旅");
    }

    public LiveData<String> getText() {
        return mText;
    }
}