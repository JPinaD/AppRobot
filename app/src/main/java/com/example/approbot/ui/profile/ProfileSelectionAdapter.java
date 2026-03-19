package com.example.approbot.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.approbot.R;
import com.example.approbot.data.model.LocalStudentProfile;

import java.util.List;

public class ProfileSelectionAdapter extends RecyclerView.Adapter<ProfileSelectionAdapter.ProfileViewHolder> {
    private final List<LocalStudentProfile> profiles;
    private final OnProfileClickListener listener;

    public interface OnProfileClickListener {
        void onProfileClick(LocalStudentProfile profile);
    }

    public ProfileSelectionAdapter(List<LocalStudentProfile> profiles, OnProfileClickListener listener) {
        this.profiles = profiles;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_profile_selection, parent, false);
        return new ProfileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
        LocalStudentProfile profile = profiles.get(position);
        holder.name.setText(profile.name);
        holder.description.setText(profile.description);
        holder.itemView.setOnClickListener(v -> listener.onProfileClick(profile));
    }

    @Override
    public int getItemCount() {
        return profiles.size();
    }

    static class ProfileViewHolder extends RecyclerView.ViewHolder {
        TextView name, description;
        ProfileViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.profileName);
            description = itemView.findViewById(R.id.profileDescription);
        }
    }
}
