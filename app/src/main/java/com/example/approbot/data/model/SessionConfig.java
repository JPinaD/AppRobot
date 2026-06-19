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

    // Contenido de actividad (opcional, depende del activityId)
    public final List<String> activityItems;   // para emotion, turns
    public final int sequenceLength;           // para sequence (0 = no aplica)
    public final List<SocialScenarioContent> socialScenarios; // para social

    public SessionConfig(String sessionId, String activityId,
                         StudentProfile studentProfile, List<String> pictograms,
                         List<String> activityItems, int sequenceLength,
                         List<SocialScenarioContent> socialScenarios) {
        this.sessionId       = sessionId;
        this.activityId      = activityId;
        this.studentProfile  = studentProfile;
        this.pictograms      = pictograms != null ? pictograms : new ArrayList<>();
        this.activityItems   = activityItems != null ? activityItems : new ArrayList<>();
        this.sequenceLength  = sequenceLength;
        this.socialScenarios = socialScenarios != null ? socialScenarios : new ArrayList<>();
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

            // activityContent (opcional)
            List<String> activityItems = new ArrayList<>();
            int sequenceLength = 0;
            List<SocialScenarioContent> socialScenarios = new ArrayList<>();

            JSONObject content = obj.optJSONObject("activityContent");
            if (content != null) {
                JSONArray items = content.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) activityItems.add(items.getString(i));
                }
                sequenceLength = content.optInt("sequenceLength", content.optInt("steps", 0));
                JSONArray scenarios = content.optJSONArray("scenarios");
                if (scenarios != null) {
                    for (int i = 0; i < scenarios.length(); i++) {
                        SocialScenarioContent s = SocialScenarioContent.fromJson(scenarios.getJSONObject(i));
                        if (s != null) socialScenarios.add(s);
                    }
                }
            }

            return new SessionConfig(sessionId, activityId, profile, pictograms,
                    activityItems, sequenceLength, socialScenarios);
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
            if (!activityItems.isEmpty() || sequenceLength > 0 || !socialScenarios.isEmpty()) {
                JSONObject content = new JSONObject();
                if (!activityItems.isEmpty()) {
                    JSONArray items = new JSONArray();
                    for (String item : activityItems) items.put(item);
                    content.put("items", items);
                }
                if (sequenceLength > 0) content.put("sequenceLength", sequenceLength);
                if (!socialScenarios.isEmpty()) {
                    JSONArray scenarios = new JSONArray();
                    for (SocialScenarioContent s : socialScenarios) scenarios.put(s.toJson());
                    content.put("scenarios", scenarios);
                }
                obj.put("activityContent", content);
            }
            return obj.toString();
        } catch (JSONException e) {
            Log.w(TAG, "Error serializando SessionConfig: " + e.getMessage());
            return null;
        }
    }

    /** Contenido de escenario social embebido en SessionConfig. */
    public static class SocialScenarioContent {
        public final String id;
        public final String description;
        public final String optionA;
        public final String optionB;
        public final String outcomeA;
        public final String outcomeB;

        public SocialScenarioContent(String id, String description,
                                     String optionA, String optionB,
                                     String outcomeA, String outcomeB) {
            this.id          = id;
            this.description = description;
            this.optionA     = optionA;
            this.optionB     = optionB;
            this.outcomeA    = outcomeA;
            this.outcomeB    = outcomeB;
        }

        public static SocialScenarioContent fromJson(JSONObject obj) {
            if (obj == null) return null;
            try {
                return new SocialScenarioContent(
                        obj.optString("id", ""),
                        obj.optString("description", ""),
                        obj.optString("optionA", ""),
                        obj.optString("optionB", ""),
                        obj.optString("outcomeA", ""),
                        obj.optString("outcomeB", ""));
            } catch (Exception e) {
                return null;
            }
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("description", description);
            obj.put("optionA", optionA);
            obj.put("optionB", optionB);
            obj.put("outcomeA", outcomeA);
            obj.put("outcomeB", outcomeB);
            return obj;
        }
    }
}
