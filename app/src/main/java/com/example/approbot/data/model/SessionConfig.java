package com.example.approbot.data.model;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO con la configuración de sesión recibida en SESSION_START.
 */
public class SessionConfig {

    private static final String TAG = "SessionConfig";

    public final String sessionId;
    public final String activityId;
    public final StudentProfile studentProfile; // nullable
    public final List<String> pictograms;

    public SessionConfig(String sessionId, String activityId,
                         StudentProfile studentProfile, List<String> pictograms) {
        this.sessionId      = sessionId;
        this.activityId     = activityId;
        this.studentProfile = studentProfile;
        this.pictograms     = pictograms != null ? pictograms : new ArrayList<>();
    }

    /** Devuelve null si el JSON es inválido o faltan campos obligatorios. */
    public static SessionConfig fromJson(String json) {
        if (json == null) return null;
        try {
            JSONObject obj = new JSONObject(json);
            String sessionId  = obj.optString("sessionId", "");
            String activityId = obj.optString("activityId", "");
            if (activityId.isEmpty()) return null;

            List<String> pictograms = new ArrayList<>();
            JSONArray arr = obj.optJSONArray("pictograms");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) pictograms.add(arr.getString(i));
            }

            StudentProfile profile = StudentProfile.fromJson(obj.optJSONObject("studentProfile"));

            return new SessionConfig(sessionId, activityId, profile, pictograms);
        } catch (JSONException e) {
            Log.w(TAG, "Error parseando SessionConfig: " + e.getMessage());
            return null;
        }
    }

    /** Serializa esta SessionConfig a JSON para persistencia. */
    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("sessionId", sessionId);
            obj.put("activityId", activityId);
            JSONArray arr = new JSONArray();
            for (String p : pictograms) arr.put(p);
            obj.put("pictograms", arr);
            if (studentProfile != null) {
                JSONObject profileObj = new JSONObject();
                profileObj.put("id", studentProfile.id);
                profileObj.put("name", studentProfile.name);
                JSONArray colors = new JSONArray();
                for (String c : studentProfile.excludedColors) colors.put(c);
                profileObj.put("excludedColors", colors);
                if (studentProfile.backgroundSoundResName != null)
                    profileObj.put("backgroundSoundResName", studentProfile.backgroundSoundResName);
                obj.put("studentProfile", profileObj);
            }
            return obj.toString();
        } catch (JSONException e) {
            Log.w(TAG, "Error serializando SessionConfig: " + e.getMessage());
            return null;
        }
    }
}
