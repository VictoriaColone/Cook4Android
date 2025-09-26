package com.ximao.cook4android.ui.cooking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.ximao.cook4android.databinding.FragmentCookingBinding;

public class CookingFragment extends Fragment {

    private FragmentCookingBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        CookingViewModel cookingViewModel =
                new ViewModelProvider(this).get(CookingViewModel.class);

        binding = FragmentCookingBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textCooking;
        cookingViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
