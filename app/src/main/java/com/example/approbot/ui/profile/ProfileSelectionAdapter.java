package com.example.approbot.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.approbot.R;
import com.example.approbot.data.local.entity.LocalProfileEntity;

import java.util.ArrayList;
import java.util.List;

public class ProfileSelectionAdapter extends RecyclerView.Adapter<ProfileSelectionAdapter.ProfileViewHolder> {

    public interface OnProfileClickListener {
        void onProfileClick(LocalProfileEntity profile);
    }

    private final List<LocalProfileEntity> profiles = new ArrayList<>();
    private final OnProfileClickListener listener;

    public ProfileSelectionAdapter(OnProfileClickListener listener) {
        this.listener = listener;
    }

    public void setProfiles(List<LocalProfileEntity> list) {
        profiles.clear();
        if (list != null) profiles.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_selection, parent, false);
        return new ProfileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
        LocalProfileEntity profile = profiles.get(position);
        holder.tvProfileName.setText(profile.name);
        holder.tvProfileDescription.setText(profile.description);
        holder.itemView.setOnClickListener(v -> listener.onProfileClick(profile));
    }

    @Override
    public int getItemCount() {
        return profiles.size();
    }

    static class ProfileViewHolder extends RecyclerView.ViewHolder {
        TextView tvProfileName, tvProfileDescription;

        ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProfileName        = itemView.findViewById(R.id.tvProfileName);
            tvProfileDescription = itemView.findViewById(R.id.tvProfileDescription);
        }
    }
}
